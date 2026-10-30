package dev.abiral.mcp.mcp_server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the whole server on a random port and talks to /mcp over real HTTP,
 * the way an MCP client (protocol 2026-07-28, Streamable HTTP) would.
 *
 * The tools work on a temporary database file, emptied before each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpConnectionIntegrationTest {

    private static final Path DATABASE = createTempDatabase();
    private static final String VERSION = "2026-07-28";

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private static Path createTempDatabase() {
        try {
            Path path = Files.createTempFile("mcp-db", null);
            path.toFile().deleteOnExit();
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void databasePath(DynamicPropertyRegistry registry) {
        registry.add("mcp.database-path", DATABASE::toString);
    }

    // also recreates the file after tests that delete it
    @BeforeEach
    void resetDatabase() throws IOException {
        Files.writeString(DATABASE, "");
    }

    @Nested
    class Discover {

        @Test
        void returnsSupportedVersionsCapabilitiesAndServerInfo() throws Exception {
            var json = success(send(request("server/discover", params())));

            var result = json.get("result");
            assertEquals("complete", result.get("resultType").asString());
            assertEquals(json("[\"2026-07-28\"]"), result.get("supportedVersions"));
            assertEquals(json("{\"tools\":{}}"), result.get("capabilities"));
            assertEquals("mcp_server", result.get("_meta").get("io.modelcontextprotocol/serverInfo").get("name").asString());
        }
    }

    @Nested
    class ToolsList {

        @Test
        void listsAllToolsWithTheirInputSchemas() throws Exception {
            var result = success(send(request("tools/list", params()))).get("result");

            assertEquals("complete", result.get("resultType").asString());
            assertEquals(json("""
                    [
                      {
                        "name": "read",
                        "description": "Read all file contents",
                        "inputSchema": {"type": "object", "properties": {}, "required": [], "additionalProperties": false}
                      },
                      {
                        "name": "write",
                        "description": "Write all contents to file",
                        "inputSchema": {
                          "type": "object",
                          "properties": {"content": {"type": "string", "description": "Content that gets written"}},
                          "required": ["content"],
                          "additionalProperties": false
                        }
                      },
                      {
                        "name": "append",
                        "description": "Append content to the end of the file",
                        "inputSchema": {
                          "type": "object",
                          "properties": {"content": {"type": "string", "description": "Content that gets appended"}},
                          "required": ["content"],
                          "additionalProperties": false
                        }
                      },
                      {
                        "name": "update",
                        "description": "Replace every occurrence of a piece of text in the file",
                        "inputSchema": {
                          "type": "object",
                          "properties": {
                            "search": {"type": "string", "description": "Exact text to find"},
                            "replace": {"type": "string", "description": "Text that replaces each match"}
                          },
                          "required": ["search", "replace"],
                          "additionalProperties": false
                        }
                      },
                      {
                        "name": "clear",
                        "description": "Delete all contents of the file",
                        "inputSchema": {"type": "object", "properties": {}, "required": [], "additionalProperties": false}
                      }
                    ]"""), result.get("tools"));
        }

        @Test
        void cursorIsRejectedBecauseThereIsOnlyOnePage() throws Exception {
            var json = error(send(request("tools/list", params().put("cursor", "abc"))), 200);

            assertErrorCode(json, -32602, "Invalid cursor");
        }
    }

    @Nested
    class ToolsCall {

        @Test
        void readReturnsFileContents() throws Exception {
            Files.writeString(DATABASE, "first line\nsecond line\n");

            var result = success(send(callTool("read", null))).get("result");

            assertToolResult(result, false, "first line\nsecond line\n");
            assertFalse(result.get("_meta").get("dev.abiral/empty").asBoolean());
        }

        @Test
        void readOnEmptyFileSaysSo() throws Exception {
            Files.writeString(DATABASE, "");

            var result = success(send(callTool("read", null))).get("result");

            assertToolResult(result, false, "The file is empty. No data has been written yet.");
            assertTrue(result.get("_meta").get("dev.abiral/empty").asBoolean());
            assertEquals("mcp_server", result.get("_meta").get("io.modelcontextprotocol/serverInfo").get("name").asString());
        }

        @Test
        void readOnWhitespaceOnlyFileCountsAsEmpty() throws Exception {
            Files.writeString(DATABASE, System.lineSeparator());

            var result = success(send(callTool("read", null))).get("result");

            assertTrue(result.get("_meta").get("dev.abiral/empty").asBoolean());
        }

        @Test
        void writeReplacesFileContents() throws Exception {
            Files.writeString(DATABASE, "old content\n");

            var result = success(send(callTool("write", "{\"content\":\"new content\"}"))).get("result");

            assertToolResult(result, false, "Success");
            assertEquals("new content" + System.lineSeparator(), Files.readString(DATABASE));
        }

        @Test
        void writeThenReadRoundTrip() throws Exception {
            send(callTool("write", "{\"content\":\"round trip\"}"));

            var result = success(send(callTool("read", "{}"))).get("result");

            assertToolResult(result, false, "round trip" + System.lineSeparator());
        }

        @Test
        void appendAddsToTheEndOfTheFile() throws Exception {
            Files.writeString(DATABASE, "first\n");

            var result = success(send(callTool("append", "{\"content\":\"second\"}"))).get("result");

            assertToolResult(result, false, "Success");
            assertEquals("first\nsecond\n", Files.readString(DATABASE));
        }

        @Test
        void updateReplacesEveryOccurrenceAndReportsTheCount() throws Exception {
            Files.writeString(DATABASE, "cat and cat\ndog\n");

            var result = success(send(callTool("update", "{\"search\":\"cat\",\"replace\":\"fox\"}"))).get("result");

            assertToolResult(result, false, "Replaced 2 occurrence(s)");
            assertEquals("fox and fox\ndog\n", Files.readString(DATABASE));
        }

        @Test
        void updateTreatsSearchAsLiteralText() throws Exception {
            Files.writeString(DATABASE, "a.b axb\n");

            success(send(callTool("update", "{\"search\":\"a.b\",\"replace\":\"c\"}")));

            assertEquals("c axb\n", Files.readString(DATABASE));
        }

        @Test
        void updateWithTextNotInTheFileIsAToolErrorAndLeavesTheFileAlone() throws Exception {
            Files.writeString(DATABASE, "keep me\n");

            var result = success(send(callTool("update", "{\"search\":\"missing\",\"replace\":\"x\"}"))).get("result");

            assertToolResult(result, true, "Tool execution failed: Text 'missing' was not found in the file");
            assertEquals("keep me\n", Files.readString(DATABASE));
        }

        @Test
        void updateWithEmptySearchIsAToolError() throws Exception {
            Files.writeString(DATABASE, "keep me\n");

            var result = success(send(callTool("update", "{\"search\":\"\",\"replace\":\"x\"}"))).get("result");

            assertToolResult(result, true, "Tool execution failed: search must not be empty");
            assertEquals("keep me\n", Files.readString(DATABASE));
        }

        @Test
        void clearEmptiesTheFile() throws Exception {
            Files.writeString(DATABASE, "something\n");

            var result = success(send(callTool("clear", null))).get("result");

            assertToolResult(result, false, "File cleared");
            assertEquals("", Files.readString(DATABASE));
        }

        @Test
        void unknownToolIsAProtocolError() throws Exception {
            var json = error(send(callTool("delete_everything", null)), 200);

            assertErrorCode(json, -32602, "Unknown tool: delete_everything");
        }

        @Test
        void toolNamesAreCaseSensitive() throws Exception {
            var json = error(send(callTool("READ", null)), 200);

            assertErrorCode(json, -32602, "Unknown tool: READ");
        }

        @Test
        void missingToolNameIsAProtocolError() throws Exception {
            var json = error(send(request("tools/call", params())), 200);

            assertErrorCode(json, -32602, "params.name must be a string");
        }

        @ParameterizedTest(name = "arguments = {0}")
        @ValueSource(strings = {"\"just a string\"", "null", "[]"})
        void argumentsThatAreNotAnObjectAreAProtocolError(String arguments) throws Exception {
            var json = error(send(callTool("write", arguments)), 200);

            assertErrorCode(json, -32602, "params.arguments must be an object");
        }
    }

    // bad arguments come back as a normal result with isError=true, so the model can fix and retry
    @Nested
    class ToolArgumentErrors {

        @ParameterizedTest(name = "arguments = {0}")
        @ValueSource(strings = {"<omitted>", "{}", "{\"content\":null}"})
        void missingRequiredArgument(String arguments) throws Exception {
            var result = success(send(callTool("write", arguments.equals("<omitted>") ? null : arguments))).get("result");

            assertToolResult(result, true, "Param (content) is required");
        }

        @ParameterizedTest(name = "content = {0}")
        @ValueSource(strings = {"5", "true", "[\"a\"]", "{\"text\":\"a\"}"})
        void wrongArgumentType(String content) throws Exception {
            var result = success(send(callTool("write", "{\"content\":%s}".formatted(content)))).get("result");

            assertToolResult(result, true, "Param (content) type doesn't match");
        }

        @ParameterizedTest(name = "{0} with {1}")
        @CsvSource(delimiter = '|', value = {
                "write | {\"content\":\"x\",\"extra\":1} | extra",
                "read  | {\"verbose\":true}             | verbose"
        })
        void undeclaredArgument(String tool, String arguments, String paramName) throws Exception {
            var result = success(send(callTool(tool, arguments))).get("result");

            assertToolResult(result, true, "Param (%s) is not accepted by this tool".formatted(paramName));
        }

        @Test
        void failedValidationDoesNotTouchTheFile() throws Exception {
            Files.writeString(DATABASE, "keep me\n");

            send(callTool("write", "{\"content\":5}"));
            send(callTool("write", "{\"content\":\"x\",\"extra\":1}"));
            send(callTool("write", null));

            assertEquals("keep me\n", Files.readString(DATABASE));
        }
    }

    // the file is deleted here and recreated by resetDatabase before the next test
    @Nested
    class MissingDatabase {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "read   | {}",
                "write  | {\"content\":\"x\"}",
                "append | {\"content\":\"x\"}",
                "update | {\"search\":\"a\",\"replace\":\"b\"}",
                "clear  | {}"
        })
        void everyToolReportsTheMissingFile(String tool, String arguments) throws Exception {
            Files.delete(DATABASE);

            var result = success(send(callTool(tool, arguments))).get("result");

            assertToolResult(result, true, "Database file not found at %s. Start the server from the repo root or create the file."
                    .formatted(DATABASE.toAbsolutePath()));
            // no tool should quietly recreate the file
            assertFalse(Files.exists(DATABASE));
        }
    }

    @Nested
    class JsonRpcEnvelope {

        @Test
        void stringIdIsEchoed() throws Exception {
            var json = success(send(request("server/discover", params()).put("id", "abc-123")));

            assertEquals(json("\"abc-123\""), json.get("id"));
        }

        @Test
        void integerIdIsEchoed() throws Exception {
            var json = success(send(request("server/discover", params()).put("id", 42)));

            assertEquals(json("42"), json.get("id"));
        }

        @ParameterizedTest(name = "missing {0}")
        @ValueSource(strings = {"jsonrpc", "method"})
        void missingRequiredFieldIsInvalidRequest(String field) throws Exception {
            var body = request("server/discover", params());
            body.remove(field);

            var json = error(send(body), 400);

            assertErrorCode(json, -32600, "Field: %s, message: must not be null".formatted(field));
            assertEquals(json("1"), json.get("id"));
        }

        @Test
        void wrongJsonRpcVersionIsInvalidRequest() throws Exception {
            var json = error(send(request("server/discover", params()).put("jsonrpc", "1.0")), 400);

            assertErrorCode(json, -32600, "jsonrpc must be \"2.0\"");
        }

        @ParameterizedTest(name = "id = {0}")
        @ValueSource(strings = {"null", "true", "1.5", "{}"})
        void invalidIdIsInvalidRequestAndNotEchoed(String id) throws Exception {
            var body = request("server/discover", params());
            body.set("id", json(id));

            var json = error(send(body), 400);

            assertErrorCode(json, -32600, "id must be a string or integer");
            assertNull(json.get("id"));
        }

        @Test
        void paramsThatAreNotAnObjectAreInvalidRequest() throws Exception {
            var body = request("server/discover", params());
            body.set("params", json("[1, 2]"));

            var json = error(send(body), 400);

            assertErrorCode(json, -32600, "params must be an object");
        }

        @Test
        void malformedJsonIsParseError() throws Exception {
            var json = error(send("{\"jsonrpc\": \"2.0\", \"id\": ", headersFor("server/discover")), 400);

            assertErrorCode(json, -32700, "Parse error");
            assertNull(json.get("id"));
        }

        @Test
        void notificationIsAcceptedWithNoBody() throws Exception {
            var body = request("notifications/cancelled", params());
            body.remove("id");

            var response = send(body);

            assertEquals(202, response.statusCode());
            assertEquals("", response.body());
        }

        @Test
        void unknownMethodIsNotFound() throws Exception {
            var json = error(send(request("initialize", params())), 404);

            assertErrorCode(json, -32601, "Method not found: initialize");
        }
    }

    @Nested
    class RequestMeta {

        @Test
        void missingMetaIsInvalidParams() throws Exception {
            var body = request("server/discover", params());
            ((ObjectNode) body.get("params")).remove("_meta");

            var json = error(send(body), 400);

            assertErrorCode(json, -32602, "Missing params._meta");
        }

        @Test
        void missingParamsIsInvalidParams() throws Exception {
            var body = request("server/discover", params());
            body.remove("params");

            var json = error(send(body), 400);

            assertErrorCode(json, -32602, "Missing params._meta");
        }

        @ParameterizedTest(name = "missing {0}")
        @ValueSource(strings = {"io.modelcontextprotocol/protocolVersion", "io.modelcontextprotocol/clientCapabilities"})
        void missingRequiredMetaFieldIsInvalidParams(String field) throws Exception {
            var body = request("server/discover", params());
            ((ObjectNode) body.get("params").get("_meta")).remove(field);

            var json = error(send(body), 400);

            assertErrorCode(json, -32602, "Missing _meta." + field);
        }

        @Test
        void unsupportedVersionListsTheSupportedOnes() throws Exception {
            var body = request("server/discover", params());
            ((ObjectNode) body.get("params").get("_meta")).put("io.modelcontextprotocol/protocolVersion", "1900-01-01");
            var headers = headersFor("server/discover");
            headers.put("MCP-Protocol-Version", "1900-01-01");

            var json = error(send(body.toString(), headers), 400);

            assertErrorCode(json, -32022, "Unsupported protocol version");
            assertEquals(json("{\"supported\":[\"2026-07-28\"],\"requested\":\"1900-01-01\"}"), json.get("error").get("data"));
        }
    }

    @Nested
    class HttpHeaders {

        @ParameterizedTest(name = "missing {0}")
        @ValueSource(strings = {"MCP-Protocol-Version", "Mcp-Method"})
        void missingRequiredHeaderIsHeaderMismatch(String header) throws Exception {
            var headers = headersFor("server/discover");
            headers.remove(header);

            var json = error(send(request("server/discover", params()).toString(), headers), 400);

            assertErrorCode(json, -32020, "Missing %s header".formatted(header));
        }

        @Test
        void methodHeaderMustMatchBody() throws Exception {
            var headers = headersFor("tools/list");

            var json = error(send(request("server/discover", params()).toString(), headers), 400);

            assertErrorCode(json, -32020, "Mcp-Method header value 'tools/list' does not match body value 'server/discover'");
        }

        @Test
        void protocolVersionHeaderMustMatchBody() throws Exception {
            var headers = headersFor("server/discover");
            headers.put("MCP-Protocol-Version", "2025-11-25");

            var json = error(send(request("server/discover", params()).toString(), headers), 400);

            assertErrorCode(json, -32020, "MCP-Protocol-Version header value '2025-11-25' does not match body value '2026-07-28'");
        }

        @Test
        void toolsCallRequiresNameHeader() throws Exception {
            var headers = headersFor("tools/call");

            var json = error(send(callTool("read", null).toString(), headers), 400);

            assertErrorCode(json, -32020, "Missing Mcp-Name header");
        }

        @Test
        void nameHeaderMustMatchBody() throws Exception {
            var headers = headersFor("tools/call");
            headers.put("Mcp-Name", "write");

            var json = error(send(callTool("read", null).toString(), headers), 400);

            assertErrorCode(json, -32020, "Mcp-Name header value 'write' does not match body value 'read'");
        }

        @Test
        void base64EncodedNameHeaderIsDecodedBeforeComparing() throws Exception {
            Files.writeString(DATABASE, "hello\n");
            var headers = headersFor("tools/call");
            headers.put("Mcp-Name", "=?base64?" + Base64.getEncoder().encodeToString("read".getBytes(StandardCharsets.UTF_8)) + "?=");

            var result = success(send(callTool("read", null).toString(), headers)).get("result");

            assertToolResult(result, false, "hello\n");
        }
    }

    @Nested
    class Transport {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"GET", "DELETE"})
        void onlyPostIsAllowed(String method) throws Exception {
            var request = HttpRequest.newBuilder(endpoint()).method(method, HttpRequest.BodyPublishers.noBody()).build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(405, response.statusCode());
        }

        @ParameterizedTest(name = "Origin: {0}")
        @ValueSource(strings = {"https://evil.example.com", "http://localhost.evil.com", "null"})
        void foreignOriginIsForbidden(String origin) throws Exception {
            var headers = headersFor("server/discover");
            headers.put("Origin", origin);

            var response = send(request("server/discover", params()).toString(), headers);

            assertEquals(403, response.statusCode());
            assertErrorCode(mapper.readTree(response.body()), -32600, "Forbidden origin");
        }

        @ParameterizedTest(name = "Origin: {0}")
        @ValueSource(strings = {"http://localhost:6274", "http://127.0.0.1:3000"})
        void localOriginIsAllowed(String origin) throws Exception {
            var headers = headersFor("server/discover");
            headers.put("Origin", origin);

            success(send(request("server/discover", params()).toString(), headers));
        }
    }

    // --- request builders ---

    // params with the _meta every 2026-07-28 request must carry
    private ObjectNode params() {
        var params = mapper.createObjectNode();
        var meta = params.putObject("_meta");
        meta.put("io.modelcontextprotocol/protocolVersion", VERSION);
        meta.putObject("io.modelcontextprotocol/clientInfo").put("name", "integration-test").put("version", "1.0.0");
        meta.putObject("io.modelcontextprotocol/clientCapabilities");
        return params;
    }

    private ObjectNode request(String method, ObjectNode params) {
        var body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", 1);
        body.put("method", method);
        body.set("params", params);
        return body;
    }

    private ObjectNode callTool(String tool, String argumentsJson) {
        var params = params().put("name", tool);
        if (argumentsJson != null)
            params.set("arguments", json(argumentsJson));
        return request("tools/call", params);
    }

    // the headers a well-behaved client mirrors from the body
    private Map<String, String> headersFor(String method) {
        var headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json, text/event-stream");
        headers.put("MCP-Protocol-Version", VERSION);
        headers.put("Mcp-Method", method);
        return headers;
    }

    // --- sending ---

    // sends the body with correct headers derived from it
    private HttpResponse<String> send(ObjectNode body) throws Exception {
        var headers = headersFor(body.path("method").asString());
        var name = body.path("params").path("name");
        if (name.isString())
            headers.put("Mcp-Name", name.asString());
        return send(body.toString(), headers);
    }

    private HttpResponse<String> send(String body, Map<String, String> headers) throws Exception {
        var builder = HttpRequest.newBuilder(endpoint()).POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // 127.0.0.1 rather than localhost: the server only binds to IPv4 loopback
    private URI endpoint() {
        return URI.create("http://127.0.0.1:%d/mcp".formatted(port));
    }

    // --- assertions ---

    private JsonNode success(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), "unexpected status, body: " + response.body());
        var json = mapper.readTree(response.body());
        assertEquals("2.0", json.get("jsonrpc").asString());
        assertTrue(json.has("result"), "expected a result but got: " + response.body());
        assertFalse(json.has("error"), "expected no error but got: " + response.body());
        return json;
    }

    private JsonNode error(HttpResponse<String> response, int expectedStatus) {
        assertEquals(expectedStatus, response.statusCode(), "unexpected status, body: " + response.body());
        var json = mapper.readTree(response.body());
        assertEquals("2.0", json.get("jsonrpc").asString());
        assertTrue(json.has("error"), "expected an error but got: " + response.body());
        assertFalse(json.has("result"), "expected no result but got: " + response.body());
        return json;
    }

    private void assertErrorCode(JsonNode json, int code, String message) {
        assertEquals(code, json.get("error").get("code").asInt());
        assertEquals(message, json.get("error").get("message").asString());
    }

    private void assertToolResult(JsonNode result, boolean isError, String text) {
        assertEquals("complete", result.get("resultType").asString());
        assertEquals(isError, result.get("isError").asBoolean(), "isError mismatch in: " + result);
        assertEquals(json("[{\"type\":\"text\",\"text\":%s}]".formatted(mapper.writeValueAsString(text))), result.get("content"));
    }

    private JsonNode json(String json) {
        return mapper.readTree(json);
    }
}
