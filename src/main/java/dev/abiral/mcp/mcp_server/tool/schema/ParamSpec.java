package dev.abiral.mcp.mcp_server.tool.schema;

public record ParamSpec(
        String name,
        ParamType paramType,
        String description,
        boolean required
) {
}
