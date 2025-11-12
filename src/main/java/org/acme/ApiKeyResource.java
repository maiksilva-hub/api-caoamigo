package org.acme;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

@Path("/admin/apikeys")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApiKeyResource {

    @GET
    @Operation(
            summary = "Lista todas as chaves de API",
            description = "Endpoint administrativo para listar todas as chaves de API cadastradas."
    )
    @APIResponse(
            responseCode = "200",
            description = "Lista de chaves retornada com sucesso."
    )
    public Response getAll() {
        return Response.ok(ApiKey.listAll()).build();
    }

    @POST
    @Operation(
            summary = "Gera e registra uma nova chave de API",
            description = "Cria um novo registro de chave de API. Retorna a chave gerada (keyValue) no corpo da resposta."
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiKeyCreationRequest.class))
    )
    @APIResponse(
            responseCode = "201",
            description = "Chave de API criada com sucesso."
    )
    @APIResponse(
            responseCode = "400",
            description = "Dados de entrada inválidos."
    )
    @Transactional
    public Response createKey(@Valid ApiKeyCreationRequest request) throws NoSuchAlgorithmException {
        ApiKey newKey = new ApiKey();
        newKey.ownerName = request.ownerName;
        newKey.accessLevel = request.accessLevel;

        String rawKey = UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(rawKey.getBytes());
        String encodedKey = Base64.getEncoder().encodeToString(hash).substring(0, 64);

        newKey.keyValue = encodedKey;
        newKey.persist();

        return Response.status(Response.Status.CREATED).entity(newKey).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Revoga (deleta) uma chave de API por ID",
            description = "Desativa e remove uma chave de API existente."
    )
    @APIResponse(responseCode = "204", description = "Chave revogada com sucesso.")
    @APIResponse(responseCode = "404", description = "Chave não encontrada.")
    @Transactional
    public Response deleteKey(@PathParam("id") Long id) {
        boolean deleted = ApiKey.deleteById(id);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    public static class ApiKeyCreationRequest {
        @NotBlank
        public String ownerName;

        @NotNull
        public ApiAccessLevel accessLevel;
    }
}