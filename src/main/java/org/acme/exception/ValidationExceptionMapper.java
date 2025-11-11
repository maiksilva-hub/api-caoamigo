package org.acme.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapeador de exceções para tratar erros de Bean Validation (@Size, @NotBlank, etc.)
 * Retorna um status 400 Bad Request com detalhes dos erros.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        // Coleta todas as mensagens de erro de validação
        List<String> errors = exception.getConstraintViolations().stream()
                .map(this::formatViolation)
                .collect(Collectors.toList());

        // Cria um objeto de erro com status 400
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Erro de Validação: Dados de entrada inválidos.", errors))
                .build();
    }

    /**
     * Formata a mensagem de erro para incluir o campo e a mensagem de erro.
     */
    private String formatViolation(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath().toString();
        String fieldName = field.substring(field.lastIndexOf('.') + 1);

        return String.format("Campo '%s': %s", fieldName, violation.getMessage());
    }

    public static class ErrorResponse {
        public String title;
        public List<String> details;

        public ErrorResponse(String title, List<String> details) {
            this.title = title;
            this.details = details;
        }

        // Construtor sem argumentos para serialização do Jackson
        public ErrorResponse() {}
    }
}