package com.thinkordrinkpoetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PoetrySiteApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(PoetrySiteApplication.class, args);
        if (context.getEnvironment().getProperty("app.legacy-import.enabled", Boolean.class, false)
                || context.getEnvironment().getProperty("app.legacy-outreach.enabled", Boolean.class, false)) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
