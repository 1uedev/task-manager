package taskmanager.platform;

import java.util.Optional;
import java.util.UUID;

/**
 * The company of this installation (DEC-006: one installation per company). The identifier is kept on the server and is
 * never supplied by a browser or by an API client. Until {@link #create()} ran there is no company, and every consumer
 * has to treat that as "no access".
 */
public interface CompanyContext {
    /** The identifier of the company, or nothing while the installation has not been bootstrapped. */
    Optional<UUID> find();

    /**
     * Creates the company. Only the bootstrap does this, inside its transaction; a second company is refused by the
     * database, so a repeated call fails instead of adding one.
     */
    UUID create();
}
