package io.casehub.agency.core.yaml.runtime;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/agency/specs")
@Produces(MediaType.APPLICATION_JSON)
public class AgencySpecsResource {

    private final AgencySpecsBean specsBean;

    @Inject
    public AgencySpecsResource(AgencySpecsBean specsBean) {
        this.specsBean = specsBean;
    }

    @GET
    public String get() {
        return specsBean.json();
    }
}
