package dev.abiral.mcp.mcp_server.model;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record RpcRequest(
        @NotNull
        String jsonrpc,
        // absent for notifications; when present must be a string or integer (never null)
        JsonNode id,
        @NotNull
        String method,
        JsonNode params) {

    public boolean isNotification() {
        return id == null;
    }

    public boolean hasValidId() {
        return id != null && (id.isString() || id.isIntegralNumber());
    }
}
