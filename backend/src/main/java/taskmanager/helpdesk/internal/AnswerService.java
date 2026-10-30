package taskmanager.helpdesk.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.tickets.TicketAccess;
import tools.jackson.databind.json.JsonMapper;

/**
 * New answers of customers (TASK-067, DEC-097). A mail that joins a ticket that already exists, by itself as the answer to a
 * reply or through a person in the review queue, changes neither status nor assignee of the ticket. So that it is not
 * overlooked, it stays new until a person marks it as seen. List and marking need what a reply needs: membership, the project
 * action {@code helpdesk.reply}, {@code ticket.read} and the read rules of {@code mail} and {@code contact}; whoever answers
 * the customer is whoever has to see the answer.
 *
 * <p>Locks, in this order: rights, project row ({@code FOR SHARE}), the mail.
 */
@Service
public class AnswerService {
    private static final int LIMIT = 100;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HelpdeskRepository repository;
    private final ReplyService replies;
    private final ProjectAccess projects;
    private final TicketAccess tickets;
    private final Clock clock;

    AnswerService(HelpdeskRepository repository, ReplyService replies, ProjectAccess projects, TicketAccess tickets, Clock clock) {
        this.repository = repository;
        this.replies = replies;
        this.projects = projects;
        this.tickets = tickets;
        this.clock = clock;
    }

    /** A new answer with the number of its ticket. */
    public record NewAnswer(Message mail, long number) {
    }

    /** The new answers of the projects in which the caller may reply, the latest first, at most one hundred. */
    @Transactional
    public List<NewAnswer> list(AccessContext actor) {
        List<UUID> allowed = projects.memberships(actor.companyId(), actor.userId()).stream().sorted()
            .filter(project -> replies.mayReply(actor, project)).toList();
        List<NewAnswer> result = new ArrayList<>();
        for (Message mail : repository.newAnswers(actor.companyId(), allowed, LIMIT)) {
            tickets.locate(mail.ticketId()).filter(location -> location.projectId().equals(mail.projectId()))
                .ifPresent(location -> result.add(new NewAnswer(mail, location.number())));
        }
        return result;
    }

    /**
     * Marks a new answer as seen for everybody. Without {@code ticket.read} the mail does not exist for the caller (404),
     * without the rights of a reply the marking is refused (403); a mail that is no new answer gives 409.
     */
    @Transactional
    public Message seen(AccessContext actor, UUID id, long expectedVersion) {
        Message unlocked = repository.message(actor.companyId(), id).orElseThrow(AnswerService::notFound);
        replies.requireReply(actor, unlocked.projectId(), AnswerService::notFound);
        Message mail = repository.lockMessage(actor.companyId(), id).orElseThrow(AnswerService::notFound);
        if (mail.version() != expectedVersion) {
            throw ApiProblemException.of(412, "helpdesk.version_conflict");
        }
        if (!repository.newAnswer(id)) {
            throw ApiProblemException.of(409, "helpdesk.answer_not_new");
        }
        Instant now = clock.instant();
        repository.answerSeen(id, now, actor.userId());
        repository.audit(actor.companyId(), "message", id, "answer.seen", JSON.writeValueAsString(new Detail(mail.ticketId())), actor.userId(), now);
        return repository.message(actor.companyId(), id).orElseThrow();
    }

    private record Detail(UUID ticketId) {
    }

    private static ApiProblemException notFound() {
        return ApiProblemException.of(404, "helpdesk.message_not_found");
    }
}
