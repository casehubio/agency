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
        if (text == null || text.isBlank()) {return List.of();}

        text = MALFORMED_TAIL.matcher(text).replaceAll("");

        var tagInfos  = extractTags(text);
        var cleanFull = removeTags(text);

        var sentences = SENTENCE_END.split(cleanFull);
        if (sentences.length == 0) {return List.of();}

        var results = new ArrayList<ParsedSegment>();

        // Compute sentence boundaries by cumulative offset instead of indexOf (#492 fix 1)
        int[] sentenceStarts = new int[sentences.length];
        int[] sentenceEnds   = new int[sentences.length];
        int   cursor         = 0;
        for (int i = 0; i < sentences.length; i++) {
            int idx = cleanFull.indexOf(sentences[i].trim(), cursor);
            if (idx < 0) {idx = cursor;}
            sentenceStarts[i] = idx;
            sentenceEnds[i]   = idx + sentences[i].trim().length();
                                cursor = sentenceEnds[i];
        }

        var consumed = new java.util.HashSet<Integer>();

        for (int i = 0; i < sentences.length; i++) {
            var trimmed = sentences[i].trim();
            if (trimmed.isEmpty()) {continue;}

            int sentenceStart     = sentenceStarts[i];
            int sentenceEnd       = sentenceEnds[i];
            int nextSentenceStart = (i + 1 < sentences.length) ? sentenceStarts[i + 1] : Integer.MAX_VALUE;

            var cues = new ArrayList<Cue>();

            // Inline tags: within this sentence's text span
            for (int t = 0; t < tagInfos.size(); t++) {
                var tag = tagInfos.get(t);
                if (!consumed.contains(t) && tag.cleanPosition >= sentenceStart && tag.cleanPosition < sentenceEnd) {
                    var cue = tag.toCue();
                    if (cue != null) {cues.add(cue);}
                    consumed.add(t);
                }
            }

            // Trailing tags: between this sentence end and the next sentence start (inclusive)
            // bind to this sentence (D2 rule 3, #492 fix 2)
            for (int t = 0; t < tagInfos.size(); t++) {
                var tag = tagInfos.get(t);
                if (!consumed.contains(t) && tag.cleanPosition >= sentenceEnd && tag.cleanPosition <= nextSentenceStart) {
                    var cue = tag.toCueAtWord(0);
                    if (cue != null) {cues.add(cue);}
                    consumed.add(t);
                }
            }

            var canonical = buildCanonical(trimmed, cues);
            results.add(new ParsedSegment(trimmed, cues, canonical));
        }

        return List.copyOf(results);
    }

    private record TagInfo(String type, String value, int originalPosition, int cleanPosition, boolean valid) {
        Cue toCue() {
            if (!valid) return null;
            if ("gesture".equals(type)) return new Cue.GestureCue(value, 0L, cleanPosition);
            if ("mood".equals(type)) return new Cue.MoodCue(value, 0L, cleanPosition);
            return null;
        }

        Cue toCueAtWord(int wordIndex) {
            if (!valid) return null;
            if ("gesture".equals(type)) return new Cue.GestureCue(value, 0L, 0);
            if ("mood".equals(type)) return new Cue.MoodCue(value, 0L, 0);
            return null;
        }
    }

    private List<TagInfo> extractTags(String text) {
        var     tags = new ArrayList<TagInfo>();
        Matcher m    = TAG_RE.matcher(text);

        while (m.find()) {
            String  type  = m.group(1).toLowerCase();
            String  value = m.group(2).toLowerCase();
            boolean valid = "gesture".equals(type) ? vocabulary.isValidGesture(value) : vocabulary.isValidMood(value);

            // Compute clean position by removing all tags from the prefix,
            // then applying the same space collapse as removeTags (#492 fix 3)
            String prefix      = text.substring(0, m.start());
            String cleanPrefix = TAG_RE.matcher(prefix).replaceAll("").replaceAll("\\s{2,}", " ");
            int    cleanPos    = cleanPrefix.length();

            tags.add(new TagInfo(type, value, m.start(), cleanPos, valid));
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
