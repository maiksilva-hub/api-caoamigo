package org.acme.idempotency;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.smallrye.mutiny.Uni;
import java.util.function.Function;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

@Provider
@ApplicationScoped
@Priority(Priorities.HEADER_DECORATOR)
public class IdempotencyFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    private static final String IDEMPOTENT_CONTEXT_PROPERTY = "idempotent-context";

    @Inject
    @CacheName("idempotency-cache")
    Cache cache;

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        Method method = resourceInfo.getResourceMethod();
        Class<?> clazz = resourceInfo.getResourceClass();

        Idempotent methodAnnotation = method.getAnnotation(Idempotent.class);
        Idempotent classAnnotation = clazz.getAnnotation(Idempotent.class);

        if (methodAnnotation == null && classAnnotation == null) {
            return;
        }

        Idempotent idempotentConfig = methodAnnotation != null ? methodAnnotation : classAnnotation;

        String idempotencyKey = requestContext.getHeaderString(IDEMPOTENCY_KEY_HEADER);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            requestContext.abortWith(Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("O cabeçalho X-Idempotency-Key é obrigatório para esta operação")
                    .build());
            return;
        }

        String cacheKey = createCacheKey(requestContext, idempotencyKey);

        try {
            Uni<Optional> recordUni = cache.get(
                    cacheKey,
                    k -> Optional.empty()
            );

            Optional<IdempotencyRecord> optionalRecord = (Optional<IdempotencyRecord>) recordUni.await().indefinitely();
            IdempotencyRecord record = optionalRecord.orElse(null);

            if (record != null) {
                if (record.getExpiry().isAfter(Instant.now())) {
                    requestContext.abortWith(Response
                            .status(record.getStatus())
                            .entity(record.getBody())
                            .build());
                    return;
                }
            }

            requestContext.setProperty(IDEMPOTENT_CONTEXT_PROPERTY,
                    new IdempotentContext(cacheKey, idempotentConfig.expireAfter()));

        } catch (RuntimeException e) {
            requestContext.abortWith(Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Falha ao acessar o cache de idempotência.")
                    .build());
            return;
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        IdempotentContext context = (IdempotentContext) requestContext.getProperty(IDEMPOTENT_CONTEXT_PROPERTY);
        if (context == null) {
            return;
        }

        if (responseContext.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {

            IdempotencyRecord record = new IdempotencyRecord(
                    responseContext.getStatus(),
                    responseContext.getEntity(),
                    Instant.now().plusSeconds(context.getExpireAfter())
            );

            Function<String, Uni<IdempotencyRecord>> storageFunction = key -> Uni.createFrom().item(record);

            cache.get(context.getCacheKey(), storageFunction)
                    .subscribe().with(result -> {}, failure -> {});
        }
    }

    private String createCacheKey(ContainerRequestContext requestContext, String idempotencyKey) {
        return requestContext.getMethod() + ":" +
                requestContext.getUriInfo().getPath() + ":" +
                idempotencyKey;
    }

    private static class IdempotentContext {
        private final String cacheKey;
        private final int expireAfter;

        public IdempotentContext(String cacheKey, int expireAfter) {
            this.cacheKey = cacheKey;
            this.expireAfter = expireAfter;
        }

        public String getCacheKey() {
            return cacheKey;
        }

        public int getExpireAfter() {
            return expireAfter;
        }
    }

    public static class IdempotencyRecord {
        private final int status;
        private final Object body;
        private final Instant expiry;

        public IdempotencyRecord(int status, Object body, Instant expiry) {
            this.status = status;
            this.body = body;
            this.expiry = expiry;
        }

        public int getStatus() {
            return status;
        }

        public Object getBody() {
            return body;
        }

        public Instant getExpiry() {
            return expiry;
        }
    }
}