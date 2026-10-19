package taskmanager.appearance.internal;

import java.time.Instant;

/** The stored appearance without the logo bytes. */
public record Appearance(String accentColor, String logoType, int logoSize, long version, Instant updatedAt) {
    public boolean hasLogo() {
        return logoType != null;
    }
}
