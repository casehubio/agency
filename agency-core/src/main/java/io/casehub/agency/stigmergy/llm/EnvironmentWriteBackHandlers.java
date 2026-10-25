package io.casehub.agency.stigmergy.llm;

public final class EnvironmentWriteBackHandlers {

    private EnvironmentWriteBackHandlers() {}

    public static ObservationHandler depositSignal(String prefix) {
        return (obs, raw, ctx) ->
            ctx.depositSignal(prefix + ":" + obs.patternId(), obs.confidence());
    }

    public static ObservationHandler storeObservation() {
        return (obs, raw, ctx) -> ctx.storeObservation(obs);
    }

    public static ObservationHandler all() {
        return (obs, raw, ctx) -> {
            ctx.storeObservation(obs);
            ctx.depositSignal("obs:" + obs.patternId(), obs.confidence());
        };
    }
}
