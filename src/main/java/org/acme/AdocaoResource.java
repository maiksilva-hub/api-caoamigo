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

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.net.URI;
import jakarta.ws.rs.core.UriBuilder;

@Path("/v1/adocoes")
@Consumes("application/json")
@Produces("application/json")
public class AdocaoResource {

    @GET
    @Operation(summary = "Retorna todas as adoções")
    @APIResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Adocao.class, type = SchemaType.ARRAY)))
    @Timeout(3000)
    public Response getAll(){
        return Response.ok(Adocao.listAll()).build();
    }

    @GET
    @Path("{id}")
    @Operation(summary = "Retorna uma adoção por ID")
    @APIResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Adocao.class)))
    @APIResponse(responseCode = "404", description = "Não encontrado")
    public Response getById(@PathParam("id") long id){
        Adocao entity = Adocao.findById(id);
        if(entity == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(entity).build();
    }

    @GET
    @Path("/search")
    @Operation(summary = "Pesquisa adoções")
    @APIResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Adocao.class, type = SchemaType.ARRAY)))
    public Response search(
            @QueryParam("q") String q,
            @QueryParam("sort") @DefaultValue("id") String sort,
            @QueryParam("direction") @DefaultValue("asc") String direction,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("4") int size
    ){
        Sort sortObj = Sort.by(sort, "desc".equalsIgnoreCase(direction) ? Sort.Direction.Descending : Sort.Direction.Ascending);
        PanacheQuery<Adocao> query;
        if (q == null || q.isBlank()) {
            query = Adocao.findAll(sortObj);
        } else {
            try {
                query = Adocao.find("dataSolicitacao = ?1", sortObj, LocalDate.parse(q));
            } catch (Exception e) {
                query = Adocao.find("lower(status) like ?1 or lower(justificativa) like ?1", sortObj, "%" + q.toLowerCase() + "%");
            }
        }
        List<Adocao> adocoes = query.page(Math.max(page, 0), size).list();

        var response = new SearchAdocaoResponse();
        response.Adocoes = adocoes;
        response.TotalAdocoes = (int) query.count();
        response.TotalPages = query.pageCount();
        response.HasMore = page < query.pageCount() - 1;
        response.NextPage = response.HasMore ? UriBuilder.fromPath("/v1/adocoes/search").queryParam("q", q).queryParam("page", page + 1).queryParam("size", size).build().toString() : "";

        return Response.ok(response).build();
    }

    @POST
    @Operation(summary = "Cria uma adoção", description = "Requer chave de idempotência")
    @Parameter(name = "X-Idempotency-Key", in = ParameterIn.HEADER, required = true, description = "Chave única para garantir idempotência")
    @APIResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = Adocao.class)))
    @APIResponse(responseCode = "200", description = "Replay", headers = @Header(name = "X-Idempotency-Status", description = "IDEMPOTENT_REPLAY"))
    @Idempotent(expireAfter = 7200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.75, delay = 10000)
    @Retry(maxRetries = 2, delay = 500)
    @Transactional
    public Response insert(@Valid Adocao adocao){
        if(adocao.cachorro != null && adocao.cachorro.id != null){
            Cachorro a = Cachorro.findById(adocao.cachorro.id);
            if(a == null) return Response.status(Response.Status.BAD_REQUEST).entity("Cachorro inexistente").build();
            adocao.cachorro = a;
        }
        if(adocao.racas != null){
            Set<Raca> resolved = new HashSet<>();
            for(Raca r : adocao.racas){
                if(r.id == null) continue;
                Raca fetched = Raca.findById(r.id);
                if(fetched == null) return Response.status(Response.Status.BAD_REQUEST).entity("Raça inexistente").build();
                resolved.add(fetched);
            }
            adocao.racas = resolved;
        }
        Adocao.persist(adocao);
        URI location = UriBuilder.fromPath("/v1/adocoes/{id}").build(adocao.id);
        return Response.created(location).entity(adocao).build();
    }

    @DELETE
    @Path("{id}")
    @Operation(summary = "Deleta uma adoção", description = "Requer chave de idempotência")
    @Parameter(name = "X-Idempotency-Key", in = ParameterIn.HEADER, required = true, description = "Chave única para garantir idempotência")
    @Idempotent
    @Transactional
    public Response delete(@PathParam("id") long id){
        Adocao entity = Adocao.findById(id);
        if(entity == null) return Response.status(Response.Status.NOT_FOUND).build();
        entity.racas.clear();
        entity.persist();
        Adocao.deleteById(id);
        return Response.noContent().build();
    }

    @PUT
    @Path("{id}")
    @Operation(summary = "Atualiza uma adoção", description = "Requer chave de idempotência")
    @Parameter(name = "X-Idempotency-Key", in = ParameterIn.HEADER, required = true, description = "Chave única para garantir idempotência")
    @Idempotent
    @Transactional
    public Response update(@PathParam("id") long id, @Valid Adocao newAdocao){
        Adocao entity = Adocao.findById(id);
        if(entity == null) return Response.status(Response.Status.NOT_FOUND).build();

        entity.dataSolicitacao = newAdocao.dataSolicitacao;
        entity.justificativa = newAdocao.justificativa;
        entity.status = newAdocao.status;

        if(newAdocao.cachorro != null && newAdocao.cachorro.id != null){
            Cachorro a = Cachorro.findById(newAdocao.cachorro.id);
            if(a == null) return Response.status(Response.Status.BAD_REQUEST).entity("Cachorro inexistente").build();
            entity.cachorro = a;
        } else {
            entity.cachorro = null;
        }

        if(newAdocao.racas != null){
            Set<Raca> resolved = new HashSet<>();
            for(Raca r : newAdocao.racas){
                if(r.id == null) continue;
                Raca fetched = Raca.findById(r.id);
                if(fetched == null) return Response.status(Response.Status.BAD_REQUEST).entity("Raça inexistente").build();
                resolved.add(fetched);
            }
            entity.racas = resolved;
        } else {
            entity.racas = new HashSet<>();
        }

        return Response.status(Response.Status.OK).entity(entity).build();
    }
}