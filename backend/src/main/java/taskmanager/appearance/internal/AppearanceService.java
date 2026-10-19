package taskmanager.appearance.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.AccessContext;
import taskmanager.platform.ApiProblemException;

/**
 * Reads and changes the single appearance row. Every change needs the version the client last saw, so two
 * administrators cannot overwrite each other unnoticed, and every change is written to the audit trail.
 */
@Service
public class AppearanceService {
    public static final String DEFAULT_ACCENT = "#f97316";
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    private final JdbcClient jdbc;
    private final Clock clock;

    AppearanceService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record LogoContent(String type, byte[] content) {
    }

    public Appearance current() {
        return jdbc.sql("select accent_color, logo_type, coalesce(octet_length(logo), 0) as logo_size, version, updated_at from appearance_setting where id = 1")
            .query((rs, row) -> new Appearance(rs.getString("accent_color"), rs.getString("logo_type"), rs.getInt("logo_size"),
                rs.getLong("version"), rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant()))
            .single();
    }

    public Optional<LogoContent> logo() {
        return jdbc.sql("select logo_type, logo from appearance_setting where id = 1 and logo is not null")
            .query((rs, row) -> new LogoContent(rs.getString("logo_type"), rs.getBytes("logo"))).optional();
    }

    @Transactional
    public Appearance changeAccent(AccessContext actor, long expectedVersion, String accentColor) {
        if (accentColor == null || !COLOR.matcher(accentColor).matches()) {
            throw ApiProblemException.of(400, "appearance.color_invalid");
        }
        String color = accentColor.toLowerCase(Locale.ROOT);
        Appearance before = locked(expectedVersion);
        jdbc.sql("update appearance_setting set accent_color = ?, version = version + 1, updated_at = ?, updated_by = ? where id = 1")
            .params(color, ts(), actor.accountId()).update();
        audit(actor, "appearance.accent_changed", before.accentColor() + " -> " + color);
        return current();
    }

    @Transactional
    public Appearance replaceLogo(AccessContext actor, long expectedVersion, String declaredType, byte[] content) {
        if (content.length == 0) {
            throw ApiProblemException.of(400, "appearance.logo_empty");
        }
        if (content.length > Logo.MAX_BYTES) {
            throw ApiProblemException.of(413, "request.too_large");
        }
        String type = Logo.detectType(content).orElseThrow(() -> ApiProblemException.of(415, "appearance.logo_type_invalid"));
        if (declaredType == null || !type.equals(declaredType.toLowerCase(Locale.ROOT).split(";")[0].trim())) {
            throw ApiProblemException.of(415, "appearance.logo_type_invalid");
        }
        locked(expectedVersion);
        jdbc.sql("update appearance_setting set logo = ?, logo_type = ?, version = version + 1, updated_at = ?, updated_by = ? where id = 1")
            .params(content, type, ts(), actor.accountId()).update();
        audit(actor, "appearance.logo_replaced", type + ", " + content.length + " bytes");
        return current();
    }

    @Transactional
    public Appearance removeLogo(AccessContext actor, long expectedVersion) {
        locked(expectedVersion);
        jdbc.sql("update appearance_setting set logo = null, logo_type = null, version = version + 1, updated_at = ?, updated_by = ? where id = 1")
            .params(ts(), actor.accountId()).update();
        audit(actor, "appearance.logo_removed", null);
        return current();
    }

    /** Locks the row and checks the version the client last saw. */
    private Appearance locked(long expectedVersion) {
        long version = jdbc.sql("select version from appearance_setting where id = 1 for update").query(Long.class).single();
        if (version != expectedVersion) {
            throw ApiProblemException.of(412, "appearance.version_conflict");
        }
        return current();
    }

    private void audit(AccessContext actor, String event, String detail) {
        jdbc.sql("insert into appearance_audit_event (occurred_at, actor_account_id, actor, event, detail) values (?, ?, ?, ?, ?)")
            .params(ts(), actor.accountId(), actor.username(), event, detail).update();
    }

    private java.time.OffsetDateTime ts() {
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC);
    }
}
