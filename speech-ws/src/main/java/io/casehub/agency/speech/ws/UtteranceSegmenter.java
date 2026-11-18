package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.PerformanceVocabulary;
import io.casehub.agency.speech.ws.protocol.Cue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UtteranceSegmenter {

    private static final Pattern TAG_RE = Pattern.compile("\\[(gesture|mood):([a-z0-9_-]+)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern MALFORMED_TAIL = Pattern.compile("\\[(gesture|mood):[^\\]]*$", Pattern.CASE_INSENSITIVE);

    private final PerformanceVocabulary vocabulary;

    public UtteranceSegmenter(PerformanceVocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    public List<ParsedSegment> segment(String text) {
        if (text == null || text.isBlank()) return List.of();

        text = MALFORMED_TAIL.matcher(text).replaceAll("");

        var tagInfos = extractTags(text);
        var cleanFull = removeTags(text);

        var sentences = SENTENCE_END.split(cleanFull);
        if (sentences.length == 0) return List.of();

        var results = new ArrayList<ParsedSegment>();
        int cleanOffset = 0;

        for (var sentence : sentences) {
            var trimmed = sentence.trim();
            if (trimmed.isEmpty()) {
                cleanOffset += sentence.length() + 1;
                continue;
            }

            var cues = new ArrayList<Cue>();
            int sentenceStart = cleanFull.indexOf(trimmed, cleanOffset);
            int sentenceEnd = sentenceStart + trimmed.length();

            for (var tag : tagInfos) {
                if (tag.cleanPosition >= sentenceStart && tag.cleanPosition < sentenceEnd) {
                    var cue = tag.toCue();
                    if (cue != null) cues.add(cue);
                }
            }

            // Trailing tags: tags after the last word of this sentence but before the next sentence
            // bind to the first word of this sentence (D2 rule 3)
            for (var tag : tagInfos) {
                if (tag.cleanPosition >= sentenceEnd && tag.cleanPosition < sentenceEnd + 2) {
                    var cue = tag.toCueAtWord(0);
                    if (cue != null) cues.add(cue);
                }
            }

            var canonical = buildCanonical(trimmed, cues);
            results.add(new ParsedSegment(trimmed, cues, canonical));
            cleanOffset = sentenceEnd;
        }

        // Handle tags with no sentence (pure markup input)
        // These are dropped — no empty segments (D2 rule 4)

        return List.copyOf(results);
    }

    private record TagInfo(String type, String value, int originalPosition, int cleanPosition, boolean valid) {
        Cue toCue() {
            if (!valid) return null;
            if ("gesture".equals(type)) return new Cue.GestureCue(value, 0L);
            if ("mood".equals(type)) return new Cue.MoodCue(value, 0L);
            return null;
        }

        Cue toCueAtWord(int wordIndex) {
            return toCue();
        }
    }

    private List<TagInfo> extractTags(String text) {
        var tags = new ArrayList<TagInfo>();
        Matcher m = TAG_RE.matcher(text);

        int tagBytesBeforeCurrent = 0;
        while (m.find()) {
            String type = m.group(1).toLowerCase();
            String value = m.group(2).toLowerCase();
            boolean valid = "gesture".equals(type) ? vocabulary.isValidGesture(value) : vocabulary.isValidMood(value);

            int cleanPos = m.start() - tagBytesBeforeCurrent;
            tags.add(new TagInfo(type, value, m.start(), cleanPos, valid));
            tagBytesBeforeCurrent += m.end() - m.start();
        }
        return tags;
    }

    private String removeTags(String text) {
        return TAG_RE.matcher(text).replaceAll("").replaceAll("\\s{2,}", " ").trim();
    }

    private String buildCanonical(String cleanText, List<Cue> cues) {
        var sb = new StringBuilder();
        for (var cue : cues) {
            if (cue instanceof Cue.GestureCue g) {
                sb.append("[gesture:").append(g.tag()).append("] ");
            } else if (cue instanceof Cue.MoodCue m) {
                sb.append("[mood:").append(m.mood()).append("] ");
            }
        }
        sb.append(cleanText);
        return sb.toString();
    }
}
