package dev.abiral.mcp.mcp_server.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.abiral.mcp.mcp_server.config.OllamaProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
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
                .body(new EmbedRequest(ollamaProperties.model(), List.of("search_query: " + query)))
                .retrieve().body(EmbedResponse.class);


        return node.embeddings().get(0);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EmbedRequest(String model, List<String> input) {
    }

    record EmbedResponse(List<float[]> embeddings) {}

}
