/*
 * Copyright (Change Date see Readme), gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes
 * by gematik, find details in the "Readme" file.
 */

package de.gematik.zts.npmproxy.security;

import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.logging.LoggingFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.WebFilter;

@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  private final NpmProxyConfiguration properties;

  @Autowired
  public SecurityConfig(NpmProxyConfiguration properties) {
    this.properties = properties;
  }

  @Bean
  SecurityWebFilterChain springSecurityFilterChain(
      ServerHttpSecurity http,
      ReactiveAuthenticationManager authenticationManager,
      ServerAuthenticationConverter authenticationConverter) {

    AuthenticationWebFilter authenticationWebFilter =
        new AuthenticationWebFilter(authenticationManager);

    authenticationWebFilter.setServerAuthenticationConverter(authenticationConverter);
    authenticationWebFilter.setAuthenticationFailureHandler(
        new CustomServerAuthenticationFailureHandler());

    return http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .addFilterAt(authenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .build();
  }

  // CORS-Konfiguration
  // Wir behandeln alle Operationen identisch. Daher reicht eine allgemeine Konfiguration, die für
  // alle Pfade/Methoden greift, aus.
  @Bean
  public UrlBasedCorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    log.info(properties.getHostName());

    configuration.addAllowedOrigin(properties.getHostName());
    configuration.addAllowedMethod("*");
    configuration.addAllowedHeader("*");
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration); // CORS für alle Pfade konfigurieren
    return source;
  }

  // Filter für Logging von Request-Informationen
  @Bean
  public WebFilter initialRequestLoggingFilter() {
    return new LoggingFilter();
  }
}
