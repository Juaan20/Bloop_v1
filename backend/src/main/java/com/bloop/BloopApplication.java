package com.bloop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de Bloop: un monolito modular Spring Boot.
 * Cada paquete de primer nivel (venues, events, orders...) es un modulo funcional
 * con sus capas controller / dto / entity / repository / service / mapper.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class BloopApplication {

    public static void main(String[] args) {
        SpringApplication.run(BloopApplication.class, args);
    }
}
