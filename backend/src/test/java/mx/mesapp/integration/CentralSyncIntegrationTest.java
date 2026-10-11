package mx.mesapp.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import mx.mesapp.config.BootstrapData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("central") @Testcontainers(disabledWithoutDocker=true)
class CentralSyncIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);p.add("mesapp.sync-token",()->"test-sync-token");}
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void receiptIsIdempotentAndProjectionKeepsTheHighestSequence() throws Exception {
        String restaurant=BootstrapData.RESTAURANT_ID.toString(),installation=BootstrapData.INSTALLATION_ID.toString(),order="33333333-3333-4333-8333-333333333333";
        String first=event("44444444-4444-4444-8444-444444444444",restaurant,installation,order,1,"CONFIRMADO");
        mvc.perform(post("/api/sync/events").header("Authorization","Bearer test-sync-token").contentType(MediaType.APPLICATION_JSON).content(first)).andExpect(status().isOk()).andExpect(jsonPath("$.resultados[0].estado").value("ACEPTADO"));
        mvc.perform(post("/api/sync/events").header("Authorization","Bearer test-sync-token").contentType(MediaType.APPLICATION_JSON).content(first)).andExpect(status().isOk()).andExpect(jsonPath("$.resultados[0].estado").value("DUPLICADO"));
        String second=event("55555555-5555-4555-8555-555555555555",restaurant,installation,order,2,"LISTO");
        mvc.perform(post("/api/sync/events").header("Authorization","Bearer test-sync-token").contentType(MediaType.APPLICATION_JSON).content(second)).andExpect(status().isOk()).andExpect(jsonPath("$.resultados[0].estado").value("ACEPTADO"));
        String late=event("66666666-6666-4666-8666-666666666666",restaurant,installation,order,1,"CONFIRMADO");
        mvc.perform(post("/api/sync/events").header("Authorization","Bearer test-sync-token").contentType(MediaType.APPLICATION_JSON).content(late)).andExpect(status().isOk()).andExpect(jsonPath("$.resultados[0].estado").value("ANTERIOR"));
        String conflict=event("77777777-7777-4777-8777-777777777777",restaurant,installation,order,2,"ENTREGADO");
        mvc.perform(post("/api/sync/events").header("Authorization","Bearer test-sync-token").contentType(MediaType.APPLICATION_JSON).content(conflict)).andExpect(status().isOk()).andExpect(jsonPath("$.resultados[0].estado").value("CONFLICTO"));
        org.junit.jupiter.api.Assertions.assertEquals(2,jdbc.queryForObject("SELECT secuencia FROM pedido_sincronizado WHERE restaurante_id=? AND pedido_id=?",Integer.class,BootstrapData.RESTAURANT_ID,java.util.UUID.fromString(order)));
        org.junit.jupiter.api.Assertions.assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM historial_evento_sincronizado WHERE restaurante_id=? AND pedido_id=? AND NOT aplicado",Integer.class,BootstrapData.RESTAURANT_ID,java.util.UUID.fromString(order)));
    }

    private static String event(String eventId,String restaurant,String installation,String order,long sequence,String state) {
        return """
            [{"id":"%s","restauranteId":"%s","instalacionId":"%s","tipo":"PEDIDO_ESTADO_CAMBIADO","entidadId":"%s","secuencia":%d,"creadoEn":"2026-10-10T12:00:00Z","payload":{"id":"%s","tipo":"MESA","estado":"%s","items":[]}}]
            """.formatted(eventId,restaurant,installation,order,sequence,order,state);
    }
}
