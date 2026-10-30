package dev.abiral.mcp.mcp_server.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.abiral.mcp.mcp_server.tool.ToolDto;

import java.util.List;
import java.util.Map;

public record ToolsListResult(
        String resultType,
        List<ToolDto> tools,
        @JsonProperty("_meta")
        Map<String, Object> meta) {
}
