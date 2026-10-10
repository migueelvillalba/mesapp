package mx.mesapp.api;

import static mx.mesapp.api.ApiExceptionHandler.ApiProblem.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import mx.mesapp.config.BootstrapData;
import mx.mesapp.sync.SyncEventService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local")
@RequestMapping("/api")
public class AdministrationController {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder;
    private final AuditService audit;
    private final SyncEventService syncEvents;
    public AdministrationController(JdbcTemplate jdbc, BCryptPasswordEncoder encoder, AuditService audit,SyncEventService syncEvents) { this.jdbc=jdbc; this.encoder=encoder; this.audit=audit;this.syncEvents=syncEvents; }

    @Transactional @PutMapping("/tables/{id}")
    public Object editTable(@PathVariable UUID id,@Valid @RequestBody TableEdit body,HttpSession session) {
        AuthController.require(session,"ADMINISTRADOR");
        Integer previous=jdbc.query("SELECT numero FROM mesa WHERE id=? AND restaurante_id=? FOR UPDATE",rs->rs.next()?rs.getInt(1):null,id,BootstrapData.RESTAURANT_ID);
        if(previous==null)throw missing("Mesa no encontrada");
        if(!body.activa()&&jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pedido WHERE mesa_id=? AND estado IN ('CONFIRMADO','EN_PREPARACION','LISTO'))",Boolean.class,id))throw conflict("No se puede desactivar una mesa con un pedido activo");
        jdbc.update("UPDATE mesa SET numero=?,activa=?,version=version+1 WHERE id=? AND restaurante_id=?",body.numero(),body.activa(),id,BootstrapData.RESTAURANT_ID);
        audit.record(session,"MESA_EDITADA",id,Map.of("numeroAnterior",previous,"numeroNuevo",body.numero(),"activa",body.activa()));
        long version=jdbc.queryForObject("SELECT version FROM mesa WHERE id=?",Long.class,id);syncEvents.record(id,version,"MESA_ACTUALIZADA",Map.of("id",id,"numero",body.numero(),"activa",body.activa()));
        return Map.of("actualizada",true);
    }

    @Transactional @PutMapping("/products/{id}")
    public Object editProduct(@PathVariable UUID id,@Valid @RequestBody ProductEdit body,HttpSession session) {
        AuthController.require(session,"ADMINISTRADOR");
        if(jdbc.queryForObject("SELECT count(*) FROM categoria WHERE id=? AND restaurante_id=? AND activa",Integer.class,body.categoriaId(),BootstrapData.RESTAURANT_ID)==0)throw bad("Categoría inválida");
        var old=jdbc.query("SELECT nombre,precio,disponible,activo FROM producto WHERE id=? AND restaurante_id=? FOR UPDATE",rs->rs.next()?Map.of("nombre",rs.getString("nombre"),"precio",rs.getBigDecimal("precio"),"disponible",rs.getBoolean("disponible"),"activo",rs.getBoolean("activo")):null,id,BootstrapData.RESTAURANT_ID);
        if(old==null)throw missing("Producto no encontrado");
        jdbc.update("UPDATE producto SET categoria_id=?,nombre=?,descripcion=?,precio=?,disponible=?,activo=?,actualizado_en=now(),version=version+1 WHERE id=? AND restaurante_id=?",body.categoriaId(),body.nombre().trim(),body.descripcion(),body.precio(),body.disponible(),body.activo(),id,BootstrapData.RESTAURANT_ID);
        audit.record(session,"PRODUCTO_EDITADO",id,Map.of("anterior",old,"nombre",body.nombre().trim(),"precio",body.precio(),"disponible",body.disponible(),"activo",body.activo()));
        long version=jdbc.queryForObject("SELECT version FROM producto WHERE id=?",Long.class,id);syncEvents.record(id,version,"PRODUCTO_ACTUALIZADO",Map.of("id",id,"categoriaId",body.categoriaId(),"nombre",body.nombre().trim(),"descripcion",body.descripcion()==null?"":body.descripcion(),"precio",body.precio(),"disponible",body.disponible(),"activo",body.activo()));
        return Map.of("actualizado",true);
    }

    @Transactional @DeleteMapping("/products/{id}")
    public Object deactivateProduct(@PathVariable UUID id,HttpSession session) {
        AuthController.require(session,"ADMINISTRADOR");
        int n=jdbc.update("UPDATE producto SET activo=false,disponible=false,actualizado_en=now(),version=version+1 WHERE id=? AND restaurante_id=? AND activo",id,BootstrapData.RESTAURANT_ID);
        if(n==0)throw missing("Producto no encontrado o ya inactivo");
        audit.record(session,"PRODUCTO_DESACTIVADO",id,Map.of());
        long version=jdbc.queryForObject("SELECT version FROM producto WHERE id=?",Long.class,id);syncEvents.record(id,version,"PRODUCTO_ACTUALIZADO",Map.of("id",id,"activo",false,"disponible",false));
        return Map.of("desactivado",true);
    }

    @Transactional @PatchMapping("/categories/{id}")
    public Object editCategory(@PathVariable UUID id,@Valid @RequestBody CategoryEdit body,HttpSession session) {
        AuthController.require(session,"ADMINISTRADOR");
        if(!body.activa()&&jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM producto WHERE categoria_id=? AND activo)",Boolean.class,id))throw conflict("Desactiva primero los productos activos de esta categoría");
        int n=jdbc.update("UPDATE categoria SET nombre=?,activa=?,version=version+1 WHERE id=? AND restaurante_id=?",body.nombre().trim(),body.activa(),id,BootstrapData.RESTAURANT_ID);
        if(n==0)throw missing("Categoría no encontrada");
        audit.record(session,"CATEGORIA_EDITADA",id,Map.of("nombre",body.nombre().trim(),"activa",body.activa()));
        long version=jdbc.queryForObject("SELECT version FROM categoria WHERE id=?",Long.class,id);syncEvents.record(id,version,"CATEGORIA_ACTUALIZADA",Map.of("id",id,"nombre",body.nombre().trim(),"activa",body.activa()));
        return Map.of("actualizada",true);
    }

    @Transactional @PatchMapping("/auth/employees/{id}")
    public Object editEmployee(@PathVariable UUID id,@Valid @RequestBody EmployeeEdit body,HttpSession session) {
        AuthController.require(session,"ADMINISTRADOR");
        if(!Set.of("MESERO","COCINA","ADMINISTRADOR","DOMICILIARIO").contains(body.rol()))throw bad("Rol inválido");
        if(!body.activo()&&id.equals(AuthController.userId(session)))throw conflict("No puedes desactivar tu propio usuario mientras tienes sesión activa");
        String previousRole=jdbc.query("SELECT rol FROM empleado WHERE id=? AND restaurante_id=? FOR UPDATE",rs->rs.next()?rs.getString(1):null,id,BootstrapData.RESTAURANT_ID);
        if(previousRole==null)throw missing("Empleado no encontrado");
        if(previousRole.equals("ADMINISTRADOR")&&(!body.activo()||!body.rol().equals("ADMINISTRADOR"))&&jdbc.queryForObject("SELECT count(*) FROM empleado WHERE restaurante_id=? AND rol='ADMINISTRADOR' AND activo",Integer.class,BootstrapData.RESTAURANT_ID)<=1)throw conflict("El restaurante debe conservar al menos un administrador activo");
        jdbc.update("UPDATE empleado SET nombre=?,rol=?,activo=?,version=version+1 WHERE id=? AND restaurante_id=?",body.nombre().trim(),body.rol(),body.activo(),id,BootstrapData.RESTAURANT_ID);
        audit.record(session,"EMPLEADO_EDITADO",id,Map.of("rolAnterior",previousRole,"rol",body.rol(),"activo",body.activo()));
        long version=jdbc.queryForObject("SELECT version FROM empleado WHERE id=?",Long.class,id);syncEvents.record(id,version,"EMPLEADO_ACTUALIZADO",Map.of("id",id,"nombre",body.nombre().trim(),"rol",body.rol(),"activo",body.activo()));
        return Map.of("actualizado",true);
    }

    @PostMapping("/auth/employees/{id}/pin")
    public Object resetEmployeePin(@PathVariable UUID id,@Valid @RequestBody Pin body,HttpSession session) {
        AuthController.require(session,"ADMINISTRADOR");
        int n=jdbc.update("UPDATE empleado SET pin_hash=? WHERE id=? AND restaurante_id=?",encoder.encode(body.pin()),id,BootstrapData.RESTAURANT_ID);
        if(n==0)throw missing("Empleado no encontrado");
        jdbc.update("DELETE FROM intentos_pin WHERE restaurante_id=? AND empleado_id=?",BootstrapData.RESTAURANT_ID,id);
        audit.record(session,"PIN_REINICIADO",id,Map.of());
        return Map.of("actualizado",true);
    }


    @GetMapping("/orders/{id}/history")
    public Object orderHistory(@PathVariable UUID id,HttpSession session) {
        String role=AuthController.role(session);
        AuthController.require(session,"MESERO","COCINA","ADMINISTRADOR","DOMICILIARIO");
        if(jdbc.queryForObject("SELECT count(*) FROM pedido WHERE id=? AND restaurante_id=?",Integer.class,id,BootstrapData.RESTAURANT_ID)==0)throw missing("Pedido no encontrado");
        if(role.equals("MESERO")&&!jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pedido WHERE id=? AND empleado_id=?)",Boolean.class,id,AuthController.userId(session)))throw forbidden("Este pedido no está asignado a tu usuario");
        if(role.equals("DOMICILIARIO")&&!jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM asignacion_domiciliario WHERE pedido_id=? AND empleado_id=?)",Boolean.class,id,AuthController.userId(session)))throw forbidden("Este domicilio no está asignado a tu usuario");
        return jdbc.queryForList("SELECT h.estado_anterior,h.estado_nuevo,h.motivo,h.creado_en,e.nombre AS empleado FROM historial_estado_pedido h JOIN empleado e ON e.id=h.empleado_id WHERE h.pedido_id=? ORDER BY h.creado_en,h.id",id);
    }

    @GetMapping("/orders/{id}/assignments")
    public Object deliveryHistory(@PathVariable UUID id,HttpSession session) {
        AuthController.require(session,"MESERO","ADMINISTRADOR","DOMICILIARIO");
        if(jdbc.queryForObject("SELECT count(*) FROM pedido WHERE id=? AND restaurante_id=? AND tipo='DOMICILIO'",Integer.class,id,BootstrapData.RESTAURANT_ID)==0)throw missing("Domicilio no encontrado");
        if(AuthController.role(session).equals("DOMICILIARIO")&&!jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM asignacion_domiciliario WHERE pedido_id=? AND empleado_id=?)",Boolean.class,id,AuthController.userId(session)))throw forbidden("Este domicilio no está asignado a tu usuario");
        return jdbc.queryForList("SELECT a.id,a.asignado_en,a.finalizado_en,e.nombre AS domiciliario,asignador.nombre AS asignado_por FROM asignacion_domiciliario a JOIN empleado e ON e.id=a.empleado_id JOIN empleado asignador ON asignador.id=a.asignado_por WHERE a.pedido_id=? ORDER BY a.asignado_en DESC",id);
    }

    @GetMapping("/audit")
    public Object auditTrail(@RequestParam(defaultValue="0") @Min(0) int page,HttpSession session) {
        AuthController.require(session,"ADMINISTRADOR");
        return jdbc.queryForList("SELECT a.id,a.accion,a.recurso_id,a.detalles,a.creado_en,e.nombre AS empleado FROM registro_auditoria a LEFT JOIN empleado e ON e.id=a.empleado_id WHERE a.restaurante_id=? ORDER BY a.creado_en DESC LIMIT 100 OFFSET ?",BootstrapData.RESTAURANT_ID,Math.min(page,1000)*100);
    }

    public record TableEdit(@Min(1) int numero,boolean activa) {}
    public record ProductEdit(@NotNull UUID categoriaId,@NotBlank @Size(max=160) String nombre,@Size(max=500) String descripcion,@NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal precio,boolean disponible,boolean activo) {}
    public record CategoryEdit(@NotBlank @Size(max=100) String nombre,boolean activa) {}
    public record EmployeeEdit(@NotBlank @Size(max=120) String nombre,@NotBlank String rol,boolean activo) {}
    public record Pin(@NotBlank @Pattern(regexp="\\d{4,12}") String pin) {}
}
