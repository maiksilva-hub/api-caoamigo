package org.acme;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.acme.idempotency.Idempotent;
import org.eclipse.microprofile.openapi.annotations.headers.Header;

import java.util.Collections;
import java.util.List;
import java.util.Set;

// MUDANÇA 1: Versão V2
@Path("/v2/racas")
@Consumes("application/json")
@Produces("application/json")
public class RacaV2Resource { // MUDANÇA 2: Nome da classe

    // MUDANÇA V2: Ordena por nome da raça (ascendente)
    @GET
    @Operation(
            summary = "Retorna todas as raças (getAll) - V2",
            description = "V2: Retorna uma lista de raças ordenada pelo nome (alfabética) por padrão."
    )
    @APIResponse(
            responseCode = "200",
            description = "Lista retornada com sucesso",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Raca.class, type = SchemaType.ARRAY)
            )
    )
    @Timeout(3000)
    @Fallback(fallbackMethod = "fallbackListaVazia")
    public Response getAll(){
        // ALTERAÇÃO V2: Ordenação por nome, ascendente
        return Response.ok(Raca.listAll(Sort.by("nome", Sort.Direction.Ascending))).build();
    }

    public Response fallbackListaVazia() {
        return Response.ok(Collections.emptyList()).header("X-Fallback", "true").build();
    }

    @GET
    @Path("{id}")
    @Operation(
            summary = "Retorna uma raça pela busca por ID (getById) - V2",
            description = "Retorna uma raça específica pela busca de ID colocado na URL no formato JSON por padrão"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Raca.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado",
            content = @Content(
                    mediaType = "text/plain",
                    schema = @Schema(implementation = String.class))
    )
    public Response getById(
            @Parameter(description = "Id da raça a ser pesquisada", required = true)
            @PathParam("id") long id){
        Raca entity = Raca.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(entity).build();
    }

    @GET
    @Operation(
            summary = "Retorna as raças conforme o sistema de pesquisa (search) - V2",
            description = "V2: Retorna uma lista de raças com ordenação padrão por nome (alfabética)."
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SearchRacaResponse.class)
            )
    )
    @Path("/search")
    public Response search(
            @Parameter(description = "Query de buscar por nome ou descrição")
            @QueryParam("q") String q,
            @Parameter(description = "Campo de ordenação da lista de retorno")
            @QueryParam("sort") @DefaultValue("nome") String sort, // MUDANÇA V2: Padrão nome
            @Parameter(description = "Esquema de filtragem de raças por ordem crescente ou decrescente")
            @QueryParam("direction") @DefaultValue("asc") String direction, // MUDANÇA V2: Padrão ascendente
            @Parameter(description = "Define qual página será retornada na response")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Define quantos objetos serão retornados por query")
            @QueryParam("size") @DefaultValue("4") int size
    ){
        Set<String> allowed = Set.of("id", "nome", "descricao");
        if(!allowed.contains(sort)){
            sort = "nome"; // MUDANÇA V2: Usar o novo padrão
        }

        Sort sortObj = Sort.by(
                sort,
                "desc".equalsIgnoreCase(direction) ? Sort.Direction.Descending : Sort.Direction.Ascending
        );

        int effectivePage = Math.max(page, 0);

        PanacheQuery<Raca> query;

        if (q == null || q.isBlank()) {
            query = Raca.findAll(sortObj);
        } else {
            query = Raca.find(
                    "lower(nome) like ?1 or lower(descricao) like ?1", sortObj, "%" + q.toLowerCase() + "%");
        }

        List<Raca> racas = query.page(effectivePage, size).list();

        var response = new SearchRacaResponse();
        response.Racas = racas;
        response.TotalRacas = (int) query.count();
        response.TotalPages = query.pageCount();
        response.HasMore = effectivePage < query.pageCount() - 1;
        // MUDANÇA V2: Atualiza NextPage para /v2
        response.NextPage = response.HasMore ? "http://localhost:8080/v2/racas/search?q="+(q != null ? q : "")+"&page="+(effectivePage + 1) + (size > 0 ? "&size="+size : "") : "";

        return Response.ok(response).build();
    }

    @POST
    @Operation(
            summary = "Adiciona um registro à lista de raças (insert) - V2",
            description = "Adiciona um item à lista de raças por meio de POST e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Raca.class)
            )
    )
    @APIResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Raca.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Bad Request",
            content = @Content(
                    mediaType = "text/plain",
                    schema = @Schema(implementation = String.class))
    )
    @APIResponse(
            responseCode = "200",
            description = "Requisição idempotente repetida. Retorna a resposta original.",
            headers = @Header(name = "X-Idempotency-Status", description = "IDEMPOTENT_REPLAY"),
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Raca.class)
            )
    )
    @Idempotent(expireAfter = 7200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.75, delay = 10000)
    @Retry(maxRetries = 2, delay = 500)
    @Fallback(fallbackMethod = "fallbackInsert")
    @Transactional
    public Response insert(@Valid Raca raca){
        Raca.persist(raca);
        // MUDANÇA V2: Ajusta URI de retorno para /v2
        return Response.status(Response.Status.CREATED)
                .header("Location", "/v2/racas/" + raca.id)
                .entity(raca).build();
    }

    public Response fallbackInsert(Raca raca) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("O serviço de persistência de raças está temporariamente indisponível. Tente novamente mais tarde.").build();
    }

    @DELETE
    @Operation(
            summary = "Remove um registro da lista de raças (delete) - V2",
            description = "Remove um item da lista de raças por meio de Id na URL"
    )
    @APIResponse(
            responseCode = "204",
            description = "Sem conteúdo",
            content = @Content(
                    mediaType = "text/plain",
                    schema = @Schema(implementation = String.class))
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado",
            content = @Content(
                    mediaType = "text/plain",
                    schema = @Schema(implementation = String.class))
    )
    @APIResponse(
            responseCode = "409",
            description = "Conflito - Raça possui adoções vinculadas",
            content = @Content(
                    mediaType = "text/plain",
                    schema = @Schema(implementation = String.class))
    )
    @Idempotent
    @Transactional
    @Path("{id}")
    public Response delete(@PathParam("id") long id){
        Raca entity = Raca.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long adocoesVinculadas = Adocao.count("?1 MEMBER OF racas", entity);
        if(adocoesVinculadas > 0){
            return Response.status(Response.Status.CONFLICT)
                    .entity("Não é possível deletar a raça. Existem " + adocoesVinculadas + " adoção(ões) vinculada(s).")
                    .build();
        }

        Raca.deleteById(id);
        return Response.noContent().build();
    }

    @PUT
    @Operation(
            summary = "Altera um registro da lista de raças (update) - V2",
            description = "Edita um item da lista de raças por meio de Id na URL e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Raca.class)
            )
    )
    @APIResponse(
            responseCode = "200",
            description = "Item editado com sucesso",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Raca.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado",
            content = @Content(
                    mediaType = "text/plain",
                    schema = @Schema(implementation = String.class))
    )
    @Idempotent
    @Transactional
    @Path("{id}")
    public Response update(@PathParam("id") long id, @Valid Raca newRaca){
        Raca entity = Raca.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        entity.nome = newRaca.nome;
        entity.descricao = newRaca.descricao;

        return Response.status(Response.Status.OK).entity(entity).build();
    }
}