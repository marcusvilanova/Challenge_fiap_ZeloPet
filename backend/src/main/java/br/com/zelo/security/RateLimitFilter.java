package br.com.zelo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de taxa em memória (janela fixa de 1 minuto). Protege o login (por IP) e os endpoints
 * que acionam IA (por usuário). Em produção com várias instâncias, trocar por um limitador distribuído.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private record Window(long startMillis, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int perMinute;
    private final int loginPerMinute;

    public RateLimitFilter(Clock clock, int perMinute, int loginPerMinute) {
        this.clock = clock;
        this.perMinute = perMinute;
        this.loginPerMinute = loginPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limitFor(pathOf(request)) == 0;
    }

    /** Caminho já decodificado: "/api/%74riage" e "/api/triage" são o mesmo recurso e o mesmo balde. */
    private static String pathOf(HttpServletRequest request) {
        return PATHS.getPathWithinApplication(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = pathOf(request);
        boolean byAddress = isAddressLimited(path);
        String key = byAddress ? path + ":" + request.getRemoteAddr() : "user:" + identity(request);
        if (!tryAcquire(key, limitFor(path))) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/problem+json;charset=UTF-8");
            response.getWriter().write("{\"title\":\"Muitas requisições\",\"status\":429,"
                    + "\"detail\":\"Aguarde um minuto antes de tentar novamente.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isAddressLimited(String path) {
        return path.equals("/api/auth/login") || path.equals("/api/auth/register");
    }

    private int limitFor(String path) {
        if (isAddressLimited(path)) {
            return loginPerMinute;
        }
        boolean aiEndpoint = path.startsWith("/api/chat") || path.equals("/api/triage") || path.startsWith("/api/vision");
        return aiEndpoint ? perMinute : 0;
    }

    private static String identity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserDetails user
                ? String.valueOf(user.id()) : request.getRemoteAddr();
    }

    boolean tryAcquire(String key, int limit) {
        long now = clock.millis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> now - w.startMillis() > WINDOW_MILLIS);
        }
        Window updated = windows.merge(key, new Window(now, 1), (old, fresh) ->
                now - old.startMillis() >= WINDOW_MILLIS ? fresh : new Window(old.startMillis(), old.count() + 1));
        return updated.count() <= limit;
    }
}
