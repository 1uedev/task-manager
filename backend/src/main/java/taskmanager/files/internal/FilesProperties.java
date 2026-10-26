package taskmanager.files.internal;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What the operation provides for the file store (TASK-051, DEC-080).
 *
 * @param directory the directory of the file store; it must lie on a persistent volume outside the container and is
 *        required when the web application starts
 * @param maxBytes largest accepted file in bytes (decimal, 25 MB)
 * @param orphanAge how old a file without attachment or an abandoned upload must be before the clean up removes it
 * @param retention how long the content of a removed attachment is kept before it is deleted
 * @param cleanupInterval time between two runs of the clean up
 * @param blockedExtensions extensions (without dot, lower case) of executable and active content that are refused; since
 *        review F5 of TASK-051 also pages a browser opens with scripts after saving, help files and links that run code under
 *        Windows, and disk images that pass their content by the mark of the web
 */
@ConfigurationProperties(prefix = "taskmanager.files")
public record FilesProperties(
        @DefaultValue("") String directory,
        @DefaultValue("25000000") long maxBytes,
        @DefaultValue("24h") Duration orphanAge,
        @DefaultValue("7d") Duration retention,
        @DefaultValue("1h") Duration cleanupInterval,
        @DefaultValue({ "exe", "dll", "msi", "bat", "cmd", "com", "scr", "pif", "cpl", "msc", "hta", "lnk", "reg", "js", "jse",
            "vbs", "vbe", "wsf", "wsh", "ps1", "psm1", "sh", "jar", "jnlp", "apk", "app", "chm", "url", "iso", "img", "vhd", "vhdx",
            "html", "htm", "xhtml", "xht", "shtml", "mht", "mhtml", "svg", "svgz" })
        List<String> blockedExtensions) {
}
