package mx.mesapp.api;

import static mx.mesapp.api.ApiExceptionHandler.ApiProblem.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import mx.mesapp.config.BootstrapData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("local") @RequestMapping("/api/printer")
public class PrinterController {
    private final JdbcTemplate jdbc;
    private final String token;
    private final AuditService audit;
    public PrinterController(JdbcTemplate jdbc,@Value("${mesapp.printer-token:}")String token,AuditService audit){this.jdbc=jdbc;this.token=token;this.audit=audit;}

    @GetMapping("/jobs") public Object pending(@RequestHeader(value="Authorization",required=false)String auth){authorize(auth);return jdbc.queryForList("SELECT t.id,t.pedido_id,t.estacion,t.estado,t.intentos,t.creado_en,p.tipo,p.mesa_id,p.destino,p.observaciones,p.total FROM trabajo_impresion t JOIN pedido p ON p.id=t.pedido_id WHERE t.restaurante_id=? AND t.estado='PENDIENTE' ORDER BY t.creado_en LIMIT 20",BootstrapData.RESTAURANT_ID);}

    @Transactional @PostMapping("/jobs/claim")
    public Object claim(@RequestHeader(value="Authorization",required=false)String auth){
        authorize(auth);
        jdbc.update("UPDATE trabajo_impresion SET estado='PENDIENTE_VERIFICACION',ultimo_error='El agente no confirmó el resultado antes de vencer el reclamo; verifica antes de reimprimir',reclamo_expira_en=NULL,claim_token=NULL,actualizado_en=now() WHERE restaurante_id=? AND estado='ENVIADO' AND reclamo_expira_en<now()",BootstrapData.RESTAURANT_ID);
        UUID claimToken=UUID.randomUUID();
        var rows=jdbc.queryForList("UPDATE trabajo_impresion SET estado='ENVIADO',claim_token=?,reclamo_expira_en=now()+interval '2 minutes',intentos=intentos+1,actualizado_en=now() WHERE id=(SELECT id FROM trabajo_impresion WHERE restaurante_id=? AND estado='PENDIENTE' ORDER BY creado_en LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING id,pedido_id,estacion,claim_token",claimToken,BootstrapData.RESTAURANT_ID);
        if(rows.isEmpty())return org.springframework.http.ResponseEntity.noContent().build();
        var job=new HashMap<String,Object>(rows.get(0));
        job.putAll(jdbc.queryForMap("SELECT tipo,mesa_id,destino,observaciones,total,creado_en FROM pedido WHERE id=? AND restaurante_id=?",job.get("pedido_id"),BootstrapData.RESTAURANT_ID));
        job.put("items",jdbc.queryForList("SELECT nombre_snapshot AS nombre,cantidad,observaciones FROM detalle_pedido WHERE pedido_id=? ORDER BY id",job.get("pedido_id")));
        return job;
    }

    @GetMapping("/admin/jobs") public Object adminJobs(HttpSession session){AuthController.require(session,"ADMINISTRADOR");return jdbc.queryForList("SELECT t.id,t.pedido_id,t.estacion,t.estado,t.intentos,t.ultimo_error,t.creado_en,t.actualizado_en,t.reclamo_expira_en FROM trabajo_impresion t WHERE t.restaurante_id=? ORDER BY t.creado_en DESC LIMIT 100",BootstrapData.RESTAURANT_ID);}

    @PostMapping("/jobs/{id}/result")
    public Object result(@PathVariable UUID id,@RequestHeader(value="Authorization",required=false)String auth,@Valid @RequestBody Result body){
        authorize(auth);
        if(!Set.of("ENVIADO","CONFIRMADO","FALLIDO","PENDIENTE_VERIFICACION").contains(body.estado()))throw bad("Estado de impresión inválido");
        int n=jdbc.update("UPDATE trabajo_impresion SET estado=?,ultimo_error=?,reclamo_expira_en=NULL,claim_token=NULL,actualizado_en=now() WHERE id=? AND restaurante_id=? AND estado='ENVIADO' AND claim_token=?",body.estado(),body.error(),id,BootstrapData.RESTAURANT_ID,body.claimToken());
        if(n==0)throw conflict("El reclamo expiró o ya se registró otro resultado para este trabajo");
        return Map.of("registrado",true);
    }

    @PostMapping("/jobs/{id}/retry")
    public Object retry(@PathVariable UUID id,HttpSession session){
        AuthController.require(session,"ADMINISTRADOR");
        int n=jdbc.update("UPDATE trabajo_impresion SET estado='PENDIENTE',claim_token=NULL,reclamo_expira_en=NULL,ultimo_error=NULL,actualizado_en=now() WHERE id=? AND restaurante_id=? AND estado IN ('FALLIDO','PENDIENTE_VERIFICACION')",id,BootstrapData.RESTAURANT_ID);
        if(n==0)throw conflict("El trabajo está en cola o se debe esperar a que venza el reclamo activo");
        audit.record(session,"REIMPRESION_SOLICITADA",id,Map.of());
        return Map.of("encolado",true);
    }

    @GetMapping("/stations") public Object stations(HttpSession session){AuthController.require(session,"ADMINISTRADOR");return jdbc.queryForList("SELECT id,codigo,nombre,activa FROM estacion_impresion WHERE restaurante_id=? ORDER BY nombre",BootstrapData.RESTAURANT_ID);}
    @PostMapping("/stations") public Object createStation(@Valid @RequestBody Station body,HttpSession session){AuthController.require(session,"ADMINISTRADOR");UUID id=UUID.randomUUID();jdbc.update("INSERT INTO estacion_impresion(id,restaurante_id,codigo,nombre) VALUES (?,?,?,?)",id,BootstrapData.RESTAURANT_ID,body.codigo().trim().toUpperCase(Locale.ROOT),body.nombre().trim());audit.record(session,"ESTACION_IMPRESION_CREADA",id,Map.of("codigo",body.codigo().trim().toUpperCase(Locale.ROOT)));return Map.of("id",id,"codigo",body.codigo().trim().toUpperCase(Locale.ROOT),"nombre",body.nombre().trim(),"activa",true);}
    @PatchMapping("/stations/{id}") public Object updateStation(@PathVariable UUID id,@Valid @RequestBody StationUpdate body,HttpSession session){AuthController.require(session,"ADMINISTRADOR");int n=jdbc.update("UPDATE estacion_impresion SET nombre=?,activa=? WHERE id=? AND restaurante_id=?",body.nombre().trim(),body.activa(),id,BootstrapData.RESTAURANT_ID);if(n==0)throw missing("Estación no encontrada");audit.record(session,"ESTACION_IMPRESION_ACTUALIZADA",id,Map.of("nombre",body.nombre().trim(),"activa",body.activa()));return Map.of("actualizada",true);}
    @GetMapping("/devices") public Object devices(HttpSession session){AuthController.require(session,"ADMINISTRADOR");return jdbc.queryForList("SELECT p.id,p.nombre,p.fabricante,p.modelo,p.adaptador,p.sistema,p.activa,p.estacion_id,e.codigo AS estacion FROM impresora p JOIN estacion_impresion e ON e.id=p.estacion_id WHERE p.restaurante_id=? ORDER BY e.codigo,p.nombre",BootstrapData.RESTAURANT_ID);}
    @PostMapping("/devices") public Object createDevice(@Valid @RequestBody Device body,HttpSession session){AuthController.require(session,"ADMINISTRADOR");if(jdbc.queryForObject("SELECT count(*) FROM estacion_impresion WHERE id=? AND restaurante_id=? AND activa",Integer.class,body.estacionId(),BootstrapData.RESTAURANT_ID)==0)throw bad("Estación inválida");UUID id=UUID.randomUUID();jdbc.update("INSERT INTO impresora(id,restaurante_id,estacion_id,nombre,fabricante,modelo,adaptador,sistema) VALUES (?,?,?,?,?,?,?,?)",id,BootstrapData.RESTAURANT_ID,body.estacionId(),body.nombre().trim(),body.fabricante(),body.modelo(),body.adaptador().trim().toUpperCase(Locale.ROOT),body.sistema());audit.record(session,"IMPRESORA_CREADA",id,Map.of("estacionId",body.estacionId(),"adaptador",body.adaptador().trim().toUpperCase(Locale.ROOT)));return Map.of("id",id,"nombre",body.nombre().trim());}
    @PatchMapping("/devices/{id}") public Object updateDevice(@PathVariable UUID id,@Valid @RequestBody DeviceUpdate body,HttpSession session){AuthController.require(session,"ADMINISTRADOR");if(jdbc.queryForObject("SELECT count(*) FROM estacion_impresion WHERE id=? AND restaurante_id=?",Integer.class,body.estacionId(),BootstrapData.RESTAURANT_ID)==0)throw bad("Estación inválida");int n=jdbc.update("UPDATE impresora SET estacion_id=?,nombre=?,fabricante=?,modelo=?,adaptador=?,sistema=?,activa=?,actualizada_en=now() WHERE id=? AND restaurante_id=?",body.estacionId(),body.nombre().trim(),body.fabricante(),body.modelo(),body.adaptador().trim().toUpperCase(Locale.ROOT),body.sistema(),body.activa(),id,BootstrapData.RESTAURANT_ID);if(n==0)throw missing("Impresora no encontrada");audit.record(session,"IMPRESORA_ACTUALIZADA",id,Map.of("adaptador",body.adaptador().trim().toUpperCase(Locale.ROOT),"activa",body.activa()));return Map.of("actualizada",true);}

    private void authorize(String auth){if(token.isBlank()||auth==null||!MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),auth.getBytes(StandardCharsets.UTF_8)))throw forbidden("Agente de impresión no autorizado");}
    public record Result(@NotBlank String estado,@NotNull UUID claimToken,String error){}
    public record Station(@NotBlank @jakarta.validation.constraints.Size(max=100)String codigo,@NotBlank @jakarta.validation.constraints.Size(max=120)String nombre){}
    public record StationUpdate(@NotBlank @jakarta.validation.constraints.Size(max=120)String nombre,boolean activa){}
    public record Device(@NotNull UUID estacionId,@NotBlank @jakarta.validation.constraints.Size(max=120)String nombre,String fabricante,String modelo,@NotBlank String adaptador,String sistema){}
    public record DeviceUpdate(@NotNull UUID estacionId,@NotBlank @jakarta.validation.constraints.Size(max=120)String nombre,String fabricante,String modelo,@NotBlank String adaptador,String sistema,boolean activa){}
}
