package io.casehub.agency.deliberation.conversation;

import io.casehub.work.progress.ProgressInstance;

@FunctionalInterface
public interface ProgressRenderer {
    String render(ProgressInstance progress);
}
