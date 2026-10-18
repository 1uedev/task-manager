package taskmanager.identity.cli;

import java.io.PrintStream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import taskmanager.identity.account.AccountService;
import taskmanager.identity.account.AccountService.Issued;
import taskmanager.platform.ApiProblemException;

/**
 * The container commands for the first administrator and for a locked out administrator. They run instead of
 * the web server ({@code java -jar app.jar bootstrap-admin NAME}); the one time password is printed once on
 * the standard output and never logged. Exit codes: 0 success, 2 refused (accounts exist, unknown account),
 * 1 unexpected error.
 */
@Component
@ConditionalOnProperty("taskmanager.admin-command")
class AdminCommandRunner implements ApplicationRunner, ExitCodeGenerator {
    static final String BOOTSTRAP = "bootstrap-admin";
    static final String RESET = "reset-admin-password";

    private final AccountService accounts;
    private final String command;
    private final String username;
    private int exitCode;

    AdminCommandRunner(AccountService accounts,
            @org.springframework.beans.factory.annotation.Value("${taskmanager.admin-command}") String command,
            @org.springframework.beans.factory.annotation.Value("${taskmanager.admin-username:}") String username) {
        this.accounts = accounts;
        this.command = command;
        this.username = username;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        PrintStream out = System.out;
        PrintStream err = System.err;
        try {
            Issued issued = switch (command) {
                case BOOTSTRAP -> accounts.bootstrapAdministrator(username);
                case RESET -> accounts.emergencyAdministratorReset(username);
                default -> throw new IllegalArgumentException("unknown command");
            };
            out.println("Account: " + issued.account().username());
            out.println("One time password (shown only now): " + issued.oneTimePassword());
            out.println("Valid until: " + issued.oneTimeExpiresAt());
            out.println("The password must be changed at the first login.");
        } catch (ApiProblemException refused) {
            err.println("Refused: " + refused.code());
            exitCode = 2;
        } catch (RuntimeException failed) {
            err.println("The command failed. See the application log for the reason.");
            exitCode = 1;
            throw failed;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
