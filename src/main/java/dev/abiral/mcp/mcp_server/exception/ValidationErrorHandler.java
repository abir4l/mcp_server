package dev.abiral.mcp.mcp_server.exception;

import dev.abiral.mcp.mcp_server.model.RpcError;
import dev.abiral.mcp.mcp_server.model.RpcRequest;
import dev.abiral.mcp.mcp_server.model.RpcResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Turns failures that happen before the controller runs (or unexpected crashes) into JSON-RPC errors.
 */
@RestControllerAdvice
public class ValidationErrorHandler {

    // a field annotated @NotNull on RpcRequest was missing
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RpcResponse> handleValidationErrors(MethodArgumentNotValidException ex){
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> "Field: %s, message: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage()))
                .sorted()
                .collect(Collectors.joining("; "));

        // the body was parsed, so the id may still be readable
        var id = ex.getBindingResult().getTarget() instanceof RpcRequest request && request.hasValidId()
                ? request.id()
                : null;

        return new ResponseEntity<>(
                RpcResponse.failure(id, new RpcError(RpcError.INVALID_REQUEST, message, null)),
                HttpStatus.BAD_REQUEST);
    }

    // the body wasn't valid JSON (or wasn't a JSON object)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RpcResponse> handleUnreadableBody(HttpMessageNotReadableException ex){
        return new ResponseEntity<>(
                RpcResponse.failure(null, new RpcError(RpcError.PARSE_ERROR, "Parse error", null)),
                HttpStatus.BAD_REQUEST);
    }
}
