package com.ramonnnarokomo.hucha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HuchaApplication {

    public static void main(String[] args) {
        SpringApplication.run(HuchaApplication.class, args);
    }
}
