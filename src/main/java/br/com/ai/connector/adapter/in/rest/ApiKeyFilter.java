package br.com.ai.connector.adapter.in.rest;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyFilter implements ContainerRequestFilter {

    @ConfigProperty(name = "connector.api-key")
    String expectedApiKey;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            return;
        }
        String provided = requestContext.getHeaderString("X-Connector-Key");
        if (!expectedApiKey.equals(provided)) {
            throw new WebApplicationException("Nao autorizado.", 401);
        }
    }
}
