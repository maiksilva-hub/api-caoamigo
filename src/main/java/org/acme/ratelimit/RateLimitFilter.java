package org.acme.ratelimit;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.acme.ratelimit.RateLimiterService.RateLimitResult;

import java.io.IOException;

@Provider
@Priority(Priorities.HEADER_DECORATOR + 1)
public class RateLimitFilter implements ContainerRequestFilter {

    @Inject
    RateLimiterService rateLimiterService;

    private String getClientIdentifier(ContainerRequestContext requestContext) {
        String apiKey = requestContext.getHeaderString("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return "API_KEY:" + apiKey;
        }

        String forwardedFor = requestContext.getHeaderString("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return "IP_FALLBACK";
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String clientKey = getClientIdentifier(requestContext);

        if (clientKey.equals("IP_FALLBACK")) {
            return;
        }

        RateLimitResult result = rateLimiterService.checkRateLimit(clientKey);

        requestContext.getHeaders().add("X-RateLimit-Limit", String.valueOf(result.limit));
        requestContext.getHeaders().add("X-RateLimit-Remaining", String.valueOf(result.remaining));
        requestContext.getHeaders().add("X-RateLimit-Reset", String.valueOf(rateLimiterService.windowSeconds));


        if (result.exceeded) {
            requestContext.abortWith(
                    Response.status(429)
                            .entity("Limite de requisições excedido. Tente novamente em " + rateLimiterService.windowSeconds + " segundos.")
                            .header("Retry-After", String.valueOf(rateLimiterService.windowSeconds))
                            .build()
            );
        }
    }
}