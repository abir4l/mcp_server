package dev.abiral.mcp.mcp_server.tool.schema;

import java.util.Map;

/**
 * What a tool handler returns: the text the model reads, plus optional _meta for the client.
 */
public record ToolOutput(String text, Map<String, Object> meta) {

    public static ToolOutput of(String text) {
        return new ToolOutput(text, Map.of());
    }
}
