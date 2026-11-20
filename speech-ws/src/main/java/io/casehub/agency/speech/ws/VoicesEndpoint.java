package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.TtsModelRegistry;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/avatar")
@Produces(MediaType.APPLICATION_JSON)
public class VoicesEndpoint {

    @Inject
    Instance<TtsModelRegistry> ttsRegistry;

    @GET
    @Path("/voices")
    public Response voices() {
        if (!ttsRegistry.isResolvable()) {
            return Response.ok(List.of()).build();
        }
        List<String> keys = ttsRegistry.get().models().keySet().stream().sorted().toList();
        return Response.ok(keys).build();
    }
}
