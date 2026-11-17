## D1: Wrapper location

**Choice:** Connectors repo (`connectors-voice-sherpa`) — new `Qwen3VoicePool` class
**Alternatives:**
- Agency demo only — simpler but not reusable by other consumers
- Single server voice cycling — low memory but 2s latency on every voice switch
**Rationale:** The voice pool is a reusable building block. Any CaseHub app that wants cloned Qwen3 voices should get it from connectors, not copy orchestration logic from the demo.
**Trade-offs:** Requires a connectors repo change and re-publish of the jar before agency can consume it.
**Sources:** `Qwen3TextToSpeech.java` (connectors), `SpeechProducers.java` (agency demo), `qwen_tts --help` output
**Exploration:** quick
**Status:** captured

## D2: Server management strategy

**Choice:** Pool with LRU eviction — start servers on-demand, keep up to N active, evict LRU
**Alternatives:**
- All servers at once — zero latency but ~10GB RAM for 12 voices
- Single shared server with restart — ~850MB but 2s latency on every voice switch
**Rationale:** Balances memory (~3GB for 3 active servers) with acceptable cold-start latency (~2s only on first request per evicted voice). Demo usage patterns are conversational — users talk to one voice at a time, rarely switching rapidly.
**Trade-offs:** First request for a cold voice pays ~2s startup. More complex than all-at-once.
**Sources:** Binary testing: `qwen_tts --load-voice` only loads one voice; server startup ~2s; ~850MB per Base model instance
**Exploration:** quick
**Status:** captured
