package taskmanager.git.internal;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads a pasted address of a repository, commit, pull request or merge request (TASK-112, rule 1). The instance is the
 * registered provider whose address the input starts with; the rest is read as GitHub ({@code owner/repo},
 * {@code /commit/<sha>}, {@code /pull/<n>}) or GitLab ({@code group/…/project}, {@code /-/commit/<sha>},
 * {@code /-/merge_requests/<n>}) names it. Query and fragment are dropped, as is {@code .git} at the end of a repository.
 * Nothing of the input is ever called: the module builds its addresses from the provider and the stable identifiers.
 */
final class GitAddress {
    static final int MAX_LENGTH = 2000;
    private static final Pattern SHA = Pattern.compile("[0-9a-fA-F]{7,64}");
    private static final Pattern NUMBER = Pattern.compile("[1-9][0-9]{0,9}");

    private GitAddress() {
    }

    /** The provider, the path of the repository, the kind of object and its SHA or number ({@code ""} for a repository). */
    record Parsed(Provider provider, String repositoryPath, String kind, String key) {
    }

    /**
     * The readings of the input for every registered provider whose address it starts with, the longest address first; several
     * instances may share an address, such as two applications for GitHub.com.
     */
    static List<Parsed> parse(String input, List<Provider> providers) {
        if (input == null) {
            return List.of();
        }
        String text = input.strip();
        int cut = indexOfAny(text, '?', '#');
        if (cut >= 0) {
            text = text.substring(0, cut);
        }
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        List<Parsed> found = new java.util.ArrayList<>();
        for (Provider provider : providers) {
            String base = provider.baseUrl().toLowerCase(Locale.ROOT);
            if (lower.startsWith(base + "/")) {
                parts(provider, text.substring(base.length() + 1)).ifPresent(found::add);
            }
        }
        found.sort(java.util.Comparator.comparingInt((Parsed p) -> -p.provider().baseUrl().length()));
        return found;
    }

    private static Optional<Parsed> parts(Provider provider, String rest) {
        if (rest.contains("//") || rest.contains("%") || rest.contains("\\")) {
            return Optional.empty();
        }
        if (provider.kind() == Provider.Kind.GITHUB) {
            String[] segments = rest.split("/");
            if (segments.length < 2) {
                return Optional.empty();
            }
            String path = segments[0] + "/" + repository(segments[1]);
            if (segments.length == 2) {
                return Optional.of(new Parsed(provider, path, GitLink.REPOSITORY, ""));
            }
            if (segments.length >= 4 && segments[2].equals("commit") && SHA.matcher(segments[3]).matches()) {
                return Optional.of(new Parsed(provider, path, GitLink.COMMIT, segments[3].toLowerCase(Locale.ROOT)));
            }
            if (segments.length >= 4 && segments[2].equals("pull") && NUMBER.matcher(segments[3]).matches()) {
                return Optional.of(new Parsed(provider, path, GitLink.PULL_REQUEST, segments[3]));
            }
            return Optional.empty();
        }
        int marker = rest.indexOf("/-/");
        String path = repository(marker < 0 ? rest : rest.substring(0, marker));
        if (path.isEmpty() || !path.contains("/")) {
            return Optional.empty();
        }
        if (marker < 0) {
            return Optional.of(new Parsed(provider, path, GitLink.REPOSITORY, ""));
        }
        String[] segments = rest.substring(marker + 3).split("/");
        if (segments.length >= 2 && segments[0].equals("commit") && SHA.matcher(segments[1]).matches()) {
            return Optional.of(new Parsed(provider, path, GitLink.COMMIT, segments[1].toLowerCase(Locale.ROOT)));
        }
        if (segments.length >= 2 && segments[0].equals("merge_requests") && NUMBER.matcher(segments[1]).matches()) {
            return Optional.of(new Parsed(provider, path, GitLink.MERGE_REQUEST, segments[1]));
        }
        return Optional.empty();
    }

    private static String repository(String name) {
        return name.endsWith(".git") ? name.substring(0, name.length() - 4) : name;
    }

    private static int indexOfAny(String text, char first, char second) {
        int a = text.indexOf(first);
        int b = text.indexOf(second);
        return a < 0 ? b : b < 0 ? a : Math.min(a, b);
    }

    /** Whether {@code kind} fits the provider: pull requests on GitHub, merge requests on GitLab. */
    static boolean fits(Provider provider, String kind) {
        return switch (kind) {
            case GitLink.REPOSITORY, GitLink.COMMIT -> true;
            case GitLink.PULL_REQUEST -> provider.kind() == Provider.Kind.GITHUB;
            case GitLink.MERGE_REQUEST -> provider.kind().gitlab();
            default -> false;
        };
    }

    static boolean validKey(String kind, String key) {
        return switch (kind) {
            case GitLink.REPOSITORY -> key == null || key.isEmpty();
            case GitLink.COMMIT -> key != null && SHA.matcher(key).matches();
            case GitLink.PULL_REQUEST, GitLink.MERGE_REQUEST -> key != null && NUMBER.matcher(key).matches() && Long.parseLong(key) <= Integer.MAX_VALUE;
            default -> false;
        };
    }
}
