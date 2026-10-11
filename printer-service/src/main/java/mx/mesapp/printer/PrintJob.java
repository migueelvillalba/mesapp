package mx.mesapp.printer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrintJob(UUID id, UUID pedido_id, String estacion, UUID claim_token, String tipo, UUID mesa_id,
                       Integer mesa_numero, String mesero, String modalidad_servicio, String destino,
                       String observaciones, BigDecimal total, String creado_en, List<Item> items) {
    public PrintJob(UUID id, UUID pedido_id, String estacion, UUID claim_token, String tipo, UUID mesa_id,
                    String destino, String observaciones, BigDecimal total, List<Item> items) {
        this(id, pedido_id, estacion, claim_token, tipo, mesa_id, null, null,
            "DOMICILIO".equals(tipo) ? "DOMICILIO" : "SERVIRSE", destino, observaciones, total, null, items);
    }
    public String fechaLocal() {
        if (creado_en == null || creado_en.isBlank()) return "Fecha no disponible";
        try { return OffsetDateTime.parse(creado_en).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX"))); }
        catch (Exception ignored) { return creado_en; }
    }
    public record Item(String nombre, int cantidad, BigDecimal precio, String observaciones) {
        public Item(String nombre, int cantidad, String observaciones) { this(nombre, cantidad, BigDecimal.ZERO, observaciones); }
    }
}
