package io.casehub.agency.conversation;

import io.casehub.work.progress.ProgressInstance;

@FunctionalInterface
public interface ProgressRenderer {
    String render(ProgressInstance progress);
}
