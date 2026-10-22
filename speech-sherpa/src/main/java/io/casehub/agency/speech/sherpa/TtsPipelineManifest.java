package io.casehub.agency.speech.sherpa;

public sealed interface TtsPipelineManifest permits CosyVoice3Manifest {
    PipelineHeader header();
}
