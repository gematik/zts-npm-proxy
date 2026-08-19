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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class JwtServerAuthenticationConverterTest {

  @Mock private JwtService jwtService;
  @Mock private ServerWebExchange exchange;
  @Mock private ServerHttpRequest request;
  @Mock private HttpHeaders headers;

  @InjectMocks private JwtServerAuthenticationConverter converter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(exchange.getRequest()).thenReturn(request);
    when(request.getHeaders()).thenReturn(headers);
  }

  @Test
  void testConvertWithValidAuthorizationHeader() {
    String username = "testUser";
    String token = "validToken";
    String packageName = "bfarm.terminologien.test";
    when(headers.getFirst(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
    when(jwtService.extractUsername(token)).thenReturn(username);
    when(jwtService.extractPackages(token)).thenReturn(List.of(packageName));

    // Methodenaufruf
    Mono<Authentication> result = converter.convert(exchange);

    // Überprüfung des Ergebnisses
    StepVerifier.create(result)
        .assertNext(
            auth -> {
              assertEquals(username, ((User) auth.getPrincipal()).getUsername());
              assertEquals(
                  packageName,
                  ((User) auth.getPrincipal()).getAuthorities().iterator().next().getAuthority());
              assertEquals(token, ((JwtAuthenticationToken) auth).getToken());
            })
        .verifyComplete();
  }

  @Test
  void testConvertWithInvalidAuthorizationHeader() {
    when(headers.getFirst(HttpHeaders.AUTHORIZATION)).thenReturn(null);

    // Methodenaufruf
    Mono<Authentication> result = converter.convert(exchange);

    // Überprüfung des Ergebnisses
    StepVerifier.create(result)
        .assertNext(
            auth -> {
              assertNull(((JwtAuthenticationToken) auth).getToken());
              assertEquals("anonymous", ((User) auth.getPrincipal()).getUsername());
              assertEquals("anonymous", ((User) auth.getPrincipal()).getPassword());
              assertEquals(
                  "unknown",
                  ((User) auth.getPrincipal()).getAuthorities().iterator().next().getAuthority());
            })
        .verifyComplete();
  }
}
