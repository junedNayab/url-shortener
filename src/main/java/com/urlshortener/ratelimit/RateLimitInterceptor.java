package com.urlshortener.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    private final StringRedisTemplate redisTemplate;
    private final int maxRequests;
    private final int windowSeconds;

    public RateLimitInterceptor(
            StringRedisTemplate redisTemplate,
            @Value("${app.rate-limit.max-requests}") int maxRequests,
            @Value("${app.rate-limit.window-seconds}") int windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String clientIp = request.getRemoteAddr();
        String key = RATE_LIMIT_PREFIX + clientIp;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            }

            response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
            response.setHeader("X-RateLimit-Remaining",
                    String.valueOf(Math.max(0, maxRequests - (count != null ? count : 0))));

            if (count != null && count > maxRequests) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"status\":429,\"message\":\"Rate limit exceeded. Try again later.\"}");
                return false;
            }
        } catch (Exception e) {
            log.warn("Rate limiting unavailable (Redis down?): {}", e.getMessage());
            // Fail open — allow request if Redis is unavailable
        }

        return true;
    }
}
