# Decisions — Kokoro TTS (#205)

## D1: Integration approach

**Choice:** New `KokoroTextToSpeech` class using sherpa-onnx C API (same native lib as SherpaOnnxTextToSpeech)
**Alternatives:**
- Direct ONNX inference via OnnxRuntimeLibrary — requires Java G2P reimplementation (misaki is very complex: spaCy POS tagging, BART fallback, lexicons)
- Generalize SherpaOnnxTextToSpeech for multiple model types — adds branching complexity to an already-working class
**Rationale:** sherpa-onnx v1.13.6 (our version) already supports Kokoro natively with built-in G2P. Different config struct fields warrant a separate class.
**Trade-offs:** Another TTS class to maintain; tightly coupled to sherpa-onnx C struct layout
**Sources:** sherpa-onnx c-api.h, k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html
**Exploration:** deep-analysis
**Status:** captured

## D2: Model variant

**Choice:** kokoro-en-v0_19 (English only, 11 voices, 330MB)
**Alternatives:**
- kokoro-multi-lang-v1_0 — 53 speakers, Chinese+English, 310MB (unused language support)
- kokoro-multi-lang-v1_1 — 103 speakers, latest, int8 available (overkill for demo)
**Rationale:** Matches our current English-only Piper voice set. Lighter deployment.
**Trade-offs:** No Chinese/multilingual voices
**Sources:** k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html
**Exploration:** quick
**Status:** captured
