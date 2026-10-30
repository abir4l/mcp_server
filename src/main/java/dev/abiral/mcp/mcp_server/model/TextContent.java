package dev.abiral.mcp.mcp_server.model;

public record TextContent(
        String type,
        String text) {

    public static TextContent of(String text) {
        return new TextContent("text", text);
    }
}
