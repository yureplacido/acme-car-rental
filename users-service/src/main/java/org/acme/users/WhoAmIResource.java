package org.acme.users;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Livro 6.1.1: expõe o template whoami.html (Qute) como página HTML.
 * Antes de configurar a segurança, mostra "anonymous"; depois do cap.6.1.2
 * exige login (Keycloak) e exibe o nome do usuário autenticado.
 */
@Path("/whoami")
public class WhoAmIResource {

    @Inject
    Template whoami;

    @Inject
    SecurityContext securityContext;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get() {
        String userId = securityContext.getUserPrincipal() != null
                ? securityContext.getUserPrincipal().getName() : null;
        return whoami.data("name", userId);
    }
}