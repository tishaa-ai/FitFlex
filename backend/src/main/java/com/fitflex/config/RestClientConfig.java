package com.fitflex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * One shared {@link RestClient} for calling external APIs (Spoonacular, Google Places, Overpass).
 * Short timeouts matter: if an external API hangs, we want to switch to the fallback quickly
 * instead of making the user wait.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient externalApiClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .requestFactory(factory)
                // Public APIs like Overpass ask clients to identify themselves.
                .defaultHeader(HttpHeaders.USER_AGENT, "FitFlex/1.0 (student project)")
                .build();
    }
}
