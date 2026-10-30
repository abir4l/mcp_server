package dev.abiral.mcp.mcp_server.tool.schema;

import tools.jackson.databind.JsonNode;

@FunctionalInterface
public interface ToolHandler {
    ToolOutput execute(JsonNode args) throws Exception;
}
