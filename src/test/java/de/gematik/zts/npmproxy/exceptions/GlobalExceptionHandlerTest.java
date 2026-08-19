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

package de.gematik.zts.npmproxy.exceptions;

import static de.gematik.zts.npmproxy.NpmProxyConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.core.MethodParameter;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.NotAcceptableStatusException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import reactor.core.publisher.Mono;

class GlobalExceptionHandlerTest {

  @InjectMocks private GlobalExceptionHandler globalExceptionHandler;

  private MockServerWebExchange exchange;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    exchange = MockServerWebExchange.builder(MockServerHttpRequest.get("/test")).build();
  }

  @Test
  void testHandleBadRequestException() {

    MethodParameter methodParameter =
        new MethodParameter(GlobalExceptionHandler.class.getDeclaredMethods()[0], 0);
    ServerWebInputException ex = new ServerWebInputException("Test error", methodParameter);
    Mono<ResponseEntity<ProblemDetail>> response =
        globalExceptionHandler.handleBadRequestException(ex, exchange);
    ResponseEntity<ProblemDetail> entity = response.block();

    assertNotNull(entity);
    assertEquals(HttpStatus.BAD_REQUEST, entity.getStatusCode());
    assertEquals(PROBLEMDETAILS_TITLE_BAD_REQUEST, entity.getBody().getTitle());
    assertEquals(ex.getMessage(), entity.getBody().getDetail());
    assertNotNull(entity.getBody().getProperties().get(PROBLEMDETAILS_PROPERTY_TIMESTAMP));
  }

  @Test
  void testHandleMethodNotAllowedException() {
    MethodNotAllowedException ex = new MethodNotAllowedException("GET", Collections.emptySet());
    Mono<ResponseEntity<ProblemDetail>> response =
        globalExceptionHandler.handleMethodNotAllowedException(ex, exchange);
    ResponseEntity<ProblemDetail> entity = response.block();

    assertNotNull(entity);
    assertEquals(HttpStatus.METHOD_NOT_ALLOWED, entity.getStatusCode());
    assertEquals(PROBLEMDETAILS_TITLE_METHOD_NOT_ALLOWED, entity.getBody().getTitle());
    assertEquals(ex.getMessage(), entity.getBody().getDetail());
    assertNotNull(entity.getBody().getProperties().get(PROBLEMDETAILS_PROPERTY_TIMESTAMP));
  }

  @Test
  void testHandleFeedGenerationException() {
    FeedGenerationException ex = new FeedGenerationException("Test error");
    Mono<ResponseEntity<ProblemDetail>> response =
        globalExceptionHandler.handleFeedGenerationException(ex, exchange);
    ResponseEntity<ProblemDetail> entity = response.block();

    assertNotNull(entity);
    assertEquals(HttpStatus.BAD_REQUEST, entity.getStatusCode());
    assertEquals(PROBLEMDETAILS_TITLE_BAD_REQUEST, entity.getBody().getTitle());
    assertEquals(ex.getMessage(), entity.getBody().getDetail());
    assertNotNull(entity.getBody().getProperties().get(PROBLEMDETAILS_PROPERTY_TIMESTAMP));
  }

  @Test
  void testHandleAuthenticationCredentialsNotFoundException() {
    AuthenticationCredentialsNotFoundException ex =
        new AuthenticationCredentialsNotFoundException("Test error");
    Mono<ResponseEntity<ProblemDetail>> response =
        globalExceptionHandler.handleAuthenticationCredentialsNotFoundException(ex, exchange);
    ResponseEntity<ProblemDetail> entity = response.block();

    assertNotNull(entity);
    assertEquals(HttpStatus.UNAUTHORIZED, entity.getStatusCode());
    assertEquals(PROBLEMDETAILS_TITLE_UNAUTHORIZED, entity.getBody().getTitle());
    assertEquals(ex.getMessage(), entity.getBody().getDetail());
    assertNotNull(entity.getBody().getProperties().get(PROBLEMDETAILS_PROPERTY_TIMESTAMP));
  }

  @Test
  void testHandlePackageAccessDeniedException() {
    PackageAccessDeniedException ex = new PackageAccessDeniedException("Test error");
    Mono<ResponseEntity<ProblemDetail>> response =
        globalExceptionHandler.handlePackageAccessDeniedException(ex, exchange);
    ResponseEntity<ProblemDetail> entity = response.block();

    assertNotNull(entity);
    assertEquals(HttpStatus.FORBIDDEN, entity.getStatusCode());
    assertEquals(PROBLEMDETAILS_TITLE_FORBIDDEN, entity.getBody().getTitle());
    assertEquals(ex.getMessage(), entity.getBody().getDetail());
    assertNotNull(entity.getBody().getProperties().get(PROBLEMDETAILS_PROPERTY_TIMESTAMP));
  }

  @Test
  void testHandleJwtCreationException() {
    JwtCreationException ex = new JwtCreationException("Test error");
    Mono<ResponseEntity<ProblemDetail>> response =
        globalExceptionHandler.handleJwtCreationException(ex, exchange);
    ResponseEntity<ProblemDetail> entity = response.block();

    assertNotNull(entity);
    assertEquals(HttpStatus.BAD_REQUEST, entity.getStatusCode());
    assertEquals(PROBLEMDETAILS_TITLE_BAD_REQUEST, entity.getBody().getTitle());
    assertEquals(ex.getMessage(), entity.getBody().getDetail());
    assertNotNull(entity.getBody().getProperties().get(PROBLEMDETAILS_PROPERTY_TIMESTAMP));
  }

  @Test
  void testHandleNoResourceFoundException() {
    NoResourceFoundException ex = new NoResourceFoundException(URI.create("/test/path"), "Test error");
    Mono<ResponseEntity<ProblemDetail>> response =
        globalExceptionHandler.handleNoResourceFoundException(ex, exchange);
    ResponseEntity<ProblemDetail> entity = response.block();

    assertNotNull(entity);
    assertEquals(HttpStatus.NOT_FOUND, entity.getStatusCode());
    assertEquals(PROBLEMDETAILS_TITLE_NOT_FOUND, entity.getBody().getTitle());
    assertEquals(ex.getReason(), entity.getBody().getDetail());
    assertNotNull(entity.getBody().getProperties().get(PROBLEMDETAILS_PROPERTY_TIMESTAMP));
  }

  @Test
  void testHandleServiceUnavailableException() {
    ServiceUnavailableException ex = new ServiceUnavailableException("Test error");
    Mono<ResponseEntity<ProblemDetail>> response =
        globalExceptionHandler.handleServiceUnavailableException(ex, exchange);
    ResponseEntity<ProblemDetail> entity = response.block();

    assertNotNull(entity);
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, entity.getStatusCode());
    assertEquals(PROBLEMDETAILS_TITLE_SERVICE_UNAVAILABLE, entity.getBody().getTitle());
    assertEquals(ex.getReason(), entity.getBody().getDetail());
    assertNotNull(entity.getBody().getProperties().get(PROBLEMDETAILS_PROPERTY_TIMESTAMP));
  }

  @Test
  void testHandleConstraintValidationException() {
    ConstraintViolationException ex = new ConstraintViolationException("Test error", null);
    Mono<ResponseEntity<ProblemDetail>> response =
        globalExceptionHandler.handleConstraintValidationException(ex, exchange);
    ResponseEntity<ProblemDetail> entity = response.block();

    assertNotNull(entity);
    assertEquals(HttpStatus.BAD_REQUEST, entity.getStatusCode());
    assertEquals(PROBLEMDETAILS_TITLE_BAD_REQUEST, entity.getBody().getTitle());
    assertEquals(ex.getMessage(), entity.getBody().getDetail());
    assertNotNull(entity.getBody().getProperties().get(PROBLEMDETAILS_PROPERTY_TIMESTAMP));
  }

  @Test
  void testHandleDecodingException() {
    DecodingException ex = new DecodingException("Test error");
    Mono<ResponseEntity<ProblemDetail>> response =
        globalExceptionHandler.handleDecodingException(ex, exchange);
    ResponseEntity<ProblemDetail> entity = response.block();

    assertNotNull(entity);
    assertEquals(HttpStatus.BAD_REQUEST, entity.getStatusCode());
    assertEquals(PROBLEMDETAILS_TITLE_BAD_REQUEST, entity.getBody().getTitle());
    assertEquals(
        "Es scheint ein Problem beim Dekodieren der Anfrage "
            + "aufgetreten zu sein. Bitte überprüfen Sie die Parameter.",
        entity.getBody().getDetail());
    assertNotNull(entity.getBody().getProperties().get(PROBLEMDETAILS_PROPERTY_TIMESTAMP));
  }

  @Test
  void testHandleUnsupportedMediaTypeStatusException() {
    var ex = new UnsupportedMediaTypeStatusException("Test error");
    Mono<ResponseEntity<ProblemDetail>> response =
        globalExceptionHandler.handleUnsupportedMediaTypeStatusException(ex, exchange);
    ResponseEntity<ProblemDetail> entity = response.block();

    assertNotNull(entity);
    assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, entity.getStatusCode());
    assertEquals(PROBLEMDETAILS_TITLE_UNSUPPORTED_MEDIA_TYPE, entity.getBody().getTitle());
    assertEquals(ex.getMessage(), entity.getBody().getDetail());
    assertNotNull(entity.getBody().getProperties().get(PROBLEMDETAILS_PROPERTY_TIMESTAMP));
  }

  @Test
  void testHandleNotAcceptableStatusException() {
    var ex = new NotAcceptableStatusException("Test error");
    Mono<ResponseEntity<ProblemDetail>> response =
        globalExceptionHandler.handleNotAcceptableStatusException(ex, exchange);
    ResponseEntity<ProblemDetail> entity = response.block();

    assertNotNull(entity);
    assertEquals(HttpStatus.NOT_ACCEPTABLE, entity.getStatusCode());
    assertEquals(PROBLEMDETAILS_TITLE_NOT_ACCEPTABLE, entity.getBody().getTitle());
    assertEquals(ex.getMessage(), entity.getBody().getDetail());
    assertNotNull(entity.getBody().getProperties().get(PROBLEMDETAILS_PROPERTY_TIMESTAMP));
  }
}
