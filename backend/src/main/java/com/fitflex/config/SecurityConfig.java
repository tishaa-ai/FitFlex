package com.fitflex.config;

import com.fitflex.security.CustomUserDetailsService;
import com.fitflex.security.JwtAuthFilter;
import com.fitflex.security.JwtUtil;
import com.fitflex.security.RestAuthErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security setup: stateless JWT authentication, which URLs are public, which roles may
 * call which URLs, CORS, and BCrypt for passwords. Read the rules top to bottom: the first
 * matching rule wins.
 */
@Configuration
@EnableMethodSecurity // allows @PreAuthorize("hasRole('TRAINER')") on controller methods
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtUtil jwtUtil,
                                                   CustomUserDetailsService userDetailsService,
                                                   RestAuthErrorHandler authErrorHandler) throws Exception {
        http
                // CSRF attacks abuse cookies. We use a token in a header instead of cookies, so CSRF is off.
                .csrf(AbstractHttpConfigurer::disable)
                // Uses the CorsConfigurationSource bean from CorsConfig.
                .cors(Customizer.withDefaults())
                // No HTTP session: every request must prove who it is with its JWT.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // The H2 console is shown inside an iframe from the same origin.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authErrorHandler)   // 401 JSON
                        .accessDeniedHandler(authErrorHandler))       // 403 JSON
                .authorizeHttpRequests(auth -> auth
                        // ----- public endpoints -----
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/user/register", "/api/user/login",
                                "/api/trainer/add", "/api/trainer/login",
                                "/api/contact").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/trainers", "/api/trainers/**", "/api/gym/nearby").permitAll()
                        .requestMatchers("/h2-console/**", "/error").permitAll()

                        // ----- role-specific endpoints -----
                        .requestMatchers("/api/user/**", "/api/dashboard/user").hasRole("USER")
                        .requestMatchers("/api/trainer/**", "/api/dashboard/trainer").hasRole("TRAINER")
                        .requestMatchers("/api/diet/**", "/api/progress/**").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/sessions/book", "/api/sessions/*/join").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/sessions/group", "/api/workout-plans", "/api/resources").hasRole("TRAINER")

                        // ----- everything else just needs a valid login -----
                        .anyRequest().authenticated())
                // Run our JWT check before Spring's username/password login filter.
                .addFilterBefore(new JwtAuthFilter(jwtUtil, userDetailsService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** BCrypt hashes passwords with a random salt, so equal passwords get different hashes. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
