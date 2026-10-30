package dev.abiral.mcp.mcp_server.model;

/**
 * Streamable HTTP headers that mirror fields of the request body.
 * Any of them may be null when the client didn't send it.
 */
public record McpHeaders(
        String protocolVersion,
        String method,
        String name) {
}
