/*
 * Copyright 2026-Present The Case Hub Authors
 * Licensed under the Apache License, Version 2.0.
 */
package io.casehub.engine.agentic.judgment;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import java.time.Duration;

final class AgentText {
  private static final Duration TIMEOUT = Duration.ofSeconds(120);

  static String invoke(AgentProvider provider, String systemPrompt, String prompt) {
    var events = provider.invoke(AgentSessionConfig.of(systemPrompt, prompt, TIMEOUT))
        .collect().asList().await().atMost(TIMEOUT);
    var text = new StringBuilder();
    for (var event : events) {
      if (event instanceof AgentEvent.TextDelta delta) {
        text.append(delta.text());
      } else if (event instanceof AgentEvent.InvocationComplete complete && complete.isError()) {
        throw new IllegalStateException("Agent invocation failed: " + text);
      }
    }
    return text.toString();
  }
}
