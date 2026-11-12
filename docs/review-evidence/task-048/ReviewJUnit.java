import java.nio.file.*;
import java.util.*;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import tools.jackson.databind.json.JsonMapper;

/** Runs the four unchanged regression tests against a selected external classpath. */
public class ReviewJUnit {
    static String assertion(Throwable failure) {
        String message = failure.getMessage();
        if (message == null) return failure.getClass().getSimpleName();
        int expected = message.indexOf("expected:");
        return expected < 0 ? failure.getClass().getSimpleName() : message.substring(expected);
    }
    public static void main(String[] args) throws Exception {
        var methods = List.of("personsWithDifferentBinaryIdentifiersReachTheirOwnAccounts",
            "aKnownEntryThatTookTheNameOfAnotherAccountIsRefused",
            "anIncompleteEncryptionSettingIsRefusedWithoutAnInternalError",
            "aServerThatDoesNotTakeStartTlsIsAFailureOfTheEncryption");
        var request = LauncherDiscoveryRequestBuilder.request().selectors(methods.stream()
            .map(name -> DiscoverySelectors.selectMethod("taskmanager.identity.LdapLoginTest", name)).toList()).build();
        var listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        var summary = listener.getSummary();
        var rows = new LinkedHashMap<String,Object>();
        rows.put("implementation", args[1]);
        rows.put("testsFound", summary.getTestsFoundCount());
        rows.put("testsSucceeded", summary.getTestsSucceededCount());
        rows.put("testsFailed", summary.getTestsFailedCount());
        rows.put("failedContainers", summary.getContainersFailedCount());
        rows.put("failures", summary.getFailures().stream().map(f -> Map.of("test", f.getTestIdentifier().getDisplayName(),
            "exception", f.getException().getClass().getSimpleName(), "message", assertion(f.getException()))).toList());
        Files.writeString(Path.of(args[0]), JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(rows)+"\n");
        if (summary.getTestsFoundCount()!=4 || summary.getContainersFailedCount()!=0 || summary.getTestsFailedCount()!=Integer.parseInt(args[2]))
            throw new AssertionError("Unexpected regression outcome");
        System.exit(0); // End the standalone launcher, including Spring's cached test context.
    }
}
