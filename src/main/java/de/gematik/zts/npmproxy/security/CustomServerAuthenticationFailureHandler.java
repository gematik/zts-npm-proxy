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

import static de.gematik.zts.npmproxy.NpmProxyConstants.PROBLEMDETAILS_TITLE_UNAUTHORIZED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import reactor.core.publisher.Mono;

// Leider können wir AuthenticationExceptions nicht im globalen ExceptionHandler bearbeiten, da
// Spring Security hier eine eigene Verarbeitungslogik verwendet. Daher müssen wir für alle Problem,
// die die Authentisierung betreffen, einen eigenen Handler implementieren.
@Slf4j
public class CustomServerAuthenticationFailureHandler
    implements ServerAuthenticationFailureHandler {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Mono<Void> onAuthenticationFailure(
      WebFilterExchange webFilterExchange, AuthenticationException exception) {

    // Zusammenstellen der Response-Inhalte
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_UNAUTHORIZED);
    problemDetail.setDetail("Authentifizierungsfehler: " + exception.getMessage());
    problemDetail.setInstance(
        URI.create(webFilterExchange.getExchange().getRequest().getPath().value()));
    problemDetail.setProperties(Map.of("timestamp", System.currentTimeMillis()));

    // Setze den HTTP-Status und den Content-Type
    var response = webFilterExchange.getExchange().getResponse();
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    return response.writeWith(
        Mono.fromSupplier(
            () -> {
              try {
                byte[] bytes = objectMapper.writeValueAsBytes(problemDetail);
                return response.bufferFactory().wrap(bytes);
              } catch (JsonProcessingException e) {
                log.error("Error writing response: {}", e.getMessage(), e);
                return response.bufferFactory().wrap("{}".getBytes(StandardCharsets.UTF_8));
              }
            }));
  }
}
