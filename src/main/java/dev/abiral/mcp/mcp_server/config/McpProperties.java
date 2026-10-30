package dev.abiral.mcp.mcp_server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * Settings under the "mcp" prefix in application.properties.
 */
@ConfigurationProperties("mcp")
public record McpProperties(
        Server server,
        String databasePath,
        Set<String> allowedOriginHosts) {

    public record Server(
            String name,
            String version) {
    }
}
