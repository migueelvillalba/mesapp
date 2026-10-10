package mx.mesapp.printer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrintJob(UUID id, UUID pedido_id, String estacion, UUID claim_token, String tipo, UUID mesa_id,
                       String destino, String observaciones, BigDecimal total, List<Item> items) {
    public record Item(String nombre, int cantidad, String observaciones) {}
}
