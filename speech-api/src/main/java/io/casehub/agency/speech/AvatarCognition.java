package io.casehub.agency.speech;

import io.casehub.neocortex.cognition.prompt.CognitiveLifecycle;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public interface AvatarCognition extends CognitiveLifecycle {

    SpeechPromptAssembler wrapAssembler(SpeechPromptAssembler base, String agentId, String tenantId,
                                        Supplier<String> subjectIdSupplier);

    default boolean providesAffect() { return false; }

    default @Nullable Affect currentAffect(String agentId, String tenantId) { return null; }
}
