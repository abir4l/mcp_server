package dev.abiral.mcp.mcp_server.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record CallToolResult(
        String resultType,
        List<TextContent> content,
        boolean isError,
        @JsonProperty("_meta")
        Map<String, Object> meta) {

    public static CallToolResult success(String text, Map<String, Object> meta) {
        return new CallToolResult("complete", List.of(TextContent.of(text)), false, meta);
    }

    // a tool execution error: still a successful JSON-RPC response, the model sees the message
    public static CallToolResult error(String text, Map<String, Object> meta) {
        return new CallToolResult("complete", List.of(TextContent.of(text)), true, meta);
    }
}
