package io.casehub.agency.speech.ws;

import io.casehub.eidos.api.AssembledPrompt;
import io.casehub.agency.speech.ConversationTurn;
import io.casehub.agency.speech.PromptContext;
import io.casehub.agency.speech.PromptSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPromptAssemblerTest {

    @Test
    void assemblesWithSystemPromptAndNoHistory() {
        var             assembler = new DefaultPromptAssembler("You are a helpful assistant.");
        AssembledPrompt result    = assembler.assemble("Hello", List.of());
        assertThat(result.systemPrompt()).isEqualTo("You are a helpful assistant.");
        assertThat(result.userMessage()).contains("Hello");
    }

    @Test
    void includesConversationHistory() {
        var assembler = new DefaultPromptAssembler("Be concise.");
        var history = List.of(
                new ConversationTurn("user", "Hi"),
                new ConversationTurn("assistant", "Hello!"));
        AssembledPrompt result = assembler.assemble("How are you?", history);
        assertThat(result.userMessage()).contains("Hi");
        assertThat(result.userMessage()).contains("Hello!");
        assertThat(result.userMessage()).contains("How are you?");
    }

    @Test
    void usesDefaultSystemPromptWhenNull() {
        var             assembler = new DefaultPromptAssembler(null);
        AssembledPrompt result    = assembler.assemble("Test", List.of());
        assertThat(result.systemPrompt()).isNotBlank();
        assertThat(result.userMessage()).contains("Test");
    }

    @Test
    void separatesSystemPromptFromUserPrompt() {
        var             assembler = new DefaultPromptAssembler("System instructions");
        AssembledPrompt result    = assembler.assemble("User question", List.of());
        assertThat(result.systemPrompt()).isEqualTo("System instructions");
        assertThat(result.userMessage()).doesNotContain("System instructions");
    }

    @Test
    void historyOrderIsPreserved() {
        var assembler = new DefaultPromptAssembler("sys");
        var history = List.of(
                new ConversationTurn("user", "first"),
                new ConversationTurn("assistant", "second"),
                new ConversationTurn("user", "third"));
        AssembledPrompt result     = assembler.assemble("fourth", history);
        String          userPrompt = result.userMessage();
        int             firstIdx   = userPrompt.indexOf("first");
        int             secondIdx  = userPrompt.indexOf("second");
        int             thirdIdx   = userPrompt.indexOf("third");
        int             fourthIdx  = userPrompt.indexOf("fourth");
        assertThat(firstIdx).isLessThan(secondIdx);
        assertThat(secondIdx).isLessThan(thirdIdx);
        assertThat(thirdIdx).isLessThan(fourthIdx);
    }

    @Test
    void speakerLabelAppearsInHistory() {
        var assembler = new DefaultPromptAssembler("sys");
        var history = List.of(
                new ConversationTurn("user", "Hi", "Mark"),
                new ConversationTurn("assistant", "Hello!"));
        AssembledPrompt result = assembler.assemble("How are you?", history);
        assertThat(result.userMessage()).contains("Mark (User): Hi");
        assertThat(result.userMessage()).contains("Assistant: Hello!");
    }

    @Test
    void speakerNamesAppendedToSystemPrompt() {
        var assembler = new DefaultPromptAssembler("Be helpful.");
        var history = List.of(
                new ConversationTurn("user", "Hi", "Mark"),
                new ConversationTurn("user", "Hey", "Sarah"));
        AssembledPrompt result = assembler.assemble("Test", history);
        assertThat(result.systemPrompt()).contains("Speaking with: Mark, Sarah.");
    }

    @Test
    void noSpeakersDoesNotModifySystemPrompt() {
        var             assembler = new DefaultPromptAssembler("Be helpful.");
        var             history   = List.of(new ConversationTurn("user", "Hi"));
        AssembledPrompt result    = assembler.assemble("Test", history);
        assertThat(result.systemPrompt()).isEqualTo("Be helpful.");
    }

    @Test
    void conversationTurnWithSpeaker() {
        var turn = new ConversationTurn("user", "hello", "Mark");
        assertThat(turn.speaker()).isEqualTo("Mark");
    }

    @Test
    void conversationTurnWithoutSpeaker() {
        var turn = new ConversationTurn("user", "hello");
        assertThat(turn.speaker()).isNull();
    }

    // --- PromptSection composition tests ---

    @Test
    void assembleWithSectionsAppendsToSystemPrompt() {
        PromptSection section = ctx -> "## Gestures\n[gesture:nod] — agreement";
        var assembler = new DefaultPromptAssembler("You are helpful.", List.of(section));
        var context = new PromptContext("agent-1", "tenant-1", "user-1");

        var result = assembler.assemble("hello", List.of(), context);

        assertThat(result.systemPrompt()).contains("You are helpful.");
        assertThat(result.systemPrompt()).contains("## Gestures");
        assertThat(result.systemPrompt()).contains("[gesture:nod]");
    }

    @Test
    void assembleWithoutContextSkipsSections() {
        PromptSection section = ctx -> "should not appear";
        var assembler = new DefaultPromptAssembler("Base.", List.of(section));

        var result = assembler.assemble("hello", List.of());

        assertThat(result.systemPrompt()).isEqualTo("Base.");
    }

    @Test
    void assembleSkipsNullSectionContributions() {
        PromptSection returnsNull = ctx -> null;
        PromptSection returnsText = ctx -> "visible section";
        var assembler = new DefaultPromptAssembler("Base.", List.of(returnsNull, returnsText));
        var context = new PromptContext("a", "t", null);

        var result = assembler.assemble("hello", List.of(), context);

        assertThat(result.systemPrompt()).contains("visible section");
        assertThat(result.systemPrompt()).doesNotContain("null");
    }

    @Test
    void assembleWithEmptySectionsListUnchanged() {
        var assembler = new DefaultPromptAssembler("Base.", List.of());
        var context = new PromptContext("a", "t", null);

        var result = assembler.assemble("hello", List.of(), context);

        assertThat(result.systemPrompt()).isEqualTo("Base.");
    }
}
