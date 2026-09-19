package org.acme.reservation.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class CurrentUser {

    @Inject
    SecurityIdentity identity;

    public String getUserId() {
        return identity.isAnonymous() ? null : identity.getPrincipal().getName();
    }

    public String getDisplayName() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }
}