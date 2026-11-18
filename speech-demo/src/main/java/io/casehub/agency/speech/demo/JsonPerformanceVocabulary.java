package io.casehub.agency.speech.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.agency.speech.GestureEntry;
import io.casehub.agency.speech.MoodEntry;
import io.casehub.agency.speech.PerformanceVocabulary;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JsonPerformanceVocabulary implements PerformanceVocabulary {

    private final Set<String> gestureTags;
    private final List<GestureEntry> promptEntries;
    private final Set<String> clipNames;
    private final Set<String> moodNames;
    private final List<MoodEntry> moods;

    public JsonPerformanceVocabulary(InputStream json) {
        var mapper = new ObjectMapper();
        try (json) {
            var root = mapper.readTree(json);

            var clips = new HashSet<String>();
            root.get("clips").fieldNames().forEachRemaining(clips::add);
            this.clipNames = Set.copyOf(clips);

            var gestureList = new ArrayList<GestureEntry>();
            var tags = new HashSet<String>();
            for (var node : root.get("gestures")) {
                var tag = node.get("tag").asText();
                tags.add(tag);
                gestureList.add(new GestureEntry(
                        tag,
                        node.has("description") ? node.get("description").asText() : "",
                        node.has("promptable") && node.get("promptable").asBoolean()));
            }
            this.gestureTags = Set.copyOf(tags);
            this.promptEntries = List.copyOf(gestureList);

            var moodList = new ArrayList<MoodEntry>();
            var mNames = new HashSet<String>();
            for (var node : root.get("moods")) {
                var name = node.get("name").asText();
                mNames.add(name);
                moodList.add(new MoodEntry(
                        name,
                        node.has("description") ? node.get("description").asText() : "",
                        node.has("promptable") && node.get("promptable").asBoolean()));
            }
            this.moodNames = Set.copyOf(mNames);
            this.moods = List.copyOf(moodList);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid vocabulary JSON", e);
        }
    }

    @Override
    public Set<String> gestureTags() {
        return gestureTags;
    }

    @Override
    public boolean isValidGesture(String tag) {
        return gestureTags.contains(tag);
    }

    @Override
    public List<GestureEntry> gesturePromptEntries() {
        return promptEntries;
    }

    @Override
    public Set<String> allClipNames() {
        return clipNames;
    }

    @Override
    public List<MoodEntry> moods() {
        return moods;
    }

    @Override
    public boolean isValidMood(String mood) {
        return moodNames.contains(mood);
    }
}
