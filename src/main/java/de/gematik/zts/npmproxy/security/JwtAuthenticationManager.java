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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
class JwtAuthenticationManager implements ReactiveAuthenticationManager {

  private final JwtService jwtService;

  // Hier findet die eigentliche Authentisierung statt, d.h. an dieser Stelle wird das Token geprüft
  @Override
  public Mono<Authentication> authenticate(Authentication authentication) {

    return Mono.create(
        sink -> {
          if (authentication instanceof JwtAuthenticationToken jwtAuth) {

            // Überprüfen, ob überhaupt ein Token enthalten ist
            if (StringUtils.isNoneEmpty(jwtAuth.getToken())) {

              // Da wir anscheinend ein Token haben, überprüfen der Tokenvalidität
              if (jwtService.isTokenValid(jwtAuth.getToken())) {
                // Das Token scheint gültig zu sein. Wir setzen den Kontext auf "authenticated"
                sink.success(jwtAuth.withAuthenticated(true));
              } else {
                log.warn("Token is invalid.");
                // Das Token ist ungültig und das teilen wir dem Nutzer auch mit. Anders als beim
                // fehlenden Token brechen wir hier jedoch die weitere Verarbeitung ab
                sink.error(new JwtAuthenticationException("Invalid Token."));
              }
            } else {
              // Wir haben keinen Token. Da es aber auch Anwendungsfälle gibt, in denen ein
              // unauthentisierter Zugriff möglich ist, setzen wir den Kontext entsprechend auf "not
              // authenticated"
              sink.success(jwtAuth.withAuthenticated(false));
            }
          } else {
            log.warn("Authentication is not of type JwtAuthenticationToken.");
            sink.error(new JwtAuthenticationException("Error during authentication."));
          }
        });
  }
}
