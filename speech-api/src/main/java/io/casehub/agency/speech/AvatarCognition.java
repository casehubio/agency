package io.casehub.agency.speech;

import io.casehub.neocortex.cognition.prompt.CognitiveLifecycle;

import java.util.function.Supplier;

public interface AvatarCognition extends CognitiveLifecycle {

    SpeechPromptAssembler wrapAssembler(SpeechPromptAssembler base, String agentId, String tenantId,
                                        Supplier<String> subjectIdSupplier);
}
