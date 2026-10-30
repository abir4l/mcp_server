package dev.abiral.mcp.mcp_server.exception;

import dev.abiral.mcp.mcp_server.model.RpcError;
import org.springframework.http.HttpStatus;

/**
 * A protocol-level failure that is answered with a JSON-RPC error object.
 * Carries the HTTP status the Streamable HTTP transport requires for it.
 */
public class RpcException extends RuntimeException{

    private final int code;
    private final Object data;
    private final HttpStatus status;

    public RpcException(int code, String message, Object data, HttpStatus status) {
        super(message);
        this.code = code;
        this.data = data;
        this.status = status;
    }

    public RpcException(int code, String message, HttpStatus status) {
        this(code, message, null, status);
    }

    public static RpcException invalidRequest(String message) {
        return new RpcException(RpcError.INVALID_REQUEST, message, HttpStatus.BAD_REQUEST);
    }

    public static RpcException invalidParams(String message, HttpStatus status) {
        return new RpcException(RpcError.INVALID_PARAMS, message, status);
    }

    public static RpcException headerMismatch(String message) {
        return new RpcException(RpcError.HEADER_MISMATCH, message, HttpStatus.BAD_REQUEST);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public RpcError toError() {
        return new RpcError(code, getMessage(), data);
    }
}
