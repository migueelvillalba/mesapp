package mx.mesapp.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness/readiness básica; la aplicación debe añadir una comprobación de DB antes de operar pedidos. */
@RestController
@Profile("central")
public class HealthController {
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "component", "mesapp-backend", "time", Instant.now());
    }
}
