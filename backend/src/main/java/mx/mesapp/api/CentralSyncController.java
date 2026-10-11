package mx.mesapp.api;

import static mx.mesapp.api.ApiExceptionHandler.ApiProblem.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("central")
@RequestMapping("/api/sync")
public class CentralSyncController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CentralSyncController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Receipts and the central read projection commit in the same database transaction. */
    @Transactional
    @PostMapping("/events")
    public Object receive(@RequestHeader(value = "Authorization", required = false) String authorization,
                          @Valid @RequestBody @NotEmpty List<@Valid SyncEvent> events) {
        if (events.size() > 500) throw bad("El lote excede el límite de 500 eventos");
        List<Map<String, Object>> results = new ArrayList<>();
        for (SyncEvent event : events) {
            checkToken(authorization,event);
            String payloadJson = write(event.payload());
            String canonical = write(Map.of("tipo", event.tipo(), "entidadId", event.entidadId(), "secuencia", event.secuencia(), "creadoEn", event.creadoEn(), "payload", event.payload()));
            String hash = sha256(canonical);
            int inserted = jdbc.update("""
                INSERT INTO evento_inbox(restaurante_id,evento_id,resultado,payload_hash,payload,instalacion_id,tipo,entidad_id,secuencia,creado_en_cliente)
                VALUES (?,?,?, ?,?::jsonb,?,?,?,?,?) ON CONFLICT DO NOTHING
                """, event.restauranteId(), event.id(), "ACEPTADO", hash, payloadJson, event.instalacionId(), event.tipo(), event.entidadId(), event.secuencia(), java.sql.Timestamp.from(event.creadoEn()));

            String result;
            if (inserted == 0) {
                String prior = jdbc.queryForObject("SELECT payload_hash FROM evento_inbox WHERE restaurante_id=? AND evento_id=?", String.class, event.restauranteId(), event.id());
                if (!Objects.equals(prior, hash)) throw conflict("El identificador de evento ya existe con una carga distinta");
                result = "DUPLICADO";
            } else {
                ProjectionResult projection = projectEvent(event, payloadJson);
                boolean applied = projection.applied();
                String receipt = projection.conflict() ? "CONFLICTO" : applied ? "ACEPTADO" : "ANTERIOR";
                jdbc.update("UPDATE evento_inbox SET resultado=? WHERE restaurante_id=? AND evento_id=?",receipt,event.restauranteId(),event.id());
                jdbc.update("""
                    INSERT INTO historial_evento_sincronizado(restaurante_id,evento_id,pedido_id,secuencia,tipo_evento,datos,aplicado,motivo)
                    VALUES (?,?,?,?,?,?::jsonb,?,?)
                    """, event.restauranteId(), event.id(), event.entidadId(), event.secuencia(), event.tipo(), payloadJson,
                    applied, projection.reason());
                result = receipt;
            }
            if(result.equals("CONFLICTO"))results.add(Map.of("id",event.id(),"estado",result,"motivo","Otra carga ocupa la misma versión de la entidad"));
            else results.add(Map.of("id", event.id(), "estado", result));
        }
        return Map.of("resultados", results);
    }

    private ProjectionResult projectEvent(SyncEvent event, String payloadJson) {
        String entityType=switch(event.tipo()) {
            case "PEDIDO_CONFIRMADO","PEDIDO_ESTADO_CAMBIADO","DOMICILIO_ASIGNADO" -> "PEDIDO";
            case "MESA_ACTUALIZADA" -> "MESA";
            case "CATEGORIA_ACTUALIZADA" -> "CATEGORIA";
            case "PRODUCTO_ACTUALIZADO" -> "PRODUCTO";
            case "EMPLEADO_ACTUALIZADO" -> "EMPLEADO";
            default -> throw bad("Tipo de evento no admitido");
        };
        JsonNode payload=parseJson(payloadJson);
        if(!event.entidadId().toString().equals(payload.path("id").asText()))throw bad("El evento no coincide con el identificador de la entidad");
        String state=payload.path("estado").asText();
        if(entityType.equals("PEDIDO")&&!Set.of("CONFIRMADO","EN_PREPARACION","LISTO","ENTREGADO","CANCELADO").contains(state))throw bad("El evento de pedido no contiene un estado válido");
        var current=jdbc.query("SELECT secuencia,tipo_evento,datos FROM entidad_sincronizada WHERE restaurante_id=? AND entidad_id=? FOR UPDATE",
            rs->rs.next()?new Projection(rs.getLong("secuencia"),rs.getString("tipo_evento"),parseJson(rs.getString("datos"))):null,event.restauranteId(),event.entidadId());
        if(current!=null&&event.secuencia()<current.sequence())return new ProjectionResult(false,false,"Evento anterior a la versión central vigente");
        if(current!=null&&event.secuencia()==current.sequence()) {
            if(!current.eventType().equals(event.tipo())||!current.data().equals(payload))return new ProjectionResult(false,true,"Otra carga ocupa la misma versión de la entidad");
            return new ProjectionResult(true,false,null);
        }
        jdbc.update("""
            INSERT INTO entidad_sincronizada(restaurante_id,entidad_id,tipo_entidad,tipo_evento,secuencia,datos,actualizado_en)
            VALUES (?,?,?,?,?,?::jsonb,now()) ON CONFLICT(restaurante_id,entidad_id) DO UPDATE
            SET tipo_entidad=EXCLUDED.tipo_entidad,tipo_evento=EXCLUDED.tipo_evento,secuencia=EXCLUDED.secuencia,datos=EXCLUDED.datos,actualizado_en=now()
            """,event.restauranteId(),event.entidadId(),entityType,event.tipo(),event.secuencia(),payloadJson);
        if(entityType.equals("PEDIDO"))jdbc.update("""
            INSERT INTO pedido_sincronizado(restaurante_id,pedido_id,secuencia,tipo_evento,estado,datos,actualizado_en)
            VALUES (?,?,?,?,?,?::jsonb,now()) ON CONFLICT(restaurante_id,pedido_id) DO UPDATE
            SET secuencia=EXCLUDED.secuencia,tipo_evento=EXCLUDED.tipo_evento,estado=EXCLUDED.estado,datos=EXCLUDED.datos,actualizado_en=now()
            """,event.restauranteId(),event.entidadId(),event.secuencia(),event.tipo(),state,payloadJson);
        return new ProjectionResult(true,false,null);
    }

    @GetMapping("/health")
    public Object health(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if(authorization==null||!authorization.startsWith("Bearer "))throw forbidden("La instalación no está autorizada");
        String supplied=sha256(authorization.substring(7));
        boolean valid=jdbc.query("SELECT token_sha256 FROM credencial_instalacion WHERE activa",rs->{while(rs.next())if(MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),rs.getString(1).getBytes(StandardCharsets.UTF_8)))return true;return false;});
        if(!valid)throw forbidden("La instalación no está autorizada");
        return Map.of("estado", "CONECTADO");
    }

    @GetMapping("/entities/{restaurantId}")
    public Object entities(@PathVariable UUID restaurantId,@RequestParam(required=false) String tipo,
                           @RequestHeader(value="Authorization",required=false)String authorization) {
        checkTenantToken(authorization,restaurantId);
        if(tipo!=null&&!Set.of("PEDIDO","MESA","CATEGORIA","PRODUCTO","EMPLEADO").contains(tipo))throw bad("Tipo de entidad inválido");
        return tipo==null?jdbc.queryForList("SELECT entidad_id,tipo_entidad,tipo_evento,secuencia,datos,actualizado_en FROM entidad_sincronizada WHERE restaurante_id=? ORDER BY tipo_entidad,actualizado_en DESC",restaurantId):jdbc.queryForList("SELECT entidad_id,tipo_entidad,tipo_evento,secuencia,datos,actualizado_en FROM entidad_sincronizada WHERE restaurante_id=? AND tipo_entidad=? ORDER BY actualizado_en DESC",restaurantId,tipo);
    }

    private void checkToken(String auth,SyncEvent event) { checkTenantToken(auth,event.restauranteId(),event.instalacionId()); }
    private void checkTenantToken(String auth,UUID restaurantId) {
        if(auth==null||!auth.startsWith("Bearer "))throw forbidden("La instalación no está autorizada");
        String supplied=sha256(auth.substring(7));
        boolean valid=jdbc.query("SELECT c.token_sha256 FROM credencial_instalacion c JOIN restaurante r ON r.id=c.restaurante_id WHERE c.restaurante_id=? AND c.activa AND r.activo",rs->{boolean match=false;while(rs.next())match|=MessageDigest.isEqual(rs.getString(1).getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8));return match;},restaurantId);
        if(!valid)throw forbidden("La instalación no está autorizada para este restaurante");
    }
    private void checkTenantToken(String auth,UUID restaurantId,UUID installationId) {
        if(auth==null||!auth.startsWith("Bearer "))throw forbidden("La instalación no está autorizada");
        String expected=jdbc.query("SELECT c.token_sha256 FROM credencial_instalacion c JOIN instalacion i ON i.id=c.instalacion_id AND i.restaurante_id=c.restaurante_id JOIN restaurante r ON r.id=c.restaurante_id WHERE c.restaurante_id=? AND c.instalacion_id=? AND c.activa AND r.activo",rs->rs.next()?rs.getString(1):null,restaurantId,installationId);
        String supplied=sha256(auth.substring(7));
        if(expected==null||!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))throw forbidden("La instalación no está autorizada para este restaurante");
    }
    private String write(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw bad("La carga del evento no es válida"); } }
    private JsonNode parseJson(String value) { try { return mapper.readTree(value); } catch (Exception e) { throw new IllegalStateException("El JSON almacenado no es válido",e); } }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private record Projection(long sequence, String eventType, JsonNode data) {}
    private record ProjectionResult(boolean applied,boolean conflict,String reason){}
    public record SyncEvent(@NotNull UUID id, @NotNull UUID restauranteId, @NotNull UUID instalacionId,
                            @NotBlank String tipo, @NotNull UUID entidadId, @Min(1) long secuencia,
                            @NotNull java.time.Instant creadoEn, @NotNull Map<String, Object> payload) {}
}
