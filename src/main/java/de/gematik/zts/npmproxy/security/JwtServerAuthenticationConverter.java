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

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
class JwtServerAuthenticationConverter implements ServerAuthenticationConverter {

  private final JwtService jwtService;
  private static final String BEARER = "Bearer ";

  // Einstiegspunkt in Authentisierung - Umwandeln von Requestinformationen in einen
  // JwtAuthenticationToken
  @Override
  public Mono<Authentication> convert(ServerWebExchange exchange) {
    return Mono.create(
        sink -> {
          String authorizationHeaderValue =
              exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
          if (StringUtils.isNoneEmpty(authorizationHeaderValue)
              && authorizationHeaderValue.startsWith(BEARER)) {
            String token = authorizationHeaderValue.substring(BEARER.length());
            sink.success(new JwtAuthenticationToken(token, createUserDetails(token)));
          } else {
            UserDetails userDetails =
                User.builder()
                    .username("anonymous")
                    .authorities("unknown")
                    .password("anonymous")
                    .build();
            Authentication auth = new JwtAuthenticationToken(null, userDetails);
            sink.success(auth);
          }
        });
  }

  /**
   * Erstellen von UserDetails auf Grundlage bestimmter Angaben im Token. In unserem Fall sind das
   * der 'username' und die 'authorities'
   *
   * @param jwtToken JWT
   * @return entsprechende UserDetails
   */
  private UserDetails createUserDetails(String jwtToken) {
    String username = jwtService.extractUsername(jwtToken);
    return User.builder()
        .username(username)
        .authorities(createAuthorities(jwtToken))
        .password("")
        .build();
  }

  private List<SimpleGrantedAuthority> createAuthorities(String token) {
    return jwtService.extractPackages(token).stream().map(SimpleGrantedAuthority::new).toList();
  }
}
