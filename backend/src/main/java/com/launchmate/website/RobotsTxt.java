package com.launchmate.website;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A small robots.txt reader: user-agent groups, Allow and Disallow with the * and $ wildcards, the
 * most specific (longest) matching rule wins, and Allow wins a tie. A group naming this crawler is
 * preferred over the * group. Crawl-delay and Sitemap lines are ignored.
 */
public final class RobotsTxt {

    private record Rule(boolean allow, Pattern pattern, int length) {}

    public static final RobotsTxt ALLOW_ALL = new RobotsTxt(List.of());

    private final List<Rule> rules;

    private RobotsTxt(List<Rule> rules) {
        this.rules = rules;
    }

    public static RobotsTxt parse(String text, String agentToken) {
        String agent = agentToken.toLowerCase(Locale.ROOT);
        List<Rule> specific = new ArrayList<>();
        List<Rule> generic = new ArrayList<>();
        boolean sawSpecific = false;

        List<String> groupAgents = new ArrayList<>();
        boolean groupHasRules = false;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine;
            int hash = line.indexOf('#');
            if (hash >= 0) {
                line = line.substring(0, hash);
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String field = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();

            if (field.equals("user-agent")) {
                if (groupHasRules) {
                    groupAgents = new ArrayList<>();
                    groupHasRules = false;
                }
                groupAgents.add(value.toLowerCase(Locale.ROOT));
            } else if (field.equals("allow") || field.equals("disallow")) {
                groupHasRules = true;
                if (value.isEmpty()) {
                    continue; // "Disallow:" with no path allows everything
                }
                Rule rule = new Rule(field.equals("allow"), toPattern(value), value.length());
                for (String ua : groupAgents) {
                    if (ua.equals("*")) {
                        generic.add(rule);
                    } else if (agent.contains(ua) || ua.contains(agent)) {
                        specific.add(rule);
                        sawSpecific = true;
                    }
                }
            }
        }
        return new RobotsTxt(sawSpecific ? specific : generic);
    }

    public boolean allows(String pathWithQuery) {
        String path = pathWithQuery == null || pathWithQuery.isEmpty() ? "/" : pathWithQuery;
        Rule best = null;
        for (Rule r : rules) {
            if (r.pattern().matcher(path).lookingAt()) {
                if (best == null || r.length() > best.length() || (r.length() == best.length() && r.allow())) {
                    best = r;
                }
            }
        }
        return best == null || best.allow();
    }

    private static Pattern toPattern(String value) {
        boolean endAnchor = value.endsWith("$");
        String body = endAnchor ? value.substring(0, value.length() - 1) : value;
        StringBuilder regex = new StringBuilder();
        for (char c : body.toCharArray()) {
            regex.append(c == '*' ? ".*" : Pattern.quote(String.valueOf(c)));
        }
        if (endAnchor) {
            regex.append("$");
        }
        return Pattern.compile(regex.toString());
    }
}
