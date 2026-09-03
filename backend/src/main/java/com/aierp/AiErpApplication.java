package com.aierp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@Modulithic(sharedModules = "platform")
public class AiErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiErpApplication.class, args);
    }
}
