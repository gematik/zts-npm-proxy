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

package de.gematik.zts.npmproxy.controller;

import static de.gematik.zts.npmproxy.NpmProxyConstants.MESSAGE_TOKEN_GENERATION_INVALID_PACKAGE;
import static de.gematik.zts.npmproxy.NpmProxyConstants.MESSAGE_TOKEN_GENERATION_NO_PACKAGE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.zts.npmproxy.JwtController;
import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.exceptions.JwtCreationException;
import de.gematik.zts.npmproxy.exceptions.ServiceUnavailableException;
import de.gematik.zts.npmproxy.model.TokenRequest;
import de.gematik.zts.npmproxy.model.TokenResponse;
import de.gematik.zts.npmproxy.repository.LuceneBackedPackageRepository;
import de.gematik.zts.npmproxy.security.JwtService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class JwtControllerTest {

  @InjectMocks private JwtController jwtController;

  @Mock private JwtService jwtService;

  @Mock private NpmProxyConfiguration properties;

  @Mock private Authentication authentication;

  @Mock private ServerWebExchange exchange;
  @Mock private LuceneBackedPackageRepository repository;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testGenerateToken_success() {
    // Mocking the request and authentication
    TokenRequest request = new TokenRequest();
    request.setPackages(List.of("package1"));

    String generatedToken = "token";
    when(authentication.getName()).thenReturn("testUser");
    doReturn(Set.of("package1")).when(properties).getProtectedPackages();
    when(jwtService.generateToken(any(UserDetails.class), anyString())).thenReturn(generatedToken);
    when(repository.isInitialUpdateSucceeded()).thenReturn(true);
    when(repository.isPackageVersionProtected("package1", null)).thenReturn(true);

    // Call the generateToken method
    Mono<TokenResponse> responseMono =
        jwtController.generateToken(request, authentication, exchange);
    TokenResponse response = responseMono.block(); // Blocking for test purposes

    // Verify the results
    assertNotNull(response);
    assertEquals(generatedToken, response.getToken());
  }

  @Test
  void testGenerateToken_noPackages() {
    // Mocking the request with no packages
    TokenRequest request = new TokenRequest();
    request.setPackages(List.of());

    when(repository.isInitialUpdateSucceeded()).thenReturn(true);

    // Call the generateToken method and expect an exception
    Exception exception =
        assertThrows(JwtCreationException.class, () -> generateTokenAndBlock(request));

    assertEquals(MESSAGE_TOKEN_GENERATION_NO_PACKAGE, exception.getMessage());
  }

  @Test
  void testGenerateToken_invalidPackage() {
    // Mocking the request with an invalid package
    TokenRequest request = new TokenRequest();
    request.setPackages(List.of("invalidPackage"));

    when(repository.isInitialUpdateSucceeded()).thenReturn(true);

    when(authentication.getName()).thenReturn("testUser");
    // when(properties.getProtectedPackages()).thenReturn(List.of("package1")); // Only package1 is
    // valid
    doReturn(Set.of("package1")).when(properties).getProtectedPackages();

    // Call the generateToken method and expect an exception
    Exception exception =
        assertThrows(JwtCreationException.class, () -> generateTokenAndBlock(request));

    assertEquals(
        MESSAGE_TOKEN_GENERATION_INVALID_PACKAGE + "invalidPackage", exception.getMessage());
  }

  @Test
  void testGenerateToken_repositoryNotInitialized() {
    // Mocking the request with a valid package
    TokenRequest request = new TokenRequest();
    request.setPackages(List.of("package1"));

    when(repository.isInitialUpdateSucceeded()).thenReturn(false);

    // Call the generateToken method and expect an exception
    Exception exception =
        assertThrows(ServiceUnavailableException.class, () -> generateTokenAndBlock(request));

    assertEquals(
        HttpStatus.SERVICE_UNAVAILABLE
            + " \"Der Dienst wurde nicht korrekt initialisiert. Bitte versuchen Sie es später erneut.\"",
        exception.getMessage());
  }

  private TokenResponse generateTokenAndBlock(TokenRequest request) {
    return jwtController.generateToken(request, authentication, exchange).block();
  }
}
