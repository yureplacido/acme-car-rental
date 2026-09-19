package org.acme.users.web;

import io.quarkus.qute.TemplateInstance;
import org.acme.users.security.CurrentUser;
import org.acme.users.templates.WhoAmITemplates;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/whoami")
public class WhoAmIResource {

    @Inject
    CurrentUser currentUser;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get() {
        return WhoAmITemplates.whoami(currentUser.getDisplayName());
    }
}