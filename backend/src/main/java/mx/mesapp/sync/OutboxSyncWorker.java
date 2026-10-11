package mx.mesapp.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import mx.mesapp.config.BootstrapData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Profile("local")
public class OutboxSyncWorker {
    private static final Logger log=LoggerFactory.getLogger(OutboxSyncWorker.class);
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final String centralUrl;private final String token;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile String cloudStatus="SIN_CONFIGURAR";private volatile Instant lastCheck;
    public OutboxSyncWorker(JdbcTemplate jdbc,ObjectMapper mapper,@Value("${mesapp.central-url:}")String centralUrl,@Value("${mesapp.sync-token:}")String token){this.jdbc=jdbc;this.mapper=mapper;this.centralUrl=centralUrl;this.token=token;}
    @Scheduled(fixedDelayString="${mesapp.sync-interval-ms:15000}")
    public void synchronize(){if(centralUrl.isBlank()||token.isBlank()){cloudStatus="SIN_CONFIGURAR";return;}try{var probe=HttpRequest.newBuilder(URI.create(centralUrl.replaceAll("/$","")+"/actuator/health")).timeout(Duration.ofSeconds(4)).GET().build();var response=client.send(probe,HttpResponse.BodyHandlers.discarding());lastCheck=Instant.now();cloudStatus=response.statusCode()/100==2?"CONECTADO":"SIN_CONEXION";}catch(Exception e){lastCheck=Instant.now();cloudStatus="SIN_CONEXION";}var rows=jdbc.queryForList("SELECT id,instalacion_id,tipo,entidad_id,secuencia,creado_en,payload,intentos FROM evento_outbox WHERE restaurante_id=? AND estado='PENDIENTE' AND siguiente_intento<=now() ORDER BY creado_en LIMIT 50",BootstrapData.RESTAURANT_ID);if(rows.isEmpty()||!cloudStatus.equals("CONECTADO"))return;List<Map<String,Object>> events=new ArrayList<>();for(var row:rows){Object value=row.get("payload");events.add(Map.of("id",row.get("id"),"restauranteId",BootstrapData.RESTAURANT_ID,"instalacionId",row.get("instalacion_id"),"tipo",row.get("tipo"),"entidadId",row.get("entidad_id"),"secuencia",row.get("secuencia"),"creadoEn",row.get("creado_en"),"payload",parse(value)));}
        try{cloudStatus="SINCRONIZANDO";String body=mapper.writeValueAsString(events);String url=centralUrl.replaceAll("/$","")+"/api/sync/events";HttpRequest request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()/100==2){JsonNode receipts=mapper.readTree(response.body()).path("resultados");for(JsonNode receipt:receipts){UUID id=UUID.fromString(receipt.path("id").asText());String state=receipt.path("estado").asText();if(Set.of("ACEPTADO","DUPLICADO","ANTERIOR").contains(state))jdbc.update("UPDATE evento_outbox SET estado='SINCRONIZADO',ultimo_error=NULL WHERE id=? AND restaurante_id=?",id,BootstrapData.RESTAURANT_ID);else if(state.equals("CONFLICTO"))jdbc.update("UPDATE evento_outbox SET estado='CONFLICTO',ultimo_error=? WHERE id=? AND restaurante_id=?",receipt.path("motivo").asText("Conflicto de versión en central"),id,BootstrapData.RESTAURANT_ID);}cloudStatus="CONECTADO";return;}
            boolean permanent=response.statusCode()>=400&&response.statusCode()<500;String status="Central respondió HTTP "+response.statusCode();for(var row:rows)recordFailure((UUID)row.get("id"),((Number)row.get("intentos")).intValue(),status,permanent);cloudStatus="ERROR_DE_SINCRONIZACION";
        }catch(Exception failure){for(var row:rows)recordFailure((UUID)row.get("id"),((Number)row.get("intentos")).intValue(),"No fue posible conectar con el servidor central",false);cloudStatus="ERROR_DE_SINCRONIZACION";lastCheck=Instant.now();log.warn("Falló el envío del outbox ({} eventos): {}",rows.size(),failure.getClass().getSimpleName());}
    }
    public String cloudStatus(){return cloudStatus;}
    public Instant lastCheck(){return lastCheck;}
    @Transactional public void recordFailure(UUID id,int attempts,String message,boolean permanent){long delay=Math.min(3600L,5L*(1L<<Math.min(attempts,10)));jdbc.update("UPDATE evento_outbox SET estado=?,intentos=intentos+1,ultimo_error=?,siguiente_intento=now()+(? * interval '1 second') WHERE id=? AND estado='PENDIENTE'",permanent?"CONFLICTO":"PENDIENTE",message,delay,id);}
    private Object parse(Object value){try{return mapper.readTree(String.valueOf(value));}catch(Exception e){throw new IllegalStateException("Carga del outbox inválida",e);}}
}
