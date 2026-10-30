package dev.abiral.mcp.mcp_server.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

// a response carries either result or error, never both
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RpcResponse(
        String jsonrpc,
        JsonNode id,
        Object result,
        RpcError error) {

    public static RpcResponse success(JsonNode id, Object result) {
        return new RpcResponse("2.0", id, result, null);
    }

    public static RpcResponse failure(JsonNode id, RpcError error) {
        return new RpcResponse("2.0", id, null, error);
    }
}
