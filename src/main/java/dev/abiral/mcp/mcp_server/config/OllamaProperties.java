package dev.abiral.mcp.mcp_server.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(String baseUrl, String model, String keepAlive, boolean warmUp){
}
