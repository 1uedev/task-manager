package taskmanager.ai.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The three adapters of V1 (TASK-125, rule 2): OpenAI (and servers compatible with its chat completions), Anthropic and Ollama,
 * each over its own HTTP interface without a vendor SDK. They translate one prompt into the request of the provider and its
 * answer back into text and the tokens the provider reports, and they tell failures apart by a fixed code: unreachable,
 * TLS, time out, rejected key, rate limit, unsuitable model, too large a context, cut off or unexpected answer. They never
 * switch to another provider or model (rule 4).
 */
@Component
class AiAdapters {
    static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AiHttp http;

    AiAdapters(AiHttp http) {
        this.http = http;
    }

    /** The fixed frame with the skill, the data block and the most tokens the answer may use. */
    record Prompt(String system, String user, int maxOutput) {
    }

    /** What a provider answered: the text and the tokens it reported, {@code null} where it reported none. */
    record Completion(String text, Integer inputTokens, Integer outputTokens) {
    }

    Completion complete(AiProvider provider, String key, AiModel model, Prompt prompt, Duration timeout) {
        return switch (provider.kind()) {
            case OPENAI -> openai(provider, key, model, prompt, timeout);
            case ANTHROPIC -> anthropic(provider, key, model, prompt, timeout);
            case OLLAMA -> ollama(provider, key, model, prompt, timeout);
        };
    }

    /** The identifiers of the models the access offers; the check of an access or a model (rule 3) reads only this list. */
    List<String> models(AiProvider provider, String key, Duration timeout) {
        AiHttp.Answer answer = switch (provider.kind()) {
            case OPENAI -> http.get(provider, provider.baseUrl() + "/v1/models", bearer(key), timeout);
            case ANTHROPIC -> http.get(provider, provider.baseUrl() + "/v1/models?limit=1000", anthropicHeaders(key), timeout);
            case OLLAMA -> http.get(provider, provider.baseUrl() + "/api/tags", bearer(key), timeout);
        };
        if (!answer.ok()) {
            throw new AiHttp.Failure(failureCode(answer), null);
        }
        JsonNode root = answer.json();
        List<String> names = new ArrayList<>();
        JsonNode list = provider.kind() == AiProvider.Kind.OLLAMA ? root.path("models") : root.path("data");
        if (!list.isArray()) {
            throw new AiHttp.Failure("ai.unexpected_answer", null);
        }
        for (JsonNode entry : list) {
            String name = provider.kind() == AiProvider.Kind.OLLAMA ? text(entry.path("name")) : text(entry.path("id"));
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    /** Whether a listed name is the model key: equal, or for Ollama the key without tag and the name with {@code :latest}. */
    static boolean matches(AiProvider.Kind kind, String listed, String modelKey) {
        return listed.equals(modelKey) || kind == AiProvider.Kind.OLLAMA && !modelKey.contains(":") && listed.equals(modelKey + ":latest");
    }

    // ------------------------------------------------------------------------------------------------ OpenAI

    private Completion openai(AiProvider provider, String key, AiModel model, Prompt prompt, Duration timeout) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model.modelKey());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", prompt.system());
        messages.addObject().put("role", "user").put("content", prompt.user());
        body.put("max_completion_tokens", prompt.maxOutput());
        body.putObject("response_format").put("type", "json_object");
        AiHttp.Answer answer = http.postJson(provider, provider.baseUrl() + "/v1/chat/completions", bearer(key), JSON.writeValueAsString(body),
            timeout);
        if (!answer.ok()) {
            throw new AiHttp.Failure(failureCode(answer), null);
        }
        JsonNode root = answer.json();
        JsonNode choice = root.path("choices").path(0);
        if ("length".equals(text(choice.path("finish_reason")))) {
            throw new AiHttp.Failure("ai.output_too_long", null);
        }
        String text = text(choice.path("message").path("content"));
        if (text == null) {
            throw new AiHttp.Failure("ai.unexpected_answer", null);
        }
        return new Completion(text, integer(root.path("usage").path("prompt_tokens")), integer(root.path("usage").path("completion_tokens")));
    }

    // ------------------------------------------------------------------------------------------------ Anthropic

    private Completion anthropic(AiProvider provider, String key, AiModel model, Prompt prompt, Duration timeout) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model.modelKey());
        body.put("max_tokens", prompt.maxOutput());
        body.put("system", prompt.system());
        body.putArray("messages").addObject().put("role", "user").put("content", prompt.user());
        AiHttp.Answer answer = http.postJson(provider, provider.baseUrl() + "/v1/messages", anthropicHeaders(key), JSON.writeValueAsString(body),
            timeout);
        if (!answer.ok()) {
            throw new AiHttp.Failure(failureCode(answer), null);
        }
        JsonNode root = answer.json();
        if ("max_tokens".equals(text(root.path("stop_reason")))) {
            throw new AiHttp.Failure("ai.output_too_long", null);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            if ("text".equals(text(block.path("type"))) && text(block.path("text")) != null) {
                text.append(text(block.path("text")));
            }
        }
        if (text.isEmpty()) {
            throw new AiHttp.Failure("ai.unexpected_answer", null);
        }
        return new Completion(text.toString(), integer(root.path("usage").path("input_tokens")), integer(root.path("usage").path("output_tokens")));
    }

    private static Map<String, String> anthropicHeaders(String key) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (key != null) {
            headers.put("x-api-key", key);
        }
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        return headers;
    }

    // ------------------------------------------------------------------------------------------------ Ollama

    private Completion ollama(AiProvider provider, String key, AiModel model, Prompt prompt, Duration timeout) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model.modelKey());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", prompt.system());
        messages.addObject().put("role", "user").put("content", prompt.user());
        body.put("stream", false);
        body.put("format", "json");
        ObjectNode options = body.putObject("options");
        options.put("num_predict", prompt.maxOutput());
        options.put("num_ctx", model.contextTokens());
        AiHttp.Answer answer = http.postJson(provider, provider.baseUrl() + "/api/chat", bearer(key), JSON.writeValueAsString(body), timeout);
        if (!answer.ok()) {
            throw new AiHttp.Failure(failureCode(answer), null);
        }
        JsonNode root = answer.json();
        if ("length".equals(text(root.path("done_reason")))) {
            throw new AiHttp.Failure("ai.output_too_long", null);
        }
        String text = text(root.path("message").path("content"));
        if (text == null) {
            throw new AiHttp.Failure("ai.unexpected_answer", null);
        }
        return new Completion(text, integer(root.path("prompt_eval_count")), integer(root.path("eval_count")));
    }

    // ------------------------------------------------------------------------------------------------ shared

    private static Map<String, String> bearer(String key) {
        return key == null ? Map.of() : Map.of("Authorization", "Bearer " + key);
    }

    /**
     * The code of a refusal by its status, and for 400 and 413 whether the provider names the context or the length of the
     * prompt; the text itself never leaves this method.
     */
    static String failureCode(AiHttp.Answer answer) {
        int status = answer.status();
        if (status == 401 || status == 403) {
            return "ai.auth_failed";
        }
        if (status == 429 || status == 529) {
            return "ai.rate_limited";
        }
        if (status == 404) {
            return "ai.model_unsuitable";
        }
        if (status == 400 || status == 413 || status == 422) {
            String text = answer.text().toLowerCase(Locale.ROOT);
            return text.contains("context") || text.contains("too long") || text.contains("too many tokens") || status == 413
                ? "ai.context_too_large" : "ai.model_unsuitable";
        }
        return "ai.provider_failed";
    }

    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.asString() : null;
    }

    private static Integer integer(JsonNode node) {
        return node != null && node.isIntegralNumber() && node.canConvertToInt() ? node.asInt() : null;
    }
}
