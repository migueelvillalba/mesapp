package mx.mesapp.api;

import static mx.mesapp.api.ApiExceptionHandler.ApiProblem.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import mx.mesapp.config.BootstrapData;
import mx.mesapp.sync.SyncEventService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;

@RestController
@Profile("local")
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbc; private final BCryptPasswordEncoder encoder; private final AuditService audit;private final SyncEventService syncEvents;
    public AuthController(JdbcTemplate jdbc, BCryptPasswordEncoder encoder, AuditService audit,SyncEventService syncEvents) { this.jdbc=jdbc; this.encoder=encoder; this.audit=audit;this.syncEvents=syncEvents; }
    @GetMapping("/employees") public Object employees(@RequestParam(defaultValue="false") boolean incluirInactivos,HttpSession session) { if(incluirInactivos)require(session,"ADMINISTRADOR");String sql="SELECT id,nombre,rol,activo FROM empleado WHERE restaurante_id=?"+(incluirInactivos?"":" AND activo")+" ORDER BY nombre";return jdbc.queryForList(sql,BootstrapData.RESTAURANT_ID); }
    @PostMapping("/login") public Object login(@Valid @RequestBody Login input, HttpServletRequest request) {
        var rows = jdbc.queryForList("SELECT id,nombre,rol,pin_hash,activo FROM empleado WHERE restaurante_id=? AND lower(nombre)=lower(?)", BootstrapData.RESTAURANT_ID,input.nombre().trim());
        if(rows.isEmpty()) throw unauthorized("Nombre o PIN incorrectos");
        var row=rows.get(0); UUID employee=(UUID)row.get("id");
        Boolean locked=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM intentos_pin WHERE restaurante_id=? AND empleado_id=? AND bloqueado_hasta>now())",Boolean.class,BootstrapData.RESTAURANT_ID,employee);
        if(Boolean.TRUE.equals(locked)) throw new ApiExceptionHandler.ApiProblem(HttpStatus.TOO_MANY_REQUESTS,"Cuenta temporalmente bloqueada por intentos fallidos");
        if(!Boolean.TRUE.equals(row.get("activo")) || !encoder.matches(input.pin(),(String)row.get("pin_hash"))) {
            jdbc.update("INSERT INTO intentos_pin(restaurante_id,empleado_id,intentos,actualizado_en) VALUES (?,?,1,now()) ON CONFLICT(restaurante_id,empleado_id) DO UPDATE SET intentos=intentos_pin.intentos+1, bloqueado_hasta=CASE WHEN intentos_pin.intentos+1>=5 THEN now()+interval '5 minutes' ELSE intentos_pin.bloqueado_hasta END, actualizado_en=now()",BootstrapData.RESTAURANT_ID,employee);
            throw unauthorized("Nombre o PIN incorrectos");
        }
        jdbc.update("DELETE FROM intentos_pin WHERE restaurante_id=? AND empleado_id=?",BootstrapData.RESTAURANT_ID,employee);
        HttpSession session=request.getSession(true); request.changeSessionId();session.setAttribute("employeeId",employee.toString()); session.setAttribute("employeeName",row.get("nombre")); session.setAttribute("role",row.get("rol"));
        audit.record(session,"INICIO_SESION",employee,Map.of());
        return Map.of("id",employee,"nombre",row.get("nombre"),"rol",row.get("rol"));
    }
    @GetMapping("/me") public Object me(HttpSession s) { return Map.of("id",userId(s),"nombre",s.getAttribute("employeeName"),"rol",s.getAttribute("role")); }
    @org.springframework.transaction.annotation.Transactional @PostMapping("/employees") public Object createEmployee(@Valid @RequestBody EmployeeInput input,HttpSession session){require(session,"ADMINISTRADOR");if(!java.util.Set.of("MESERO","COCINA","ADMINISTRADOR","DOMICILIARIO").contains(input.rol()))throw bad("Rol inválido");UUID id=UUID.randomUUID();jdbc.update("INSERT INTO empleado(id,restaurante_id,nombre,rol,pin_hash) VALUES (?,?,?,?,?)",id,BootstrapData.RESTAURANT_ID,input.nombre().trim(),input.rol(),encoder.encode(input.pin()));audit.record(session,"EMPLEADO_CREADO",id,Map.of("rol",input.rol()));syncEvents.record(id,1,"EMPLEADO_ACTUALIZADO",Map.of("id",id,"nombre",input.nombre().trim(),"rol",input.rol(),"activo",true));return Map.of("id",id,"nombre",input.nombre().trim(),"rol",input.rol());}
    @PostMapping("/change-pin") public Object changeOwnPin(@Valid @RequestBody ChangePin input,HttpSession session){UUID id=userId(session);String hash=jdbc.query("SELECT pin_hash FROM empleado WHERE id=? AND restaurante_id=? AND activo",rs->rs.next()?rs.getString(1):null,id,BootstrapData.RESTAURANT_ID);if(hash==null||!encoder.matches(input.pinActual(),hash))throw unauthorized("El PIN actual es incorrecto");jdbc.update("UPDATE empleado SET pin_hash=? WHERE id=? AND restaurante_id=?",encoder.encode(input.pinNuevo()),id,BootstrapData.RESTAURANT_ID);audit.record(session,"PIN_CAMBIADO",id,Map.of());return Map.of("actualizado",true);}
    @PostMapping("/logout") public Object logout(HttpSession s) { s.invalidate(); return Map.of("cerrado",true); }
    static UUID userId(HttpSession s) { Object value=s.getAttribute("employeeId"); if(value==null) throw unauthorized("Inicia sesión para continuar"); return UUID.fromString(value.toString()); }
    static String role(HttpSession s) { Object value=s.getAttribute("role"); if(value==null) throw unauthorized("Inicia sesión para continuar"); return value.toString(); }
    static void require(HttpSession s,String... roles) { String role=role(s); if(java.util.Arrays.stream(roles).noneMatch(role::equals)) throw forbidden("Tu rol no puede realizar esta operación"); }
    public record Login(@NotBlank String nombre,@NotBlank @Pattern(regexp="\\d{4,12}") String pin) {}
    public record EmployeeInput(@NotBlank @jakarta.validation.constraints.Size(max=120)String nombre,@NotBlank String rol,@NotBlank @Pattern(regexp="\\d{4,12}")String pin){}
    public record ChangePin(@NotBlank @Pattern(regexp="\\d{4,12}")String pinActual,@NotBlank @Pattern(regexp="\\d{4,12}")String pinNuevo){}
}
