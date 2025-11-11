package org.acme;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

@Entity
public class ApiKey extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(readOnly = true)
    public Long id;

    @Column(unique = true, nullable = false, length = 64)
    @Schema(readOnly = true)
    public String keyValue;

    @NotBlank
    @Size(max = 100)
    public String ownerName;

    @NotNull
    public ApiAccessLevel accessLevel = ApiAccessLevel.READ_ONLY;

    @NotNull
    public Instant createdAt = Instant.now();

    public Instant expiresAt;

    public static ApiKey findByKeyValue(String keyValue) {
        return find("keyValue", keyValue).firstResult();
    }
}