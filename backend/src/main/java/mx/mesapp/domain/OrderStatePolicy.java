package mx.mesapp.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-side transition and actor policy for an order. */
public final class OrderStatePolicy {
    private static final Map<String, List<String>> TRANSITIONS = Map.of(
        "BORRADOR", List.of("CONFIRMADO", "CANCELADO"),
        "CONFIRMADO", List.of("EN_PREPARACION", "CANCELADO"),
        "EN_PREPARACION", List.of("LISTO", "CANCELADO"),
        "LISTO", List.of("ENTREGADO")
    );
    private OrderStatePolicy() {}
    public static boolean transitionAllowed(String from, String to) { return TRANSITIONS.getOrDefault(from, List.of()).contains(to); }
    public static boolean roleAllowed(String role, String to) {
        return switch (role) {
            case "ADMINISTRADOR", "MESERO" -> Set.of("CONFIRMADO", "EN_PREPARACION", "LISTO", "ENTREGADO", "CANCELADO").contains(to);
            case "COCINA" -> Set.of("EN_PREPARACION", "LISTO").contains(to);
            case "DOMICILIARIO" -> "ENTREGADO".equals(to);
            default -> false;
        };
    }
}
