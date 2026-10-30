package dev.abiral.mcp.mcp_server.tool.schema;

import java.util.List;

public record ToolSchema (
        String name,
        String description,
        List<ParamSpec> paramSpecs,
        ToolHandler handler
) {
}
