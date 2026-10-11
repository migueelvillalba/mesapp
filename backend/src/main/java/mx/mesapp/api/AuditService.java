package mx.mesapp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import mx.mesapp.config.BootstrapData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public AuditService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public void record(HttpSession session, String action, UUID resourceId, Object details) {
        UUID employeeId = null;
        if (session != null && session.getAttribute("employeeId") != null) employeeId = UUID.fromString(session.getAttribute("employeeId").toString());
        String json;
        try { json = mapper.writeValueAsString(details == null ? java.util.Map.of() : details); }
        catch (Exception e) { json = "{}"; }
        jdbc.update("INSERT INTO registro_auditoria(id,restaurante_id,empleado_id,accion,recurso_id,detalles) VALUES (?,?,?,?,?,?::jsonb)",
            UUID.randomUUID(), BootstrapData.RESTAURANT_ID, employeeId, action, resourceId, json);
    }
}
