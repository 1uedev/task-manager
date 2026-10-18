package taskmanager.identity.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Small conversions between {@link Instant} and PostgreSQL timestamptz for JDBC. */
public final class Db {
    private Db() {
    }

    /** PostgreSQL keeps microseconds and rounds finer values, so truncate here: a stored time never lands after the instant it was computed from. */
    public static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
