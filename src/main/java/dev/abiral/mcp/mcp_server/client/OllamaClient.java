package dev.abiral.mcp.mcp_server.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.abiral.mcp.mcp_server.config.OllamaProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Component
public class OllamaClient {

    private final OllamaProperties ollamaProperties;
    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public OllamaClient(
            OllamaProperties ollamaProperties,
            JsonMapper jsonMapper
    ) {
        this.ollamaProperties = ollamaProperties;
        this.restClient = RestClient.builder().baseUrl(ollamaProperties.baseUrl()).build();
        this.jsonMapper = jsonMapper;
    }

    /**
     * Sends the conversation along with the tools the model may call. The returned assistant
     * message either has content (a final answer) or tool_calls for us to run and send back.
     */
    public float[] embed(String query) {

        EmbedResponse node = restClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbedRequest(ollamaProperties.model(), List.of(query)))
                .retrieve().body(EmbedResponse.class);


        return node.embeddings().get(0);
    }

    /**
     * role is user, assistant or tool. An assistant message may carry tool_calls instead of content;
     * a tool message carries the tool's output in content and names the tool in tool_name.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(String role, String content,
                          @JsonProperty("tool_calls") List<ToolCall> toolCalls,
                          @JsonProperty("tool_name") String toolName) {

        public Message(String role, String content) {
            this(role, content, null, null);
        }

        public static Message toolResult(String toolName, String content) {
            return new Message("tool", content, null, toolName);
        }

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    public record ToolCall(FunctionCall function) {
    }
    /** Ollama sends arguments as a JSON object, not a string like OpenAI does. */
    public record FunctionCall(String name, JsonNode arguments) {
    }

    /** A tool offered to the model; parameters is a JSON Schema object. */
    public record ToolDefinition(String type, FunctionDefinition function) {

        public static ToolDefinition function(String name, String description, JsonNode parameters) {
            return new ToolDefinition("function", new FunctionDefinition(name, description, parameters));
        }
    }

    public record FunctionDefinition(String name, String description, JsonNode parameters) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EmbedRequest(String model, List<String> input) {
    }

    record EmbedResponse(List<float[]> embeddings) {}

}
