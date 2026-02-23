package activityreport.client;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Quarkus REST client for JIRA API.
 */
@RegisterRestClient(configKey = "jira")
@RegisterProvider(BasicAuthHeaderFactory.class)
@Path("/rest/api/3")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface JiraRestClient {

    @GET
    @Path("/search")
    JsonNode search(@QueryParam("jql") String jql,
                    @QueryParam("fields") String fields,
                    @QueryParam("maxResults") int maxResults);
}
