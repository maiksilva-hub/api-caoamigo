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

import java.util.List;
import java.util.Set;

@Path("/cachorros")
public class CachorroResource {
    @GET
    @Operation(
            summary = "Retorna todos os cachorros (getAll)",
            description = "Retorna uma lista de cachorros por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Lista retornada com sucesso",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Cachorro.class, type = SchemaType.ARRAY)
            )
    )
    public Response getAll(){
        return Response.ok(Cachorro.listAll()).build();
    }

    @GET
    @Path("{id}")
    @Operation(
            summary = "Retorna um cachorro pela busca por ID (getById)",
            description = "Retorna um cachorro específico pela busca de ID colocado na URL no formato JSON por padrão"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Cachorro.class, type = SchemaType.ARRAY)
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
            @Parameter(description = "Id do cachorro a ser pesquisado", required = true)
            @PathParam("id") long id){
        Cachorro entity = Cachorro.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(entity).build();
    }

    @GET
    @Operation(
            summary = "Retorna os cachorros conforme o sistema de pesquisa (search)",
            description = "Retorna uma lista de cachorros filtrada conforme a pesquisa por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Cachorro.class, type = SchemaType.ARRAY)
            )
    )
    @Path("/search")
    public Response search(
            @Parameter(description = "Query de buscar por nome ou local de resgate")
            @QueryParam("q") String q,
            @Parameter(description = "Campo de ordenação da lista de retorno")
            @QueryParam("sort") @DefaultValue("id") String sort,
            @Parameter(description = "Esquema de filtragem de cachorros por ordem crescente ou decrescente")
            @QueryParam("direction") @DefaultValue("asc") String direction,
            @Parameter(description = "Define qual página será retornada na response")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Define quantos objetos serão retornados por query")
            @QueryParam("size") @DefaultValue("4") int size
    ){
        Set<String> allowed = Set.of("id", "nome", "dataDeNascimento", "localDeResgate");
        if(!allowed.contains(sort)){
            sort = "id";
        }

        Sort sortObj = Sort.by(
                sort,
                "desc".equalsIgnoreCase(direction) ? Sort.Direction.Descending : Sort.Direction.Ascending
        );

        int effectivePage = Math.max(page, 0);

        PanacheQuery<Cachorro> query;

        if (q == null || q.isBlank()) {
            query = Cachorro.findAll(sortObj);
        } else {
            query = Cachorro.find(
                    "lower(nome) like ?1 or lower(localDeResgate) like ?1", sortObj, "%" + q.toLowerCase() + "%");
        }

        List<Cachorro> cachorros = query.page(effectivePage, size).list();

        var response = new SearchCachorroResponse();
        response.Cachorros = cachorros;
        response.TotalCachorros = query.list().size();
        response.TotalPages = query.pageCount();
        response.HasMore = effectivePage < query.pageCount() - 1;
        response.NextPage = response.HasMore ? "http://localhost:8080/cachorros/search?q="+(q != null ? q : "")+"&page="+(effectivePage + 1) + (size > 0 ? "&size="+size : "") : "";

        return Response.ok(response).build();
    }

    @POST
    @Operation(
            summary = "Adiciona um registro à lista de cachorros (insert)",
            description = "Adiciona um item à lista de cachorros por meio de POST e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Cachorro.class)
            )
    )
    @APIResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Cachorro.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Bad Request",
            content = @Content(
                    mediaType = "text/plain",
                    schema = @Schema(implementation = String.class))
    )
    @Transactional
    public Response insert(@Valid Cachorro cachorro){
        Cachorro.persist(cachorro);
        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Operation(
            summary = "Remove um registro da lista de cachorros (delete)",
            description = "Remove um item da lista de cachorros por meio de Id na URL"
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
            description = "Conflito - Cachorro possui adoções vinculadas",
            content = @Content(
                    mediaType = "text/plain",
                    schema = @Schema(implementation = String.class))
    )
    @Transactional
    @Path("{id}")
    public Response delete(@PathParam("id") long id){
        Cachorro entity = Cachorro.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long adocoesVinculadas = Adocao.count("cachorro.id = ?1", id);
        if(adocoesVinculadas > 0){
            return Response.status(Response.Status.CONFLICT)
                    .entity("Não é possível deletar o cachorro. Existem " + adocoesVinculadas + " adoção(ões) vinculada(s).")
                    .build();
        }

        Cachorro.deleteById(id);
        return Response.noContent().build();
    }

    @PUT
    @Operation(
            summary = "Altera um registro da lista de cachorros (update)",
            description = "Edita um item da lista de cachorros por meio de Id na URL e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Cachorro.class)
            )
    )
    @APIResponse(
            responseCode = "200",
            description = "Item editado com sucesso",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Cachorro.class, type = SchemaType.ARRAY)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado",
            content = @Content(
                    mediaType = "text/plain",
                    schema = @Schema(implementation = String.class))
    )
    @Transactional
    @Path("{id}")
    public Response update(@PathParam("id") long id, @Valid Cachorro newCachorro){
        Cachorro entity = Cachorro.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        entity.nome = newCachorro.nome;
        entity.dataDeNascimento = newCachorro.dataDeNascimento;
        entity.localDeResgate = newCachorro.localDeResgate;

        // Atualizar ficha
        if(newCachorro.ficha != null){
            if(entity.ficha == null){
                entity.ficha = new FichaCachorro();
            }
            entity.ficha.descricaoHistoria = newCachorro.ficha.descricaoHistoria;
            entity.ficha.temperamentoPrincipal = newCachorro.ficha.temperamentoPrincipal;
            entity.ficha.habilidadesEspeciais = newCachorro.ficha.habilidadesEspeciais;
        } else {
            // Se não vier ficha no request, limpa a ficha existente
            entity.ficha = null;
        }

        return Response.status(Response.Status.OK).entity(entity).build();
    }
}