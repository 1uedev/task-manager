package taskmanager.assist.internal;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import taskmanager.ai.AiSuggestionGuard;
import taskmanager.ai.AiUseCase;
import taskmanager.permissions.AccessContext;

/** The guards of the ticket summary and of the reply draft (TASK-126): both check through {@link TicketAssist#stillValid}. */
@Configuration
class AssistGuards {
    @Bean
    AiSuggestionGuard ticketSummaryGuard(TicketAssist assist) {
        return guard(AiUseCase.TICKET_SUMMARY, assist);
    }

    @Bean
    AiSuggestionGuard replyDraftGuard(TicketAssist assist) {
        return guard(AiUseCase.REPLY_DRAFT, assist);
    }

    private static AiSuggestionGuard guard(AiUseCase useCase, TicketAssist assist) {
        return new AiSuggestionGuard() {
            @Override
            public AiUseCase useCase() {
                return useCase;
            }

            @Override
            public String objectKind() {
                return TicketAssist.OBJECT_KIND;
            }

            @Override
            public boolean stillValid(AccessContext actor, UUID projectId, String objectKind, UUID objectId, Long objectVersion,
                    String referencesJson) {
                return TicketAssist.OBJECT_KIND.equals(objectKind) && assist.stillValid(useCase, actor, projectId, objectId, referencesJson);
            }
        };
    }
}
