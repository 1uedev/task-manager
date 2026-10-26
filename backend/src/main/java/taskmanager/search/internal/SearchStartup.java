package taskmanager.search.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import taskmanager.platform.CompanyContext;

/**
 * Builds the projection at the start of the web application when the company has none yet (DEC-069), so that a database
 * migrated with existing projects, tickets and tags becomes searchable without a separate command. Afterwards only the
 * events of the owning modules change the projection; {@code rebuild-search} builds it again on demand.
 */
@Component
@ConditionalOnWebApplication
class SearchStartup {
    private static final Logger LOG = LoggerFactory.getLogger(SearchStartup.class);
    private final SearchProjection projection;
    private final CompanyContext companies;

    SearchStartup(SearchProjection projection, CompanyContext companies) {
        this.projection = projection;
        this.companies = companies;
    }

    @EventListener(ApplicationReadyEvent.class)
    void buildIfMissing() {
        companies.find().ifPresent(company -> {
            if (projection.buildIfMissing(company)) {
                LOG.info("Search projection built for the company of this installation.");
            }
        });
    }
}
