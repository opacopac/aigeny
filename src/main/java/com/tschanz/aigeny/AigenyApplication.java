package com.tschanz.aigeny;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AigenyApplication {
    public static void main(String[] args) {
        SpringApplication.run(AigenyApplication.class, args);
    }
}

