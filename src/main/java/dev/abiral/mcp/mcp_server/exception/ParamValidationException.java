package dev.abiral.mcp.mcp_server.exception;

public class ParamValidationException extends ToolArgumentException{

    public ParamValidationException(String paramName) {
        super("Param (%s) type doesn't match".formatted(paramName));
    }
}
