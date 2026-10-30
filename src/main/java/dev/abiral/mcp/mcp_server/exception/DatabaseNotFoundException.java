package dev.abiral.mcp.mcp_server.exception;

import java.nio.file.Path;

/**
 * The notes file the tools operate on doesn't exist.
 * Reported back as a tool result with isError=true, since it's a server-side setup problem rather than a bad request.
 */
public class DatabaseNotFoundException extends RuntimeException{

    public DatabaseNotFoundException(Path path) {
        super("Database file not found at %s. Start the server from the repo root or create the file."
                .formatted(path.toAbsolutePath()));
    }
}
