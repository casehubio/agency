package io.casehub.agency.orchestration.topology;

import org.jspecify.annotations.Nullable;

import java.util.function.LongConsumer;

public interface TokenRecordable {

    void setTokenRecorder(@Nullable LongConsumer recorder);
}
