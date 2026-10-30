package dev.abiral.mcp.mcp_server.tool;

public record ToolDto(
        String name,
        String description,
        InputSchema inputSchema
) {
}

