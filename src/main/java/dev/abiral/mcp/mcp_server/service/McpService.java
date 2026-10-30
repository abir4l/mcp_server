package dev.abiral.mcp.mcp_server.service;

import dev.abiral.mcp.mcp_server.config.McpProperties;
import dev.abiral.mcp.mcp_server.exception.DatabaseNotFoundException;
import dev.abiral.mcp.mcp_server.exception.RpcException;
import dev.abiral.mcp.mcp_server.exception.ToolArgumentException;
import dev.abiral.mcp.mcp_server.exception.ToolNotFoundException;
import dev.abiral.mcp.mcp_server.model.CallToolResult;
import dev.abiral.mcp.mcp_server.model.DiscoverResult;
import dev.abiral.mcp.mcp_server.model.McpHeaders;
import dev.abiral.mcp.mcp_server.model.RpcError;
import dev.abiral.mcp.mcp_server.model.RpcRequest;
import dev.abiral.mcp.mcp_server.model.ServerInfo;
import dev.abiral.mcp.mcp_server.model.ToolsListResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the MCP 2026-07-28 protocol on top of the tool registry.
 * The protocol is stateless: every request carries its own version and capabilities in params._meta,
 * so each request is validated and handled on its own.
 */
@Service
public class McpService {

    public static final String PROTOCOL_VERSION = "2026-07-28";
    public static final List<String> SUPPORTED_VERSIONS = List.of(PROTOCOL_VERSION);

    private static final String META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    private static final String META_CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";
    private final Registry registry;
    private final Map<String, Object> resultMeta;

    public McpService(Registry registry, McpProperties properties) {
        this.registry = registry;
        this.resultMeta = Map.of(
                "io.modelcontextprotocol/serverInfo",
                new ServerInfo(properties.server().name(), properties.server().version())
        );
    }

    /**
     * Checks the JSON-RPC envelope. Runs for requests and notifications alike.
     */
    public void validateEnvelope(RpcRequest request) {
        if (!"2.0".equals(request.jsonrpc()))
            throw RpcException.invalidRequest("jsonrpc must be \"2.0\"");
        // id is optional (notifications), but when present it must be a string or integer
        if (request.id() != null && !request.hasValidId())
            throw RpcException.invalidRequest("id must be a string or integer");
        if (request.params() != null && !request.params().isObject())
            throw RpcException.invalidRequest("params must be an object");
    }

    /**
     * Handles a request (not a notification) and returns the result object.
     * Protocol failures are thrown as RpcException.
     */
    public Object handle(RpcRequest request, McpHeaders headers) {
        validateHeaders(request, headers);
        validateMeta(request.params(), headers);

        return switch (request.method()) {
            case "server/discover" -> discover();
            case "tools/list" -> listTools(request.params());
            case "tools/call" -> callTool(request.params(), headers);
            default -> throw new RpcException(
                    RpcError.METHOD_NOT_FOUND,
                    "Method not found: " + request.method(),
                    HttpStatus.NOT_FOUND
            );
        };
    }

    private void validateHeaders(RpcRequest request, McpHeaders headers) {
        if (headers.protocolVersion() == null)
            throw RpcException.headerMismatch("Missing MCP-Protocol-Version header");
        if (headers.method() == null)
            throw RpcException.headerMismatch("Missing Mcp-Method header");
        if (!headers.method().equals(request.method()))
            throw RpcException.headerMismatch("Mcp-Method header value '%s' does not match body value '%s'"
                    .formatted(headers.method(), request.method()));
    }

    private void validateMeta(JsonNode params, McpHeaders headers) {
        JsonNode meta = params == null ? null : params.get("_meta");
        if (meta == null || !meta.isObject())
            throw RpcException.invalidParams("Missing params._meta", HttpStatus.BAD_REQUEST);

        JsonNode version = meta.get(META_PROTOCOL_VERSION);
        if (version == null || !version.isString())
            throw RpcException.invalidParams("Missing _meta.%s".formatted(META_PROTOCOL_VERSION), HttpStatus.BAD_REQUEST);

        JsonNode capabilities = meta.get(META_CLIENT_CAPABILITIES);
        if (capabilities == null || !capabilities.isObject())
            throw RpcException.invalidParams("Missing _meta.%s".formatted(META_CLIENT_CAPABILITIES), HttpStatus.BAD_REQUEST);

        // the header is a mirror of the body, the body is the source of truth
        if (!version.asString().equals(headers.protocolVersion()))
            throw RpcException.headerMismatch("MCP-Protocol-Version header value '%s' does not match body value '%s'"
                    .formatted(headers.protocolVersion(), version.asString()));

        if (!SUPPORTED_VERSIONS.contains(version.asString()))
            throw new RpcException(
                    RpcError.UNSUPPORTED_PROTOCOL_VERSION,
                    "Unsupported protocol version",
                    Map.of("supported", SUPPORTED_VERSIONS, "requested", version.asString()),
                    HttpStatus.BAD_REQUEST
            );
    }

    private DiscoverResult discover() {
        return new DiscoverResult(
                "complete",
                SUPPORTED_VERSIONS,
                // we only offer tools, and never send list_changed notifications
                Map.of("tools", Map.of()),
                resultMeta
        );
    }

    private ToolsListResult listTools(JsonNode params) {
        // all tools fit in one page, so we never hand out a cursor and any cursor we get is invalid
        JsonNode cursor = params.get("cursor");
        if (cursor != null && !cursor.isNull())
            throw RpcException.invalidParams("Invalid cursor", HttpStatus.OK);

        return new ToolsListResult("complete", registry.toolsList(), resultMeta);
    }

    private CallToolResult callTool(JsonNode params, McpHeaders headers) {
        JsonNode name = params.get("name");
        if (name == null || !name.isString())
            throw RpcException.invalidParams("params.name must be a string", HttpStatus.OK);
        String toolName = name.asString();

        if (headers.name() == null)
            throw RpcException.headerMismatch("Missing Mcp-Name header");
        if (!decodeHeaderValue(headers.name()).equals(toolName))
            throw RpcException.headerMismatch("Mcp-Name header value '%s' does not match body value '%s'"
                    .formatted(headers.name(), toolName));

        JsonNode arguments = params.get("arguments");
        if (arguments != null && !arguments.isObject())
            throw RpcException.invalidParams("params.arguments must be an object", HttpStatus.OK);

        // unknown tool is a protocol error, bad arguments are a tool error the model can fix
        try {
            registry.validate(toolName, params);
        } catch (ToolNotFoundException e) {
            throw RpcException.invalidParams("Unknown tool: " + toolName, HttpStatus.OK);
        } catch (ToolArgumentException e) {
            return CallToolResult.error(e.getMessage(), resultMeta);
        }

        try {
            var output = registry.execute(toolName, params);
            // tool-specific _meta sits alongside the server info
            Map<String, Object> meta = new HashMap<>(resultMeta);
            meta.putAll(output.meta());
            return CallToolResult.success(output.text(), meta);
        } catch (DatabaseNotFoundException e) {
            return CallToolResult.error(e.getMessage(), resultMeta);
        } catch (RuntimeException e) {
            return CallToolResult.error("Tool execution failed: " + e.getMessage(), resultMeta);
        }
    }

    // header values that aren't plain ASCII are sent as =?base64?<value>?=
    private String decodeHeaderValue(String value) {
        if (!value.startsWith("=?base64?") || !value.endsWith("?="))
            return value;
        String encoded = value.substring("=?base64?".length(), value.length() - "?=".length());
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw RpcException.headerMismatch("Header value '%s' is not valid base64".formatted(value));
        }
    }
}
