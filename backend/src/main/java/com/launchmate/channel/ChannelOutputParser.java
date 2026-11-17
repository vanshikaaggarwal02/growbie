package com.launchmate.channel;

import com.launchmate.ai.AiJson;
import com.launchmate.ai.InvalidAiOutputException;
import com.launchmate.ai.Quotes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates the model's channel analysis against the evidence it was given. Theme examples must be
 * real video titles from the channel, strengths and gaps must quote a real line of the evidence, and
 * new video ideas must not simply repeat an existing title. As with the website audit, the first
 * attempt is strict (any unverifiable item fails the reply so the model can retry with the reason)
 * and the retry is lenient (unverifiable items are dropped and counted).
 */
final class ChannelOutputParser {

    record Theme(String name, String description, List<String> exampleTitles) {}

    record Strength(String point, String evidence) {}

    record Gap(String title, String reasoning, String evidence, String suggestion, String basis) {}

    record Idea(String title, String hook, String format, String why) {}

    record CalendarEntry(int day, String format, String task, String titleIdea) {}

    record Output(String summary, List<Theme> themes, List<Strength> strengths, List<Gap> gaps,
                  List<String> differentiation, List<Idea> ideas, List<CalendarEntry> calendar,
                  List<String> limitations, int dropped) {}

    private static final Set<String> BASES = Set.of("OBSERVED", "INFERENCE");
    private static final Set<String> IDEA_FORMATS = Set.of("SHORT", "LONG_FORM");
    private static final Set<String> CALENDAR_FORMATS = Set.of("SHORT", "LONG_FORM", "PLANNING");
    private static final int MIN_EVIDENCE = 4;

    private ChannelOutputParser() {}

    static Output parse(ObjectMapper json, String text, Set<String> videoTitles, String evidenceText,
                        boolean strict) {
        JsonNode root = AiJson.parseObject(json, text);
        String summary = AiJson.str(root, "summary", 1, 600);
        String haystack = Quotes.normalise(evidenceText);
        Set<String> knownTitles = videoTitles.stream().map(Quotes::normalise).collect(Collectors.toSet());
        List<String> problems = new ArrayList<>();
        int dropped = 0;

        List<Theme> themes = new ArrayList<>();
        for (JsonNode t : AiJson.array(root, "themes", 1, 6)) {
            requireObject(t, "themes");
            String name = AiJson.str(t, "name", 1, 60);
            String description = AiJson.str(t, "description", 1, 500);
            List<String> examples = new ArrayList<>();
            for (String title : AiJson.strings(t, "exampleTitles", 1, 4, 200)) {
                if (knownTitles.contains(Quotes.normalise(title))) {
                    examples.add(title);
                } else {
                    problems.add("theme '" + name + "' lists '" + title + "', which is not one of the channel's video titles");
                }
            }
            if (examples.isEmpty()) {
                dropped++;
            } else {
                themes.add(new Theme(name, description, List.copyOf(examples)));
            }
        }

        List<Strength> strengths = new ArrayList<>();
        for (JsonNode s : AiJson.array(root, "strengths", 0, 4)) {
            requireObject(s, "strengths");
            String point = AiJson.str(s, "point", 1, 300);
            String evidence = AiJson.str(s, "evidence", MIN_EVIDENCE, 700);
            if (Quotes.appearsIn(haystack, evidence)) {
                strengths.add(new Strength(point, evidence));
            } else {
                problems.add("strength '" + point + "': its evidence is not copied exactly from one line of the EVIDENCE");
                dropped++;
            }
        }

        List<Gap> gaps = new ArrayList<>();
        for (JsonNode g : AiJson.array(root, "gaps", 0, 5)) {
            requireObject(g, "gaps");
            Gap gap = new Gap(AiJson.str(g, "title", 1, 200), AiJson.str(g, "reasoning", 1, 700),
                    AiJson.str(g, "evidence", MIN_EVIDENCE, 700), AiJson.str(g, "suggestion", 1, 600),
                    AiJson.oneOf(g, "basis", BASES));
            if (Quotes.appearsIn(haystack, gap.evidence())) {
                gaps.add(gap);
            } else {
                problems.add("gap '" + gap.title() + "': its evidence is not copied exactly from one line of the EVIDENCE");
                dropped++;
            }
        }

        List<Idea> ideas = new ArrayList<>();
        Set<String> seenIdeas = new HashSet<>();
        for (JsonNode i : AiJson.array(root, "ideas", 3, 8)) {
            requireObject(i, "ideas");
            Idea idea = new Idea(AiJson.str(i, "title", 1, 160), AiJson.str(i, "hook", 1, 320),
                    AiJson.oneOf(i, "format", IDEA_FORMATS), AiJson.str(i, "why", 1, 450));
            String key = Quotes.normalise(idea.title());
            if (knownTitles.contains(key)) {
                problems.add("idea '" + idea.title() + "' repeats an existing video title");
                dropped++;
            } else if (seenIdeas.add(key)) {
                ideas.add(idea);
            }
        }

        List<CalendarEntry> calendar = new ArrayList<>();
        Set<Integer> days = new HashSet<>();
        for (JsonNode c : AiJson.array(root, "calendar", 1, 7)) {
            requireObject(c, "calendar");
            int day = AiJson.integer(c, "day", 1, 7);
            if (!days.add(day)) {
                throw new InvalidAiOutputException("'calendar' uses day " + day + " more than once.");
            }
            calendar.add(new CalendarEntry(day, AiJson.oneOf(c, "format", CALENDAR_FORMATS),
                    AiJson.str(c, "task", 1, 250), AiJson.str(c, "titleIdea", 0, 100)));
        }
        calendar.sort((a, b) -> Integer.compare(a.day(), b.day()));

        if (strict && !problems.isEmpty()) {
            throw new InvalidAiOutputException("These items could not be verified against the evidence: "
                    + String.join("; ", problems.subList(0, Math.min(3, problems.size()))) + ".");
        }
        if (themes.isEmpty()) {
            throw new InvalidAiOutputException("No theme could be verified: every theme needs example titles that "
                    + "are copied exactly from the channel's video titles.");
        }
        return new Output(summary, List.copyOf(themes), List.copyOf(strengths), List.copyOf(gaps),
                AiJson.strings(root, "differentiation", 0, 4, 300), List.copyOf(ideas), List.copyOf(calendar),
                AiJson.strings(root, "limitations", 0, 6, 300), dropped);
    }

    private static void requireObject(JsonNode n, String list) {
        if (!n.isObject()) {
            throw new InvalidAiOutputException("Every item in '" + list + "' must be an object.");
        }
    }
}
