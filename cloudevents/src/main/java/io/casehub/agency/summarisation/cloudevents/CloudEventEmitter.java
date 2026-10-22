package io.casehub.agency.summarisation.cloudevents;

import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.agency.summarisation.LevelEvent;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public class CloudEventEmitter<E> {

    private static final URI DEFAULT_SOURCE = URI.create("/summarisation");

    private final EventSink<CloudEvent> sink;
    private final String cloudEventType;
    private final Function<E, byte[]> serializer;

    public CloudEventEmitter(EventStreamBus<E> bus,
                             EventSink<CloudEvent> sink,
                             String cloudEventType,
                             Function<E, byte[]> serializer) {
        this.sink = Objects.requireNonNull(sink);
        this.cloudEventType = Objects.requireNonNull(cloudEventType);
        this.serializer = Objects.requireNonNull(serializer);
        bus.subscribe(e -> true, this::onEvent);
    }

    private void onEvent(LevelEvent<E> event) {
        var builder = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(cloudEventType)
                .withSource(DEFAULT_SOURCE)
                .withTime(OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(event.timestamp()), ZoneOffset.UTC))
                .withData("application/json", serializer.apply(event.payload()));

        if (event.tenancyId() != null) {
            builder.withExtension("tenancyid", event.tenancyId());
        }

        sink.emit(builder.build());
    }
}
