package taskmanager.operations.internal;

import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import taskmanager.ai.AiRestore;
import taskmanager.files.FilesRestore;
import taskmanager.helpdesk.HelpdeskRestore;
import taskmanager.identity.IdentityRestore;
import taskmanager.permissions.CompanyAdministration;
import taskmanager.platform.CompanyContext;

/**
 * The container command that prepares a restored installation ({@code java -jar app.jar after-restore NAME}, TASK-131,
 * DEC-161). It runs instead of the web server, once, and only while the marker of a restore exists:
 *
 * <ol>
 *   <li>ends every open session, so no login from before the failure stays valid;</li>
 *   <li>holds every mail reply that waited or was being sent as uncertain, so the restored job sends nothing a second
 *       time and a person decides;</li>
 *   <li>discards the kept AI suggestions, which belong to a state that no longer exists;</li>
 *   <li>checks that every content the database refers to is in the file store with its checksum;</li>
 *   <li>lists the accounts that hold company rights, so the operator can repeat withdrawals made after the backup point
 *       (rule 11: the application does not do that by itself).</li>
 * </ol>
 *
 * <p>The report names identifiers and counts, never content. The marker is removed at the end, even when the file store is
 * incomplete: the report says so and the operator decides, because a missing content does not make the installation
 * unusable. Exit codes: 0 everything consistent, 3 ran but contents are missing or damaged, 2 refused (no restore waits),
 * 1 unexpected error.
 */
@Component
@ConditionalOnProperty(name = "taskmanager.operations-command", havingValue = RestoreCommandRunner.AFTER_RESTORE)
class RestoreCommandRunner implements ApplicationRunner, ExitCodeGenerator {
    static final String AFTER_RESTORE = "after-restore";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestoreRepository repository;
    private final IdentityRestore identity;
    private final HelpdeskRestore helpdesk;
    private final AiRestore ai;
    private final FilesRestore files;
    private final CompanyAdministration administration;
    private final CompanyContext companies;
    private final Clock clock;
    private final String operator;
    private final String filesDirectory;
    private int exitCode;

    RestoreCommandRunner(RestoreRepository repository, IdentityRestore identity, HelpdeskRestore helpdesk, AiRestore ai,
            FilesRestore files, CompanyAdministration administration, CompanyContext companies, Clock clock,
            @Value("${taskmanager.operations-operator:}") String operator,
            @Value("${taskmanager.files.directory:}") String filesDirectory) {
        this.repository = repository;
        this.identity = identity;
        this.helpdesk = helpdesk;
        this.ai = ai;
        this.files = files;
        this.administration = administration;
        this.companies = companies;
        this.clock = clock;
        this.operator = operator;
        this.filesDirectory = filesDirectory;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        PrintStream out = System.out;
        PrintStream err = System.err;
        try {
            if (filesDirectory.isBlank()) {
                err.println("Refused: files.directory_missing");
                err.println("The command checks the file store and therefore needs TASK_MANAGER_FILES_DIR"
                    + " (taskmanager.files.directory), the same directory as the web process.");
                exitCode = 2;
                return;
            }
            Optional<RestoreRepository.Pending> pending = repository.pending();
            if (pending.isEmpty()) {
                err.println("Refused: restore.not_pending");
                err.println("No restore waits for this command. A restore writes its marker into operations_restore_pending;"
                    + " the restore script of the backup service does that.");
                exitCode = 2;
                return;
            }
            String point = pending.get().point();
            Instant now = clock.instant();
            out.println("Backup point: " + point);
            out.println("Restored at: " + pending.get().restoredAt());

            int sessions = identity.revokeAllSessions(now);
            out.println("Sessions ended: " + sessions);
            int replies = helpdesk.holdOpenReplies(now);
            out.println("Mail replies held as uncertain: " + replies);
            int suggestions = ai.discardSuggestions();
            out.println("AI suggestions discarded: " + suggestions);

            FilesRestore.Check check = files.checkContents();
            out.println("Contents checked: " + check.checked());
            out.println("Contents missing: " + check.missing().size());
            check.missing().forEach(id -> out.println("  missing " + id));
            out.println("Contents damaged: " + check.damaged().size());
            check.damaged().forEach(id -> out.println("  damaged " + id));

            List<String> holders = companyRightHolders();
            out.println("Accounts with company rights: " + holders.size());
            holders.forEach(line -> out.println("  " + line));

            repository.audit("restore.released", point, operator, detail(sessions, replies, suggestions, check), now);
            repository.release(point);
            if (check.complete()) {
                out.println("The installation is released. Start the web process and the worker.");
            } else {
                exitCode = 3;
                out.println("The installation is released, but the file store is incomplete. Check whether database and file"
                    + " store belong to the same backup point before you start the processes.");
            }
            out.println("Repeat the account locks and withdrawals of rights that happened after the backup point; the"
                + " application does not know them.");
        } catch (RuntimeException failed) {
            err.println("The command failed. See the application log for the reason.");
            exitCode = 1;
            throw failed;
        }
    }

    /** Every account with company rights as "identifier login-name right,right"; an inactive account has no login name. */
    private List<String> companyRightHolders() {
        Optional<UUID> company = companies.find();
        if (company.isEmpty()) {
            return List.of();
        }
        SortedMap<UUID, SortedSet<String>> holders = administration.companyRightHolders(company.get());
        Map<UUID, String> names = identity.activeLoginNames(holders.keySet());
        return holders.entrySet().stream()
            .map(entry -> entry.getKey() + " " + names.getOrDefault(entry.getKey(), "(nicht aktiv)")
                + " " + String.join(",", entry.getValue()))
            .toList();
    }

    private String detail(int sessions, int replies, int suggestions, FilesRestore.Check check) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sessions", sessions);
        detail.put("replies", replies);
        detail.put("suggestions", suggestions);
        detail.put("contentsChecked", check.checked());
        detail.put("contentsMissing", check.missing().size());
        detail.put("contentsDamaged", check.damaged().size());
        return JSON.writeValueAsString(detail);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
