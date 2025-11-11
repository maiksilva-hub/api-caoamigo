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

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// MUDANÇA 1: Versão V2
@Path("/v2/adocoes")
@Consumes("application/json")
@Produces("application/json")
public class AdocaoV2Resource { // MUDANÇA 2: Nome da classe

    // MUDANÇA V2: Ordena por data de solicitação (mais recente primeiro)
    @GET
    @Operation(
            summary = "Retorna todas as adoções (getAll) - V2",
            description = "V2: Retorna uma lista de adoções ordenada por data de solicitação (mais recente) por padrão."
    )
    @APIResponse(
            responseCode = "200",
            description = "Lista retornada com sucesso",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Adocao.class, type = SchemaType.ARRAY)
            )
    )
    @Timeout(3000)
    @Fallback(fallbackMethod = "fallbackListaVazia")
    public Response getAll(){
        // ALTERAÇÃO V2: Ordenação por data (mais recente)
        return Response.ok(Adocao.listAll(Sort.by("dataSolicitacao", Sort.Direction.Descending))).build();
    }

    public Response fallbackListaVazia() {
        return Response.ok(Collections.emptyList()).header("X-Fallback", "true").build();
    }

    @GET
    @Path("{id}")
    @Operation(
            summary = "Retorna uma adoção pela busca por ID (getById) - V2",
            description = "Retorna uma adoção específica pela busca de ID colocado na URL no formato JSON por padrão"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Adocao.class) // Removido ARRAY
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
            @Parameter(description = "Id da adoção a ser pesquisada", required = true)
            @PathParam("id") long id){
        Adocao entity = Adocao.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(entity).build();
    }

    @GET
    @Operation(
            summary = "Retorna as adoções conforme o sistema de pesquisa (search) - V2",
            description = "V2: Retorna uma lista de adoções com ordenação padrão por data de solicitação (mais recente)."
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SearchAdocaoResponse.class) // Removido ARRAY
            )
    )
    @Path("/search")
    public Response search(
            @Parameter(description = "Query de busca por status ou data de solicitação")
            @QueryParam("q") String q,
            @Parameter(description = "Campo de ordenação da lista de retorno")
            @QueryParam("sort") @DefaultValue("dataSolicitacao") String sort, // MUDANÇA V2: Padrão dataSolicitacao
            @Parameter(description = "Esquema de filtragem de adoções por ordem crescente ou decrescente")
            @QueryParam("direction") @DefaultValue("desc") String direction, // MUDANÇA V2: Padrão decrescente
            @Parameter(description = "Define qual página será retornada na response")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Define quantos objetos serão retornados por query")
            @QueryParam("size") @DefaultValue("4") int size
    ){
        Set<String> allowed = Set.of("id", "dataSolicitacao", "justificativa", "status");
        if(!allowed.contains(sort)){
            sort = "dataSolicitacao"; // MUDANÇA V2: Usar o novo padrão
        }

        Sort sortObj = Sort.by(
                sort,
                "desc".equalsIgnoreCase(direction) ? Sort.Direction.Descending : Sort.Direction.Ascending
        );

        int effectivePage = Math.max(page, 0);

        PanacheQuery<Adocao> query;

        if (q == null || q.isBlank()) {
            query = Adocao.findAll(sortObj);
        } else {
            try {
                LocalDate data = LocalDate.parse(q);

                query = Adocao.find(
                        "dataSolicitacao = ?1",
                        sortObj,
                        data
                );

            } catch (Exception e) {
                query = Adocao.find(
                        "lower(status) like ?1 or lower(justificativa) like ?1",
                        sortObj,
                        "%" + q.toLowerCase() + "%"
                );
            }
        }

        List<Adocao> adocoes = query.page(effectivePage, size).list();

        var response = new SearchAdocaoResponse();
        response.Adocoes = adocoes;
        response.TotalAdocoes = (int) query.count();
        response.TotalPages = query.pageCount();
        response.HasMore = effectivePage < query.pageCount() - 1;
        // MUDANÇA V2: Atualiza NextPage para /v2
        response.NextPage = response.HasMore ? "http://localhost:8080/v2/adocoes/search?q="+(q != null ? q : "")+"&page="+(effectivePage + 1) + (size > 0 ? "&size="+size : "") : "";

        return Response.ok(response).build();
    }

    @POST
    @Operation(
            summary = "Adiciona um registro à lista de adoções (insert) - V2",
            description = "Adiciona um item à lista de adoções por meio de POST e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Adocao.class)
            )
    )
    @APIResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Adocao.class))
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
                    schema = @Schema(implementation = Adocao.class)
            )
    )
    @Idempotent(expireAfter = 7200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.75, delay = 10000)
    @Retry(maxRetries = 2, delay = 500)
    @Fallback(fallbackMethod = "fallbackInsert")
    @Transactional
    public Response insert(@Valid Adocao adocao){

        if(adocao.cachorro != null && adocao.cachorro.id != null){
            Cachorro a = Cachorro.findById(adocao.cachorro.id);
            if(a == null){
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Cachorro com id " + adocao.cachorro.id + " não existe").build();
            }
            adocao.cachorro = a;
        } else {
            adocao.cachorro = null;
        }

        if(adocao.racas != null && !adocao.racas.isEmpty()){
            Set<Raca> resolved = new HashSet<>();
            for(Raca r : adocao.racas){
                if(r == null || r.id == null){
                    continue;
                }
                Raca fetched = Raca.findById(r.id);
                if(fetched == null){
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity("Raça com id " + r.id + " não existe").build();
                }
                resolved.add(fetched);
            }
            adocao.racas = resolved;
        } else {
            adocao.racas = new HashSet<>();
        }

        Adocao.persist(adocao);
        // MUDANÇA V2: Ajusta URI de retorno para /v2
        return Response.status(Response.Status.CREATED)
                .header("Location", "/v2/adocoes/" + adocao.id)
                .entity(adocao).build();
    }

    public Response fallbackInsert(Adocao adocao) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("O serviço de persistência está temporariamente indisponível. Tente novamente mais tarde.").build();
    }

    @DELETE
    @Operation(
            summary = "Remove um registro da lista de adoções (delete) - V2",
            description = "Remove um item da lista de adoções por meio de Id na URL"
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
    @Idempotent
    @Transactional
    @Path("{id}")
    public Response delete(@PathParam("id") long id){
        Adocao entity = Adocao.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        entity.racas.clear();
        entity.persist();

        Adocao.deleteById(id);
        return Response.noContent().build();
    }

    @PUT
    @Operation(
            summary = "Altera um registro da lista de adoções (update) - V2",
            description = "Edita um item da lista de adoções por meio de Id na URL e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Adocao.class)
            )
    )
    @APIResponse(
            responseCode = "200",
            description = "Item editado com sucesso",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Adocao.class)
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
    public Response update(@PathParam("id") long id, @Valid Adocao newAdocao){
        Adocao entity = Adocao.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        entity.dataSolicitacao = newAdocao.dataSolicitacao;
        entity.justificativa = newAdocao.justificativa;
        entity.status = newAdocao.status;

        if(newAdocao.cachorro != null && newAdocao.cachorro.id != null){
            Cachorro a = Cachorro.findById(newAdocao.cachorro.id);
            if(a == null){
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Cachorro com id " + newAdocao.cachorro.id + " não existe").build();
            }
            entity.cachorro = a;
        } else {
            entity.cachorro = null;
        }

        if(newAdocao.racas != null){
            Set<Raca> resolved = new HashSet<>();
            for(Raca r : newAdocao.racas){
                if(r == null || r.id == null) continue;
                Raca fetched = Raca.findById(r.id);
                if(fetched == null){
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity("Raça com id " + r.id + " não existe").build();
                }
                resolved.add(fetched);
            }
            entity.racas = resolved;
        } else {
            entity.racas = new HashSet<>();
        }

        return Response.status(Response.Status.OK).entity(entity).build();
    }
}