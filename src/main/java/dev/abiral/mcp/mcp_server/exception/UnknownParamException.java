package dev.abiral.mcp.mcp_server.exception;

public class UnknownParamException extends ToolArgumentException{

    public UnknownParamException(String paramName) {
        super("Param (%s) is not accepted by this tool".formatted(paramName));
    }
}
