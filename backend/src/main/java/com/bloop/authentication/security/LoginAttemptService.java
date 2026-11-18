package com.bloop.authentication.security;

import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proteccion frente a fuerza bruta (rate limiting) en el login y en la recuperacion de contrasena.
 *
 * Contadores en memoria con dos claves:
 * - email + IP: tras N fallos (LOGIN_MAX_FAILURES, 5 por defecto) se bloquea ese email desde esa IP
 *   durante LOGIN_LOCK_MINUTES. Al ser por IP, un atacante no puede bloquear la cuenta de otra persona
 *   desde otro sitio.
 * - IP: un limite mas alto (4 x N) frena a quien prueba muchos emails distintos desde la misma IP.
 *
 * Limitacion conocida: los contadores viven en la memoria de esta instancia. Con varias instancias
 * del backend habria que moverlos a la base de datos o a Redis (documentado en SECURITY.md).
 */
@Component
public class LoginAttemptService {

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration lockDuration;
    private final Clock clock;

    public LoginAttemptService(BloopProperties properties, Clock clock) {
        this.maxFailures = Math.max(1, properties.security().maxLoginFailures());
        this.lockDuration = Duration.ofMinutes(Math.max(1, properties.security().loginLockMinutes()));
        this.clock = clock;
    }

    /** Lanza 429 si el email (desde esta IP) o la propia IP estan bloqueados. */
    public void assertLoginAllowed(String email) {
        String ip = clientIp();
        check(loginKey(email, ip), maxFailures);
        check("ip|" + ip, maxFailures * 4);
    }

    /**
     * Registra un intento fallido.
     *
     * @return true si este fallo provoca el bloqueo (para registrarlo en la auditoria)
     */
    public boolean registerFailure(String email) {
        String ip = clientIp();
        increment("ip|" + ip);
        return increment(loginKey(email, ip)) == maxFailures;
    }

    /** Login correcto: se olvidan los fallos de ese email desde esa IP. */
    public void registerSuccess(String email) {
        counters.remove(loginKey(email, clientIp()));
    }

    /** Recuperacion de contrasena: como mucho N solicitudes por IP en la ventana de bloqueo. */
    public void assertPasswordResetAllowed() {
        String key = "reset|" + clientIp();
        check(key, maxFailures);
        increment(key);
    }

    /** Olvida todos los contadores (la usan los tests para empezar siempre desde cero). */
    public void clearAll() {
        counters.clear();
    }

    /** Limpieza periodica de contadores caducados para que la memoria no crezca sin limite. */
    @Scheduled(fixedDelayString = "PT10M")
    public void evictExpired() {
        Instant now = Instant.now(clock);
        counters.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }

    private void check(String key, int limit) {
        Counter counter = counters.get(key);
        Instant now = Instant.now(clock);
        if (counter != null && counter.expiresAt.isAfter(now) && counter.count >= limit) {
            long seconds = Math.max(1, Duration.between(now, counter.expiresAt).getSeconds());
            throw new TooManyRequestsException("Demasiados intentos. Vuelve a intentarlo en "
                    + Math.max(1, seconds / 60) + " minutos", seconds);
        }
    }

    /** Suma un intento a la ventana actual (o abre una nueva si la anterior caduco). */
    private int increment(String key) {
        Instant now = Instant.now(clock);
        return counters.compute(key, (k, current) -> {
            if (current == null || current.expiresAt.isBefore(now)) {
                return new Counter(1, now.plus(lockDuration));
            }
            return new Counter(current.count + 1, now.plus(lockDuration));
        }).count;
    }

    private static String loginKey(String email, String ip) {
        return "login|" + email.trim().toLowerCase(Locale.ROOT) + "|" + ip;
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return "unknown";
    }

    private record Counter(int count, Instant expiresAt) {
    }
}
