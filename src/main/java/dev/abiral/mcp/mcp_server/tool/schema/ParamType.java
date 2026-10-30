package dev.abiral.mcp.mcp_server.tool.schema;

import tools.jackson.databind.JsonNode;

import java.util.function.Predicate;

public enum ParamType {
    STRING("string", JsonNode::isString);

    final String jsonName;
    final Predicate<JsonNode> check;

    ParamType(String jsonName, Predicate<JsonNode> check) { this.jsonName = jsonName; this.check = check; }

    public String getJsonName() {
        return jsonName;
    }

    public Predicate<JsonNode> getCheck() {
        return check;
    }
}
