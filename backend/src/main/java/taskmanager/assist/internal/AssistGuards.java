package taskmanager.assist.internal;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import taskmanager.ai.AiSuggestionGuard;
import taskmanager.ai.AiUseCase;
import taskmanager.permissions.AccessContext;
import tools.jackson.databind.JsonNode;

/**
 * The guards of the use cases of the module: the ticket summary and the reply draft check through {@link TicketAssist#stillValid}
 * (TASK-126), the requirement review through {@link RequirementAssist#stillValid} and the wiki revision through
 * {@link WikiAssist#stillValid}, which also refuses an answer without its placeholders when the execution ends (TASK-127).
 */
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

    @Bean
    AiSuggestionGuard requirementReviewGuard(RequirementAssist assist) {
        return new AiSuggestionGuard() {
            @Override
            public AiUseCase useCase() {
                return AiUseCase.REQUIREMENT_REVIEW;
            }

            @Override
            public String objectKind() {
                return RequirementAssist.OBJECT_KIND;
            }

            @Override
            public boolean stillValid(AccessContext actor, UUID projectId, String objectKind, UUID objectId, Long objectVersion,
                    String referencesJson) {
                return RequirementAssist.OBJECT_KIND.equals(objectKind) && assist.stillValid(actor, projectId, objectId, referencesJson);
            }
        };
    }

    @Bean
    AiSuggestionGuard wikiRevisionGuard(WikiAssist assist) {
        return new AiSuggestionGuard() {
            @Override
            public AiUseCase useCase() {
                return AiUseCase.WIKI_REVISION;
            }

            @Override
            public String objectKind() {
                return WikiAssist.OBJECT_KIND;
            }

            @Override
            public boolean stillValid(AccessContext actor, UUID projectId, String objectKind, UUID objectId, Long objectVersion,
                    String referencesJson) {
                return WikiAssist.OBJECT_KIND.equals(objectKind) && assist.stillValid(actor, projectId, objectId, referencesJson);
            }

            @Override
            public String contentProblem(String referencesJson, JsonNode content) {
                return assist.contentProblem(referencesJson, content);
            }
        };
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
