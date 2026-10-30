package dev.abiral.mcp.mcp_server.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RpcError(
        int code,
        String message,
        Object data) {

    // standard JSON-RPC 2.0 codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;

    // codes defined by the MCP spec (2026-07-28)
    public static final int HEADER_MISMATCH = -32020;
    public static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;
}
