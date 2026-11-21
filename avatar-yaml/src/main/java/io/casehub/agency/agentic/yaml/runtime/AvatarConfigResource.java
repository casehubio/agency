package io.casehub.agency.core.yaml.runtime;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/avatar")
@Produces(MediaType.APPLICATION_JSON)
public class AvatarConfigResource {

    @Inject
    Instance<AvatarConfigBean> avatarConfig;

    @GET
    @Path("/config")
    public Response getConfig() {
        if (!avatarConfig.isResolvable()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"No avatar configuration found\"}")
                    .build();
        }
        return Response.ok(avatarConfig.get().json()).build();
    }
}
