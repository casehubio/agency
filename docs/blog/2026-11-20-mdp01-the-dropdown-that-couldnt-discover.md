---
layout: post
title: "The Dropdown That Couldn't Discover"
date: 2026-11-20
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [avatar, speech, frontend, rest-api]
---

# The Dropdown That Couldn't Discover

The voice dropdown in the avatar demo had the same problem as every hardcoded list: it was always wrong. Every voice registered at runtime — cloned voices discovered from `.wav` files, new engine variants provisioned on startup — lived in `TtsModelRegistry` and worked perfectly if you typed the key into a WebSocket message. But the UI couldn't see them.

The dropdown was fifty lines of `<option>` tags baked into `index.html`. Adding a voice meant editing HTML. Cloned voices discovered at boot time by `Qwen3VoicePool.createProvisioned()` never appeared at all — the user had no way to select them without knowing the internal key.

The fix is a single JAX-RS endpoint. `GET /api/avatar/voices` returns `TtsModelRegistry.models().keySet()`, sorted. Twenty-six lines of Java:

```java
@Path("/api/avatar")
@Produces(MediaType.APPLICATION_JSON)
public class VoicesEndpoint {

    @Inject
    TtsModelRegistry ttsRegistry;

    @GET
    @Path("/voices")
    public Response voices() {
        List<String> keys = ttsRegistry.models().keySet()
                .stream().sorted().toList();
        return Response.ok(keys).build();
    }
}
```

The frontend fetches this list on page load and groups voices by engine prefix — `kokoro:`, `qwen3:`, `sherpa:`, `qwen3:clone:` — into `<optgroup>` sections. Fifty hardcoded options become a `fetch()` call that reflects whatever the backend actually has.

There's a tradeoff. The old dropdown had rich labels — "Heart (US) · 4.5" with MOS scores and locale tags. The dynamic version shows bare keys: "af heart", "ryan". Runtime-discovered voices don't carry metadata, so the endpoint returns what `TtsModelRegistry` knows: the key. A richer response — objects with display names, engine type, quality scores — is the natural follow-up, but it needs `TtsModelRegistry` to carry that metadata alongside the `TextToSpeech` instances. That's a different shape of change.

The fallback is deliberate. If the fetch fails, the dropdown reverts to a single `lessac-medium` option. If the user sends a message before voices load (the placeholder shows "Loading voices..."), the empty-string model key reaches `TurnExecutor.resolveTts()`, which falls back to the default TTS engine. No crash, no silent failure — just the default voice.

What matters here isn't the endpoint. It's the principle: the UI should render what the backend knows, not maintain its own inventory. The dropdown was a fork of the registry — same data, different source of truth, guaranteed to diverge. Now there's one source.
