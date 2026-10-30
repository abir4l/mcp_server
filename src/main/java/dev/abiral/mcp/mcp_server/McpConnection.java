package dev.abiral.mcp.mcp_server;

import dev.abiral.mcp.mcp_server.exception.RpcException;
import dev.abiral.mcp.mcp_server.model.McpHeaders;
import dev.abiral.mcp.mcp_server.model.RpcRequest;
import dev.abiral.mcp.mcp_server.model.RpcResponse;
import dev.abiral.mcp.mcp_server.service.McpService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The single MCP endpoint of the Streamable HTTP transport.
 * Only POST is mapped, so GET/DELETE get 405 as the spec asks.
 */
@RestController
public class McpConnection {

    private final McpService mcpService;

    public McpConnection(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @PostMapping("/mcp")
    public ResponseEntity<RpcResponse> mcp(
            @RequestBody @Validated RpcRequest rpcRequest,
            @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
            @RequestHeader(value = "Mcp-Method", required = false) String method,
            @RequestHeader(value = "Mcp-Name", required = false) String name) {
        try {
            mcpService.validateEnvelope(rpcRequest);

            // notifications never get a response body
            if (rpcRequest.isNotification())
                return ResponseEntity.accepted().build();

            var result = mcpService.handle(rpcRequest, new McpHeaders(protocolVersion, method, name));
            return ResponseEntity.ok(RpcResponse.success(rpcRequest.id(), result));
        } catch (RpcException e) {
            // echo the id when we can read it, otherwise the error goes out without one
            var id = rpcRequest.hasValidId() ? rpcRequest.id() : null;
            return ResponseEntity.status(e.getStatus()).body(RpcResponse.failure(id, e.toError()));
        }
    }
}
