package taskmanager.git.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * What the git module asks of GitHub and GitLab (TASK-111): the OAuth flow with PKCE, the account behind a token, the
 * repositories a token may read and one repository by its stable identifier, all read only. GitHub is used through a GitHub
 * App and its user access tokens, GitLab through an OAuth application with the scope {@code read_api}. The addresses are
 * built here from the registered provider; a repository address shown to a person is built from the address of the provider
 * and the path, never taken from an answer.
 */
@Component
public class ProviderApi {
    /** GitLab of its own from this major version on (rule 8). */
    static final int GITLAB_MIN_MAJOR = 16;
    private static final int MAX_REPOSITORIES = 100;
    private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9._\\-]+(/[A-Za-z0-9._\\-]+){1,20}");
    private static final Pattern VERSION = Pattern.compile("^(\\d{1,4})\\.(\\d{1,4})");

    private final GitHttp http;
    private final GitProperties properties;

    ProviderApi(GitHttp http, GitProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    record Tokens(String access, Instant accessExpiresAt, String refresh, Instant refreshExpiresAt) {
    }

    record Account(String id, String login) {
    }

    public record Repository(String externalId, String name, String path, String url) {
    }

    /** The provider refused the token: it ended, was withdrawn or the grant is gone. */
    static final class TokenRejected extends RuntimeException {
        private static final long serialVersionUID = 1L;

        TokenRejected() {
            super("The provider refused the token");
        }
    }

    /** The address of the provider for people, without slash at the end. */
    String webUrl(Provider provider) {
        return provider.baseUrl();
    }

    String apiUrl(Provider provider) {
        return provider.kind() == Provider.Kind.GITHUB ? properties.githubApiUrl() : provider.baseUrl() + "/api/v4";
    }

    // ------------------------------------------------------------------------------------------------ OAuth

    String authorizeUrl(Provider provider, String redirect, String state, String verifier) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", provider.clientId());
        query.put("redirect_uri", redirect);
        query.put("state", state);
        query.put("code_challenge", challenge(verifier));
        query.put("code_challenge_method", "S256");
        String path;
        if (provider.kind() == Provider.Kind.GITHUB) {
            path = "/login/oauth/authorize";
        } else {
            path = "/oauth/authorize";
            query.put("response_type", "code");
            query.put("scope", "read_api");
        }
        StringBuilder url = new StringBuilder(webUrl(provider)).append(path).append('?');
        query.forEach((key, value) -> url.append(GitHttp.encode(key)).append('=').append(GitHttp.encode(value)).append('&'));
        return url.substring(0, url.length() - 1);
    }

    Tokens exchange(Provider provider, String secret, String code, String verifier, String redirect, Instant now) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", provider.clientId());
        form.put("client_secret", secret);
        form.put("code", code);
        form.put("redirect_uri", redirect);
        form.put("code_verifier", verifier);
        if (provider.kind().gitlab()) {
            form.put("grant_type", "authorization_code");
        }
        return tokens(http.postForm(provider, tokenUrl(provider), form), now);
    }

    Tokens refresh(Provider provider, String secret, String refreshToken, Instant now) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", provider.clientId());
        form.put("client_secret", secret);
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        return tokens(http.postForm(provider, tokenUrl(provider), form), now);
    }

    private String tokenUrl(Provider provider) {
        return webUrl(provider) + (provider.kind() == Provider.Kind.GITHUB ? "/login/oauth/access_token" : "/oauth/token");
    }

    private static Tokens tokens(GitHttp.Answer answer, Instant now) {
        if (answer.limited()) {
            throw new GitHttp.Failure("git.rate_limited", null);
        }
        if (answer.status() == 400 || answer.status() == 401) {
            throw new TokenRejected();
        }
        if (!answer.ok()) {
            throw new GitHttp.Failure("git.unexpected_answer", null);
        }
        JsonNode json = answer.json();
        if (json.hasNonNull("error")) {
            // GitHub answers 200 with an error for a used or wrong code and for an ended refresh token.
            throw new TokenRejected();
        }
        String access = text(json, "access_token");
        if (access == null || access.length() > 4000) {
            throw new GitHttp.Failure("git.unexpected_answer", null);
        }
        String refresh = text(json, "refresh_token");
        if (refresh != null && refresh.length() > 4000) {
            throw new GitHttp.Failure("git.unexpected_answer", null);
        }
        return new Tokens(access, seconds(json, "expires_in", now), refresh, seconds(json, "refresh_token_expires_in", now));
    }

    private static Instant seconds(JsonNode json, String field, Instant now) {
        JsonNode value = json.get(field);
        if (value == null || !value.isNumber() || value.asLong() <= 0) {
            return null;
        }
        return now.plusSeconds(Math.min(value.asLong(), 10L * 365 * 24 * 3600));
    }

    // ------------------------------------------------------------------------------------------------ reading

    Account account(Provider provider, String token) {
        JsonNode json = read(provider, apiUrl(provider) + "/user", token);
        String id = json.hasNonNull("id") && json.get("id").isNumber() ? json.get("id").asString() : null;
        String login = text(json, provider.kind() == Provider.Kind.GITHUB ? "login" : "username");
        if (id == null || login == null) {
            throw new GitHttp.Failure("git.unexpected_answer", null);
        }
        return new Account(id, login);
    }

    /** The repositories the token may read, at most 100, filtered by a part of name or path. */
    List<Repository> repositories(Provider provider, String token, String query) {
        String part = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        List<Repository> result = new ArrayList<>();
        if (provider.kind() == Provider.Kind.GITHUB) {
            JsonNode installations = read(provider, apiUrl(provider) + "/user/installations?per_page=100", token).get("installations");
            if (installations == null || !installations.isArray()) {
                throw new GitHttp.Failure("git.unexpected_answer", null);
            }
            for (JsonNode installation : installations) {
                if (result.size() >= MAX_REPOSITORIES || !installation.hasNonNull("id")) {
                    break;
                }
                JsonNode repositories = read(provider, apiUrl(provider) + "/user/installations/" + installation.get("id").asLong()
                    + "/repositories?per_page=100", token).get("repositories");
                if (repositories == null || !repositories.isArray()) {
                    throw new GitHttp.Failure("git.unexpected_answer", null);
                }
                for (JsonNode repository : repositories) {
                    repository(provider, repository).filter(r -> matches(r, part)).ifPresent(result::add);
                    if (result.size() >= MAX_REPOSITORIES) {
                        break;
                    }
                }
            }
        } else {
            String url = apiUrl(provider) + "/projects?membership=true&simple=true&per_page=100&order_by=last_activity_at"
                + (part.isEmpty() ? "" : "&search=" + GitHttp.encode(part));
            JsonNode projects = read(provider, url, token);
            if (!projects.isArray()) {
                throw new GitHttp.Failure("git.unexpected_answer", null);
            }
            for (JsonNode project : projects) {
                repository(provider, project).filter(r -> matches(r, part)).ifPresent(result::add);
            }
        }
        return result.size() > MAX_REPOSITORIES ? result.subList(0, MAX_REPOSITORIES) : result;
    }

    /** One repository by its stable identifier; empty when the token may not read it or it does not exist. */
    Optional<Repository> repository(Provider provider, String token, String externalId) {
        if (!externalId.matches("[0-9]{1,20}")) {
            return Optional.empty();
        }
        String url = apiUrl(provider) + (provider.kind() == Provider.Kind.GITHUB ? "/repositories/" : "/projects/") + externalId;
        GitHttp.Answer answer = http.get(provider, url, token);
        if (answer.status() == 404 || (answer.status() == 403 && !answer.limited())) {
            return Optional.empty();
        }
        return repository(provider, checked(answer)).filter(r -> r.externalId().equals(externalId));
    }

    /** The version a GitLab of its own reports to the token, as major and minor; empty when it does not say. */
    Optional<int[]> version(Provider provider, String token) {
        GitHttp.Answer answer = http.get(provider, apiUrl(provider) + "/version", token);
        if (answer.status() == 401 || answer.status() == 403 || answer.status() == 404) {
            return Optional.empty();
        }
        return parseVersion(text(checked(answer), "version"));
    }

    static Optional<int[]> parseVersion(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = VERSION.matcher(text);
        return matcher.find() ? Optional.of(new int[] { Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)) }) : Optional.empty();
    }

    /**
     * Whether the provider answers at all, without a token: GitHub at {@code /meta}, GitLab at {@code /api/v4/version}, where
     * 401 means it is there. Returns the version when a GitLab tells it without a token.
     */
    Optional<int[]> reach(Provider provider) {
        String url = provider.kind() == Provider.Kind.GITHUB ? apiUrl(provider) + "/meta" : apiUrl(provider) + "/version";
        GitHttp.Answer answer = http.get(provider, url, null);
        if (answer.limited()) {
            return Optional.empty();
        }
        if (provider.kind().gitlab() && answer.status() == 401) {
            return Optional.empty();
        }
        JsonNode json = checked(answer);
        return provider.kind().gitlab() ? parseVersion(text(json, "version")) : Optional.empty();
    }

    // ------------------------------------------------------------------------------------------------ inside

    private JsonNode read(Provider provider, String url, String token) {
        return checked(http.get(provider, url, token));
    }

    private static JsonNode checked(GitHttp.Answer answer) {
        if (answer.status() == 401) {
            throw new TokenRejected();
        }
        if (answer.limited()) {
            throw new GitHttp.Failure("git.rate_limited", null);
        }
        if (!answer.ok()) {
            throw new GitHttp.Failure(answer.status() >= 500 ? "git.unavailable" : "git.unexpected_answer", null);
        }
        return answer.json();
    }

    private Optional<Repository> repository(Provider provider, JsonNode json) {
        if (json == null || !json.isObject() || !json.hasNonNull("id") || !json.get("id").isNumber()) {
            return Optional.empty();
        }
        String id = json.get("id").asString();
        String name = text(json, "name");
        String path = text(json, provider.kind() == Provider.Kind.GITHUB ? "full_name" : "path_with_namespace");
        if (name == null || path == null || name.length() > 200 || path.length() > 500) {
            return Optional.empty();
        }
        String url = SAFE_PATH.matcher(path).matches() ? webUrl(provider) + "/" + path : null;
        return Optional.of(new Repository(id, name, path, url));
    }

    private static boolean matches(Repository repository, String part) {
        return part.isEmpty() || repository.name().toLowerCase(Locale.ROOT).contains(part) || repository.path().toLowerCase(Locale.ROOT).contains(part);
    }

    private static String text(JsonNode json, String field) {
        JsonNode value = json == null ? null : json.get(field);
        return value == null || !value.isString() || value.asString().isBlank() ? null : value.asString();
    }

    static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
