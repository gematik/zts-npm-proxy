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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class JwtAuthenticationManagerTest {

  @Mock private JwtService jwtService;
  @InjectMocks private JwtAuthenticationManager jwtAuthenticationManager;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testAuthenticateWithValidToken() {
    String token = "validToken";
    UserDetails userDetails = mock(UserDetails.class);
    when(userDetails.getUsername()).thenReturn("testUser");
    JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(token, userDetails);

    // Sicherstellen, dass das Token als gültig angenommen wird
    when(jwtService.isTokenValid(token)).thenReturn(true);

    // Methode aufrufen
    Mono<Authentication> result = jwtAuthenticationManager.authenticate(jwtAuth);

    StepVerifier.create(result)
        .assertNext(
            auth -> {
              assertTrue(auth.isAuthenticated());
              assertEquals(token, ((JwtAuthenticationToken) auth).getToken());
            })
        .verifyComplete();
  }

  @Test
  void testAuthenticateWithInvalidToken() {
    String emptyToken = "invalidToken";
    UserDetails userDetails = mock(UserDetails.class);
    when(userDetails.getUsername()).thenReturn("testUser");
    JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(emptyToken, userDetails);

    // Methode aufrufen
    Mono<Authentication> result = jwtAuthenticationManager.authenticate(jwtAuth);

    // Ergebnisse prüfen
    StepVerifier.create(result).expectError(JwtAuthenticationException.class).verify();
  }

  @Test
  void testAuthenticateWithEmptyToken() {
    String emptyToken = null;
    UserDetails userDetails = mock(UserDetails.class);
    when(userDetails.getUsername()).thenReturn("testUser");
    JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(emptyToken, userDetails);

    // Methode aufrufen
    Mono<Authentication> result = jwtAuthenticationManager.authenticate(jwtAuth);

    // Ergebnisse prüfen
    StepVerifier.create(result)
        .assertNext(
            auth -> {
              assertFalse(auth.isAuthenticated());
            })
        .verifyComplete();
  }

  @Test
  void testAuthenticateWithNonJwtAuthentication() {
    Authentication auth = mock(Authentication.class);

    // Methode aufrufen
    Mono<Authentication> result = jwtAuthenticationManager.authenticate(auth);

    // Ergebnisse prüfen
    StepVerifier.create(result).expectError(JwtAuthenticationException.class).verify();
  }
}
