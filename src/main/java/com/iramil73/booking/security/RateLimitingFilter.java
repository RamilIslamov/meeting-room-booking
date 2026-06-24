package com.iramil73.booking.security;

import com.iramil73.booking.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client fixed-window rate limiter for {@code /api/auth/**}, to blunt brute
 * force / credential stuffing. In-memory and per-instance — fine for a single
 * node; a distributed deployment would back this with Redis.
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitingFilter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        Window window = windows.computeIfAbsent(clientKey(request), k -> new Window());
        if (window.tryAcquire(properties.capacity(), properties.windowSeconds())) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(String.format(
                    "{\"timestamp\":\"%s\",\"status\":429,\"error\":\"Too Many Requests\","
                            + "\"message\":\"Rate limit exceeded, try again later\"}",
                    Instant.now()));
        }
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Fixed-window counter for a single client. */
    private static final class Window {
        private long windowStartEpochSec;
        private int count;

        synchronized boolean tryAcquire(int capacity, long windowSeconds) {
            long nowSec = Instant.now().getEpochSecond();
            if (nowSec - windowStartEpochSec >= windowSeconds) {
                windowStartEpochSec = nowSec;
                count = 0;
            }
            if (count < capacity) {
                count++;
                return true;
            }
            return false;
        }
    }
}
