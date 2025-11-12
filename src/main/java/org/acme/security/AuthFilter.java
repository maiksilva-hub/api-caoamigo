package org.acme.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.acme.ApiAccessLevel;
import org.acme.ApiKey;

import java.io.IOException;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String UNAUTHORIZED_MESSAGE = "Acesso negado. Chave de API ausente ou inválida.";
    private static final String FORBIDDEN_MESSAGE = "Acesso negado. Sua chave de API não possui permissão para esta operação.";

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        if (path.contains("openapi") || path.contains("swagger-ui") || requestContext.getMethod().equalsIgnoreCase("GET")) {
            return;
        }

        String apiKey = requestContext.getHeaderString(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            requestContext.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity(UNAUTHORIZED_MESSAGE)
                            .build());
            return;
        }

        ApiKey keyEntity = ApiKey.findByKeyValue(apiKey);

        if (keyEntity == null) {
            requestContext.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity(UNAUTHORIZED_MESSAGE)
                            .build());
            return;
        }


        ApiAccessLevel requiredLevel = getRequiredAccessLevel(requestContext.getMethod());

        if (keyEntity.accessLevel.compareTo(requiredLevel) < 0) {
            requestContext.abortWith(
                    Response.status(Response.Status.FORBIDDEN)
                            .entity(FORBIDDEN_MESSAGE)
                            .build());
            return;
        }

        requestContext.setProperty("currentApiKeyOwner", keyEntity.ownerName);
    }

    private ApiAccessLevel getRequiredAccessLevel(String method) {
        switch (method.toUpperCase()) {
            case "POST":
            case "PUT":
            case "DELETE":
                return ApiAccessLevel.READ_WRITE;
            case "GET":
            default:
                return ApiAccessLevel.READ_ONLY;
        }
    }
}