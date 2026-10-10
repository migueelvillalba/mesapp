package mx.mesapp.config;

import java.util.UUID;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class BootstrapData {
    public static final UUID RESTAURANT_ID = UUID.fromString(env("MESAPP_RESTAURANT_ID", "11111111-1111-4111-8111-111111111111"));
    public static final UUID INSTALLATION_ID = UUID.fromString(env("MESAPP_INSTALLATION_ID", "22222222-2222-4222-8222-222222222222"));

    @Bean @Profile("local") ApplicationRunner initializeLocalRestaurant(JdbcTemplate jdbc, BCryptPasswordEncoder encoder) {
        return args -> {
            jdbc.update("INSERT INTO restaurante(id,nombre) VALUES (?,?) ON CONFLICT(id) DO NOTHING", RESTAURANT_ID, env("MESAPP_RESTAURANT_NAME", "Restaurante MesApp"));
            jdbc.update("INSERT INTO instalacion(id,restaurante_id,nombre) VALUES (?,?,?) ON CONFLICT(id) DO NOTHING", INSTALLATION_ID, RESTAURANT_ID, env("MESAPP_INSTALLATION_NAME", "Instalación local"));
            for (int numero = 1; numero <= Integer.parseInt(env("MESAPP_INITIAL_TABLES", "23")); numero++) {
                jdbc.update("INSERT INTO mesa(id,restaurante_id,numero) VALUES (?,?,?) ON CONFLICT(restaurante_id,numero) DO NOTHING", UUID.nameUUIDFromBytes((RESTAURANT_ID + ":mesa:" + numero).getBytes()), RESTAURANT_ID, numero);
            }
            UUID kitchenStation=UUID.nameUUIDFromBytes((RESTAURANT_ID+":estacion:COCINA").getBytes());
            jdbc.update("INSERT INTO estacion_impresion(id,restaurante_id,codigo,nombre) VALUES (?,?,?,?) ON CONFLICT(restaurante_id,codigo) DO NOTHING",kitchenStation,RESTAURANT_ID,"COCINA","Cocina");
            jdbc.update("INSERT INTO impresora(id,restaurante_id,estacion_id,nombre,fabricante,modelo,adaptador,sistema,activa) VALUES (?,?,?,?,?,?,?,?,true) ON CONFLICT(restaurante_id,estacion_id,nombre) DO NOTHING",UUID.nameUUIDFromBytes((RESTAURANT_ID+":impresora:simulada").getBytes()),RESTAURANT_ID,kitchenStation,"Agente simulado","MesApp","Desarrollo","SIMULADO","Windows");
            String adminName = System.getenv("MESAPP_BOOTSTRAP_ADMIN_NAME");
            String adminPin = System.getenv("MESAPP_BOOTSTRAP_ADMIN_PIN");
            Integer count = jdbc.queryForObject("SELECT count(*) FROM empleado WHERE restaurante_id=?", Integer.class, RESTAURANT_ID);
            if (count != null && count == 0 && ((adminName != null && !adminName.isBlank()) || (adminPin != null && !adminPin.isBlank()))) {
                if(adminName==null||adminName.isBlank()||adminPin==null||!adminPin.matches("\\d{4,12}"))throw new IllegalStateException("Configura MESAPP_BOOTSTRAP_ADMIN_NAME y un MESAPP_BOOTSTRAP_ADMIN_PIN de 4 a 12 dígitos para inicializar el primer empleado.");
                jdbc.update("INSERT INTO empleado(id,restaurante_id,nombre,rol,pin_hash) VALUES (?,?,?,?,?)", UUID.randomUUID(), RESTAURANT_ID, adminName.trim(), "ADMINISTRADOR", encoder.encode(adminPin));
            }
        };
    }
    @Bean @Profile("central") ApplicationRunner provisionCentralTenant(JdbcTemplate jdbc,ObjectMapper mapper,@Value("${mesapp.sync-token:}")String defaultToken,@Value("${mesapp.sync-tenants:}")String configuredTenants) {
        return args -> {
            List<SyncTenant> tenants;
            if(configuredTenants==null||configuredTenants.isBlank()) {
                if(defaultToken==null||defaultToken.isBlank())throw new IllegalStateException("Configura MESAPP_SYNC_TOKEN o MESAPP_SYNC_TENANTS antes de iniciar el servicio central.");
                tenants=List.of(new SyncTenant(RESTAURANT_ID,INSTALLATION_ID,env("MESAPP_RESTAURANT_NAME","Restaurante MesApp"),env("MESAPP_INSTALLATION_NAME","Instalación local"),defaultToken));
            } else try { tenants=mapper.readValue(configuredTenants,new TypeReference<List<SyncTenant>>(){}); }
            catch(Exception e){throw new IllegalStateException("MESAPP_SYNC_TENANTS debe ser un arreglo JSON de restaurantes, instalaciones y tokens.",e);}
            for(SyncTenant tenant:tenants) {
                if(tenant.restaurantId()==null||tenant.installationId()==null||tenant.token()==null||tenant.token().isBlank())throw new IllegalStateException("Cada entrada MESAPP_SYNC_TENANTS requiere restaurantId, installationId y token.");
                String restaurantName=tenant.restaurantName()==null||tenant.restaurantName().isBlank()?"Restaurante MesApp":tenant.restaurantName();
                String installationName=tenant.installationName()==null||tenant.installationName().isBlank()?"Instalación local":tenant.installationName();
                jdbc.update("INSERT INTO restaurante(id,nombre) VALUES (?,?) ON CONFLICT(id) DO UPDATE SET nombre=EXCLUDED.nombre",tenant.restaurantId(),restaurantName);
                jdbc.update("INSERT INTO instalacion(id,restaurante_id,nombre) VALUES (?,?,?) ON CONFLICT(id) DO UPDATE SET nombre=EXCLUDED.nombre",tenant.installationId(),tenant.restaurantId(),installationName);
                jdbc.update("INSERT INTO credencial_instalacion(restaurante_id,instalacion_id,token_sha256) VALUES (?,?,?) ON CONFLICT(restaurante_id,instalacion_id) DO UPDATE SET token_sha256=EXCLUDED.token_sha256,activa=true",tenant.restaurantId(),tenant.installationId(),sha256(tenant.token()));
            }
        };
    }
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record SyncTenant(UUID restaurantId,UUID installationId,String restaurantName,String installationName,String token){}
    private static String env(String key, String fallback) { String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value; }
}
