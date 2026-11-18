package io.casehub.agency.speech;

import java.util.List;
import java.util.Set;

public interface PerformanceVocabulary {
    Set<String> gestureTags();
    boolean isValidGesture(String tag);
    List<GestureEntry> gesturePromptEntries();
    Set<String> allClipNames();
    List<MoodEntry> moods();
    boolean isValidMood(String mood);
}
