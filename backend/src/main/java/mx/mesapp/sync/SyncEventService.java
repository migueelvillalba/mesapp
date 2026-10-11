package mx.mesapp.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import mx.mesapp.config.BootstrapData;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Writes immutable domain snapshots to the local outbox in the caller's transaction. */
@Service @Profile("local")
public class SyncEventService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public SyncEventService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    public void record(UUID entityId,long sequence,String type,Object payload){
        try {
            String json=mapper.writeValueAsString(payload);
            jdbc.update("INSERT INTO evento_outbox(id,restaurante_id,instalacion_id,tipo,entidad_id,secuencia,payload) VALUES (?,?,?,?,?,?,?::jsonb)",UUID.randomUUID(),BootstrapData.RESTAURANT_ID,BootstrapData.INSTALLATION_ID,type,entityId,sequence,json);
        } catch(Exception error){throw new IllegalStateException("No se pudo registrar la sincronización local",error);}
    }
}
