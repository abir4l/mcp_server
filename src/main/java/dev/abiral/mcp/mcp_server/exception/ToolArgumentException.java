package dev.abiral.mcp.mcp_server.exception;

/**
 * Base for errors in the arguments a client passed to a tool.
 * These are reported back as tool results with isError=true so the model can correct itself.
 */
public abstract class ToolArgumentException extends RuntimeException{

    protected ToolArgumentException(String message) {
        super(message);
    }
}
