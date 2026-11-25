package com.fitflex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * {@link RestClient}s for calling external APIs.
 * - externalApiClient: short timeouts (Spoonacular, Google, Nominatim). If one of these hangs we
 *   want to switch to the fallback quickly instead of making the user wait.
 * - overpassRestClient: a longer read timeout, because the free Overpass servers often need
 *   15-25 seconds to answer when they are busy, and a slow answer is better than none.
 */
@Configuration
public class RestClientConfig {

    /** Public APIs like Overpass and Nominatim ask clients to identify themselves. */
    public static final String USER_AGENT = "FitFlex/1.0 (student project)";

    @Bean
    @Primary
    public RestClient externalApiClient() {
        return build(Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    @Bean
    public RestClient overpassRestClient() {
        return build(Duration.ofSeconds(5), Duration.ofSeconds(25));
    }

    /** Builds a RestClient with the given timeouts; public so tests can make one too. */
    public static RestClient build(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }
}
