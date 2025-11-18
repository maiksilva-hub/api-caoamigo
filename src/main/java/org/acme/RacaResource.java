package org.acme;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.headers.Header;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import org.acme.idempotency.Idempotent;

import java.util.List;
import java.net.URI;
import jakarta.ws.rs.core.UriBuilder;

@Path("/v1/racas")
@Consumes("application/json")
@Produces("application/json")
public class RacaResource {

    @GET
    @Operation(summary = "Retorna todas as raças")
    @APIResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Raca.class, type = SchemaType.ARRAY)))
    @Timeout(3000)
    public Response getAll(){
        return Response.ok(Raca.listAll()).build();
    }

    @GET
    @Path("{id}")
    @Operation(summary = "Retorna uma raça por ID")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Raca.class)))
    @APIResponse(responseCode = "404", description = "Não encontrado")
    public Response getById(@PathParam("id") long id){
        Raca entity = Raca.findById(id);
        if(entity == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(entity).build();
    }

    @GET
    @Path("/search")
    @Operation(summary = "Pesquisa raças")
    @APIResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Raca.class, type = SchemaType.ARRAY)))
    public Response search(
            @QueryParam("q") String q,
            @QueryParam("sort") @DefaultValue("id") String sort,
            @QueryParam("direction") @DefaultValue("asc") String direction,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("4") int size
    ){
        Sort sortObj = Sort.by(sort, "desc".equalsIgnoreCase(direction) ? Sort.Direction.Descending : Sort.Direction.Ascending);
        PanacheQuery<Raca> query;
        if (q == null || q.isBlank()) {
            query = Raca.findAll(sortObj);
        } else {
            query = Raca.find("lower(nome) like ?1 or lower(descricao) like ?1", sortObj, "%" + q.toLowerCase() + "%");
        }
        List<Raca> racas = query.page(Math.max(page, 0), size).list();

        var response = new SearchRacaResponse();
        response.Racas = racas;
        response.TotalRacas = (int) query.count();
        response.TotalPages = query.pageCount();
        response.HasMore = page < query.pageCount() - 1;
        response.NextPage = response.HasMore ? UriBuilder.fromPath("/v1/racas/search").queryParam("q", q).queryParam("page", page + 1).queryParam("size", size).build().toString() : "";

        return Response.ok(response).build();
    }

    @POST
    @Operation(summary = "Cria uma raça", description = "Requer chave de idempotência")
    @Parameter(name = "X-Idempotency-Key", in = ParameterIn.HEADER, required = true, description = "Chave única para garantir idempotência")
    @APIResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = Raca.class)))
    @APIResponse(responseCode = "200", description = "Replay", headers = @Header(name = "X-Idempotency-Status", description = "IDEMPOTENT_REPLAY"))
    @Idempotent(expireAfter = 7200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.75, delay = 10000)
    @Retry(maxRetries = 2, delay = 500)
    @Transactional
    public Response insert(@Valid Raca raca){
        Raca.persist(raca);
        URI location = UriBuilder.fromPath("/v1/racas/{id}").build(raca.id);
        return Response.created(location).entity(raca).build();
    }

    @DELETE
    @Path("{id}")
    @Operation(summary = "Deleta uma raça", description = "Requer chave de idempotência")
    @Parameter(name = "X-Idempotency-Key", in = ParameterIn.HEADER, required = true, description = "Chave única para garantir idempotência")
    @Idempotent
    @Transactional
    public Response delete(@PathParam("id") long id){
        Raca entity = Raca.findById(id);
        if(entity == null) return Response.status(Response.Status.NOT_FOUND).build();

        if(Adocao.count("?1 MEMBER OF racas", entity) > 0){
            return Response.status(Response.Status.CONFLICT).entity("Raça em uso").build();
        }

        Raca.deleteById(id);
        return Response.noContent().build();
    }

    @PUT
    @Path("{id}")
    @Operation(summary = "Atualiza uma raça", description = "Requer chave de idempotência")
    @Parameter(name = "X-Idempotency-Key", in = ParameterIn.HEADER, required = true, description = "Chave única para garantir idempotência")
    @Idempotent
    @Transactional
    public Response update(@PathParam("id") long id, @Valid Raca newRaca){
        Raca entity = Raca.findById(id);
        if(entity == null) return Response.status(Response.Status.NOT_FOUND).build();
        entity.nome = newRaca.nome;
        entity.descricao = newRaca.descricao;
        return Response.status(Response.Status.OK).entity(entity).build();
    }
}