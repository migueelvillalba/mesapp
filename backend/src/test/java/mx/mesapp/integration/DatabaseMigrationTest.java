package mx.mesapp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import mx.mesapp.config.BootstrapData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker=true)
class DatabaseMigrationTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry properties){properties.add("spring.datasource.url",postgres::getJdbcUrl);properties.add("spring.datasource.username",postgres::getUsername);properties.add("spring.datasource.password",postgres::getPassword);}
    @Autowired JdbcTemplate jdbc;
    @Test void appliesAllMigrationsAndSeedsTwentyThreeTablesOnce(){
        Integer migrations=jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class);
        Integer tables=jdbc.queryForObject("SELECT count(*) FROM mesa WHERE restaurante_id=?",Integer.class,BootstrapData.RESTAURANT_ID);
        assertTrue(migrations>=10);assertEquals(23,tables);
    }
}
