package dev.abiral.mcp.mcp_server.tool;

import dev.abiral.mcp.mcp_server.config.McpProperties;
import dev.abiral.mcp.mcp_server.exception.DatabaseNotFoundException;
import dev.abiral.mcp.mcp_server.tool.schema.ToolOutput;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ToolBox {

    // _meta key telling the client whether the file had any data
    public static final String META_EMPTY = "dev.abiral/empty";

    // no CREATE option, so writing to a missing file fails instead of silently recreating it
    private static final StandardOpenOption[] OVERWRITE = {StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};

    private final Path database;

    public ToolBox(McpProperties properties) {
        this.database = Path.of(properties.databasePath());
    }

    public String read(){
        try {
            return Files.readString(database);
        } catch (NoSuchFileException e) {
            throw new DatabaseNotFoundException(database);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // an empty file is a valid state, so it's a success with an explicit message rather than blank text
    public ToolOutput readTool(){
        String lines = read();
        if (lines.isBlank())
            return new ToolOutput("The file is empty. No data has been written yet.", Map.of(META_EMPTY, true));
        return new ToolOutput(lines, Map.of(META_EMPTY, false));
    }

    // each write/append ends the file with a newline, so appends always start on a fresh line
    // mutators are synchronized so update's read-modify-write can't interleave with another call
    public synchronized String write(String content){
        try {
            Files.writeString(database, content + "\n", OVERWRITE);
            return "Success";
        } catch (NoSuchFileException e) {
            throw new DatabaseNotFoundException(database);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized String append(String content){
        try {
            Files.writeString(database, content + "\n", StandardOpenOption.APPEND);
            return "Success";
        } catch (NoSuchFileException e) {
            throw new DatabaseNotFoundException(database);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized String update(String search, String replace){
        // an empty search would match between every character
        if (search.isEmpty())
            throw new IllegalArgumentException("search must not be empty");

        String content = read();
        int occurrences = content.split(Pattern.quote(search), -1).length - 1;
        if (occurrences == 0)
            throw new IllegalArgumentException("Text '%s' was not found in the file".formatted(search));

        // written as-is rather than through write(), which would add another trailing newline
        try {
            Files.writeString(database, content.replace(search, replace), OVERWRITE);
        } catch (NoSuchFileException e) {
            throw new DatabaseNotFoundException(database);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return "Replaced %d occurrence(s)".formatted(occurrences);
    }

    public synchronized String clear(){
        try {
            Files.write(database, new byte[0], OVERWRITE);
            return "File cleared";
        } catch (NoSuchFileException e) {
            throw new DatabaseNotFoundException(database);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
