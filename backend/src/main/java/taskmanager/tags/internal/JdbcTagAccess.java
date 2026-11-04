package taskmanager.tags.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.tags.ProjectTagChanged;
import taskmanager.tags.TagAccess;
import taskmanager.tags.TagChanged;
import taskmanager.tags.TicketTagChanged;

/** The tag port for other modules; see {@link TagAccess}. */
@Service
class JdbcTagAccess implements TagAccess {
    private final JdbcClient jdbc;

    JdbcTagAccess(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<TagChanged> tags(UUID companyId) {
        return jdbc.sql("select id, company_id, project_id, name from tag where company_id = ? and merged_into is null order by id").param(companyId)
            .query((rs, row) -> new TagChanged(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class),
                rs.getObject("project_id", UUID.class), rs.getString("name")))
            .list();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<TicketTagChanged> ticketAssignments(UUID companyId) {
        return jdbc.sql("select a.ticket_id, a.tag_id from tag_ticket a join tag t on t.id = a.tag_id where t.company_id = ? order by a.ticket_id, a.tag_id")
            .param(companyId)
            .query((rs, row) -> new TicketTagChanged(rs.getObject("ticket_id", UUID.class), rs.getObject("tag_id", UUID.class), true))
            .list();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ProjectTagChanged> projectAssignments(UUID companyId) {
        return jdbc.sql("select a.project_id, a.tag_id from tag_project a join tag t on t.id = a.tag_id where t.company_id = ? order by a.project_id, a.tag_id")
            .param(companyId)
            .query((rs, row) -> new ProjectTagChanged(rs.getObject("project_id", UUID.class), rs.getObject("tag_id", UUID.class), true))
            .list();
    }
}
