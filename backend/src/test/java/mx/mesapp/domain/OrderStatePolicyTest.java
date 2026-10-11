package mx.mesapp.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OrderStatePolicyTest {
    @Test void acceptsOnlyAdjacentPreparationTransitions() {
        assertTrue(OrderStatePolicy.transitionAllowed("CONFIRMADO", "EN_PREPARACION"));
        assertTrue(OrderStatePolicy.transitionAllowed("EN_PREPARACION", "LISTO"));
        assertFalse(OrderStatePolicy.transitionAllowed("CONFIRMADO", "LISTO"));
        assertFalse(OrderStatePolicy.transitionAllowed("ENTREGADO", "EN_PREPARACION"));
    }
    @Test void limitsActionsByRole() {
        assertTrue(OrderStatePolicy.roleAllowed("COCINA", "LISTO"));
        assertFalse(OrderStatePolicy.roleAllowed("COCINA", "CANCELADO"));
        assertTrue(OrderStatePolicy.roleAllowed("DOMICILIARIO", "ENTREGADO"));
        assertFalse(OrderStatePolicy.roleAllowed("DOMICILIARIO", "EN_PREPARACION"));
        assertFalse(OrderStatePolicy.roleAllowed("DESCONOCIDO", "ENTREGADO"));
    }
}
