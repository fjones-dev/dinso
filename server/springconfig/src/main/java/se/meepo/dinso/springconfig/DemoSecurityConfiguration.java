package se.meepo.dinso.springconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class DemoSecurityConfiguration {
  private final DemoJwtAuthenticationFilter jwtFilter;

  public DemoSecurityConfiguration(DemoJwtAuthenticationFilter jwtFilter) {
    this.jwtFilter = jwtFilter;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) -> response.setStatus(401))
                    .accessDeniedHandler((request, response, exception) -> response.setStatus(403)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/demo/**", "/api/auth/**", "/h2-console/**", "/error")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("SYSTEM_ADMIN")
                    .requestMatchers("/api/private/**", "/api/company/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
        .build();
  }
}
