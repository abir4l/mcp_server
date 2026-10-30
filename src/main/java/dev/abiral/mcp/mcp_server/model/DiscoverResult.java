package dev.abiral.mcp.mcp_server.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record DiscoverResult(
        String resultType,
        List<String> supportedVersions,
        Map<String, Object> capabilities,
        @JsonProperty("_meta")
        Map<String, Object> meta) {
}
