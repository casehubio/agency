package io.casehub.agency.summarisation.cloudevents;

@FunctionalInterface
public interface EventSink<T> {
    void emit(T event);
}
