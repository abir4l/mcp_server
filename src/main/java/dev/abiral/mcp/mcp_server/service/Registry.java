package dev.abiral.mcp.mcp_server.service;

import dev.abiral.mcp.mcp_server.exception.MissingParamException;
import dev.abiral.mcp.mcp_server.exception.ParamValidationException;
import dev.abiral.mcp.mcp_server.exception.ToolNotFoundException;
import dev.abiral.mcp.mcp_server.exception.UnknownParamException;
import dev.abiral.mcp.mcp_server.tool.InputSchema;
import dev.abiral.mcp.mcp_server.tool.PropertySchema;
import dev.abiral.mcp.mcp_server.tool.ToolBox;
import dev.abiral.mcp.mcp_server.tool.ToolDto;
import dev.abiral.mcp.mcp_server.tool.schema.ParamSpec;
import dev.abiral.mcp.mcp_server.tool.schema.ParamType;
import dev.abiral.mcp.mcp_server.tool.schema.ToolOutput;
import dev.abiral.mcp.mcp_server.tool.schema.ToolSchema;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class Registry {

    private final List<ToolSchema> toolSchemas = new ArrayList<>();
    private final ToolBox toolBox;

    // tools are registered here rather than on ApplicationReadyEvent, so they're listed from the first request
    public Registry(ToolBox toolBox) {
        this.toolBox = toolBox;
        registerTools();
    }

    public ToolOutput execute(String name, JsonNode request) {
        var schema = findTool(name);
        try {
            return schema.handler().execute(extractArguments(request));
        } catch (RuntimeException e) {
            // rethrown as-is so callers can tell failures apart (e.g. a missing database)
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void registerTools() {
        toolSchemas.add(
                new ToolSchema(
                        "read",
                        "Read all file contents",
                        null,
                        args -> toolBox.readTool()
                )
        );
        toolSchemas.add(
                new ToolSchema(
                        "write",
                        "Write all contents to file",
                        List.of(new ParamSpec(
                                "content",
                                ParamType.STRING,
                                "Content that gets written",
                                true
                        )),
                        args -> ToolOutput.of(toolBox.write(args.get("content").asString()))
                )
        );
        toolSchemas.add(
                new ToolSchema(
                        "append",
                        "Append content to the end of the file",
                        List.of(new ParamSpec(
                                "content",
                                ParamType.STRING,
                                "Content that gets appended",
                                true
                        )),
                        args -> ToolOutput.of(toolBox.append(args.get("content").asString()))
                )
        );
        toolSchemas.add(
                new ToolSchema(
                        "update",
                        "Replace every occurrence of a piece of text in the file",
                        List.of(
                                new ParamSpec(
                                        "search",
                                        ParamType.STRING,
                                        "Exact text to find",
                                        true
                                ),
                                new ParamSpec(
                                        "replace",
                                        ParamType.STRING,
                                        "Text that replaces each match",
                                        true
                                )
                        ),
                        args -> ToolOutput.of(toolBox.update(args.get("search").asString(), args.get("replace").asString()))
                )
        );
        toolSchemas.add(
                new ToolSchema(
                        "clear",
                        "Delete all contents of the file",
                        null,
                        args -> ToolOutput.of(toolBox.clear())
                )
        );
    }

    public List<ToolDto> toolsList() {
        return toolSchemas.stream().map(
                schema -> {
                    // LinkedHashMap keeps properties in declaration order so tools/list is deterministic
                    Map<String, PropertySchema> properties = new LinkedHashMap<>();
                    List<String> required = new ArrayList<>();
                    if (schema.paramSpecs() != null) {
                        for (ParamSpec param : schema.paramSpecs()) {
                            properties.put(param.name(), new PropertySchema(param.paramType().getJsonName(), param.description()));
                            if (param.required())
                                required.add(param.name());
                        }
                    }
                    var inputSchema = new InputSchema(
                            "object",
                            properties,
                            required,
                            false
                    );

                    return new ToolDto(
                            schema.name(),
                            schema.description(),
                            inputSchema
                    );

                }
        ).toList();
    }

    public void validate(String method, JsonNode params) {

        var toolSchema = findTool(method);
        var paramSpecs = toolSchema.paramSpecs() == null ? List.<ParamSpec>of() : toolSchema.paramSpecs();
        var arguments = extractArguments(params);

        if (arguments != null && !arguments.isObject())
            throw new ParamValidationException("arguments");

        // reject arguments the tool doesn't declare
        if (arguments != null) {
            for (String name : arguments.propertyNames()) {
                boolean declared = paramSpecs.stream().anyMatch(spec -> spec.name().equals(name));
                if (!declared)
                    throw new UnknownParamException(name);
            }
        }

        for (ParamSpec param : paramSpecs) {
            JsonNode value = arguments == null ? null : arguments.get(param.name());
            // absent and explicit null are both treated as "not provided"
            if (value == null || value.isNull()) {
                if (param.required())
                    throw new MissingParamException(param.name());
                continue;
            }
            if (!param.paramType().getCheck().test(value))
                throw new ParamValidationException(param.name());
        }
    }

    private ToolSchema findTool(String name) {
        return toolSchemas.stream()
                // tool names are case-sensitive per the MCP spec
                .filter(schema -> schema.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ToolNotFoundException(name));
    }

    // tool arguments live under params.arguments, e.g. { "params": { "arguments": { "content": "..." } } }
    private JsonNode extractArguments(JsonNode params) {
        if (params == null)
            return null;
        return params.get("arguments");
    }
}
