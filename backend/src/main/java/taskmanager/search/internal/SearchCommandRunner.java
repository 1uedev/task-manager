package taskmanager.search.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import taskmanager.platform.CompanyContext;

/**
 * The container command that builds the search projection again from the projects, tickets and tags modules
 * ({@code java -jar app.jar rebuild-search}). It runs instead of the web server. Exit codes: 0 success, 2 refused (the
 * installation has no company yet), 1 unexpected error.
 */
@Component
@ConditionalOnProperty("taskmanager.search-command")
class SearchCommandRunner implements ApplicationRunner, ExitCodeGenerator {
    private final SearchProjection projection;
    private final CompanyContext companies;
    private int exitCode;

    SearchCommandRunner(SearchProjection projection, CompanyContext companies) {
        this.projection = projection;
        this.companies = companies;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Optional<UUID> company = companies.find();
        if (company.isEmpty()) {
            System.err.println("Refused: company.missing");
            exitCode = 2;
            return;
        }
        try {
            projection.rebuild(company.get());
            System.out.println("Search projection rebuilt.");
        } catch (RuntimeException failed) {
            System.err.println("The command failed. See the application log for the reason.");
            exitCode = 1;
            throw failed;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
