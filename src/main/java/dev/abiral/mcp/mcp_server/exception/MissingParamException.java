package dev.abiral.mcp.mcp_server.exception;

public class MissingParamException extends ToolArgumentException{

    public MissingParamException(String paramName) {
        super("Param (%s) is required".formatted(paramName));
    }
}
