package com.tschanz.aigeny;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

@SpringBootApplication
@EnableAsync
public class AigenyApplication {
    public static void main(String[] args) {
        SpringApplication.run(AigenyApplication.class, args);
    }

    /** Prints a friendly "ready" banner with the local URL once the app has fully started. */
    @Component
    static class ReadyBanner {
        private final Environment env;

        ReadyBanner(Environment env) {
            this.env = env;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void onReady() {
            String port = env.getProperty("server.port", "8080");
            String url  = "http://localhost:" + port;
            System.out.println();
            System.out.println("==========================================");
            System.out.println("  AIgeny is ready!");
            System.out.println("  Open a browser at " + url);
            System.out.println("==========================================");
            System.out.println();
        }
    }
}

