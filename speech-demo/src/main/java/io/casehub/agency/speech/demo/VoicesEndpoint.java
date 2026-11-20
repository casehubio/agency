package io.casehub.agency.speech.demo;

import io.casehub.agency.speech.ws.TtsModelRegistry;
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
    TtsModelRegistry ttsRegistry;

    @GET
    @Path("/voices")
    public Response voices() {
        List<String> keys = ttsRegistry.models().keySet().stream().sorted().toList();
        return Response.ok(keys).build();
    }
}
