package org.acme.users.adapter.in.web;

import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.users.adapter.in.security.CurrentUser;
import org.acme.users.adapter.in.web.templates.WhoAmITemplates;

@Path("/whoami")
public class WhoAmIResource {

    private final CurrentUser currentUser;

    public WhoAmIResource(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get() {
        return WhoAmITemplates.whoami(currentUser.getDisplayName());
    }
}
