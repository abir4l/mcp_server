package dev.abiral.mcp.mcp_server.tool;

import java.util.List;
import java.util.Map;

public record InputSchema(
        String type,
        Map<String, PropertySchema> properties,
        List<String> required,
        // false because the registry rejects arguments a tool doesn't declare
        boolean additionalProperties
) {

}
