package mx.mesapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MesAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(MesAppApplication.class, args);
    }
}
