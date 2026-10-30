package dev.abiral.mcp.mcp_server.exception;

public class ToolNotFoundException extends RuntimeException{

    public ToolNotFoundException(String toolName) {
        super("Unknown tool: " + toolName);
    }
}
