package com.fitflex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the FitFlex REST API. {@code @SpringBootApplication} turns on auto-configuration
 * and scans every class under {@code com.fitflex} for controllers, services and repositories.
 */
@SpringBootApplication
public class FitFlexApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitFlexApplication.class, args);
    }
}
