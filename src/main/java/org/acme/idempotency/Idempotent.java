package org.acme.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.ws.rs.NameBinding;

@NameBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /**
     * Tempo de expiração da chave de idempotência em segundos.
     * Depois desse período, a mesma requisição será tratada como nova.
     */
    int expireAfter() default 3600; // 1 hora por padrão
}
