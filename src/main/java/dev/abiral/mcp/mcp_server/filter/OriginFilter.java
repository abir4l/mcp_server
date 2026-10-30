package dev.abiral.mcp.mcp_server.filter;

import dev.abiral.mcp.mcp_server.config.McpProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * Rejects browser requests coming from other sites, to prevent DNS rebinding attacks
 * against this local server. Requests without an Origin header (non-browser clients) pass through.
 */
@Component
public class OriginFilter extends OncePerRequestFilter {

    private final Set<String> allowedHosts;

    public OriginFilter(McpProperties properties) {
        this.allowedHosts = properties.allowedOriginHosts();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !isAllowed(origin)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32600,\"message\":\"Forbidden origin\"}}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(String origin) {
        try {
            URI uri = URI.create(origin);
            boolean http = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
            return http && allowedHosts.contains(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
