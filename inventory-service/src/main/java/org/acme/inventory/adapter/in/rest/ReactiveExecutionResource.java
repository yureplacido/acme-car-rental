package org.acme.inventory.adapter.in.rest;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Diagnostic endpoint used by Cap. 8 to make Quarkus execution-model behavior observable.
 * It is intentionally not part of the inventory business API.
 */
@Path("/reactive/execution")
@Produces(MediaType.TEXT_PLAIN)
public class ReactiveExecutionResource {

    @GET
    @Path("/event-loop")
    public Uni<String> eventLoop() {
        return Uni.createFrom().item(Thread.currentThread().getName());
    }

    @GET
    @Path("/worker")
    @Blocking
    public Uni<String> worker() {
        return Uni.createFrom().item(Thread.currentThread().getName());
    }
}
