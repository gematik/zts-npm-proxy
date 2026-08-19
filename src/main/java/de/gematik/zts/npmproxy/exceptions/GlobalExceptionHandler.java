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

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@ControllerAdvice
@Order(-2)
@Slf4j
public class GlobalExceptionHandler {

  // Tritt auf, wenn die Anfrageparameter nicht den Erwartungen entsprechen (bspw. Boolean-Parameter
  // nicht "true" oder "false").
  @ExceptionHandler(ServerWebInputException.class)
  public Mono<ResponseEntity<ProblemDetail>> handleBadRequestException(
      ServerWebInputException ex, ServerWebExchange exchange) {
    exchange.getAttributes().put(ATTRIBUTE_EXCEPTION, ex);
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_BAD_REQUEST);
    problemDetail.setDetail(ex.getMessage());
    problemDetail.setProperties(
        Map.of(PROBLEMDETAILS_PROPERTY_TIMESTAMP, System.currentTimeMillis()));

    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail));
  }

  // Tritt auf, wenn eine Methode aufgerufen wird, die nicht erlaubt ist (bspw. PUT auf dem
  // generate-token-Endpoint)
  @ExceptionHandler(MethodNotAllowedException.class)
  public Mono<ResponseEntity<ProblemDetail>> handleMethodNotAllowedException(
      MethodNotAllowedException ex, ServerWebExchange exchange) {
    exchange.getAttributes().put(ATTRIBUTE_EXCEPTION, ex);
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_METHOD_NOT_ALLOWED);
    problemDetail.setDetail(ex.getMessage());
    problemDetail.setProperties(
        Map.of(PROBLEMDETAILS_PROPERTY_TIMESTAMP, System.currentTimeMillis()));

    return Mono.just(ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(problemDetail));
  }

  // Tritt auf, wenn ein Fehler beim Generieren des eines Feeds auftritt
  @ExceptionHandler(FeedGenerationException.class)
  public Mono<ResponseEntity<ProblemDetail>> handleFeedGenerationException(
      FeedGenerationException ex, ServerWebExchange exchange) {
    exchange.getAttributes().put(ATTRIBUTE_EXCEPTION, ex);
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_BAD_REQUEST);
    problemDetail.setDetail(ex.getMessage());
    problemDetail.setProperties(
        Map.of(PROBLEMDETAILS_PROPERTY_TIMESTAMP, System.currentTimeMillis()));

    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail));
  }

  // Tritt auf, wenn ein protected package angefordert wird, ohne dass der Token in der Anfrage
  // mitgesendet wurde
  @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
  public Mono<ResponseEntity<ProblemDetail>> handleAuthenticationCredentialsNotFoundException(
      AuthenticationCredentialsNotFoundException ex, ServerWebExchange exchange) {
    exchange.getAttributes().put(ATTRIBUTE_EXCEPTION, ex);
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_UNAUTHORIZED);
    problemDetail.setDetail(ex.getMessage());
    problemDetail.setProperties(
        Map.of(PROBLEMDETAILS_PROPERTY_TIMESTAMP, System.currentTimeMillis()));

    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail));
  }

  // Tritt auf, wenn ein protected package angefordert wird, ohne dass der Token dieses Paket
  // enthält
  @ExceptionHandler(PackageAccessDeniedException.class)
  public Mono<ResponseEntity<ProblemDetail>> handlePackageAccessDeniedException(
      PackageAccessDeniedException ex, ServerWebExchange exchange) {
    exchange.getAttributes().put(ATTRIBUTE_EXCEPTION, ex);
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_FORBIDDEN);
    problemDetail.setDetail(ex.getMessage());
    problemDetail.setProperties(
        Map.of(PROBLEMDETAILS_PROPERTY_TIMESTAMP, System.currentTimeMillis()));

    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail));
  }

  // Tritt auf, wenn die Liste der Pakete leer ist, bzw. ein unbekanntes Paket enthält
  @ExceptionHandler(JwtCreationException.class)
  public Mono<ResponseEntity<ProblemDetail>> handleJwtCreationException(
      JwtCreationException ex, ServerWebExchange exchange) {
    exchange.getAttributes().put(ATTRIBUTE_EXCEPTION, ex);

    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_BAD_REQUEST);
    problemDetail.setDetail(ex.getMessage());
    problemDetail.setProperties(
        Map.of(PROBLEMDETAILS_PROPERTY_TIMESTAMP, System.currentTimeMillis()));

    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail));
  }

  // Tritt auf, wenn ein Paket nicht gefunden werden konnte
  @ExceptionHandler(NoResourceFoundException.class)
  public Mono<ResponseEntity<ProblemDetail>> handleNoResourceFoundException(
      NoResourceFoundException ex, ServerWebExchange exchange) {
    exchange.getAttributes().put(ATTRIBUTE_EXCEPTION, ex);

    ProblemDetail problemDetail = ProblemDetail.forStatus(ex.getStatusCode());
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_NOT_FOUND);
    problemDetail.setDetail(ex.getReason());
    problemDetail.setProperties(
        Map.of(PROBLEMDETAILS_PROPERTY_TIMESTAMP, System.currentTimeMillis()));

    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail));
  }

  // Tritt auf, wenn der Service nicht verfügbar ist (not healthy)
  @ExceptionHandler(ServiceUnavailableException.class)
  public Mono<ResponseEntity<ProblemDetail>> handleServiceUnavailableException(
      ServiceUnavailableException ex, ServerWebExchange exchange) {
    exchange.getAttributes().put(ATTRIBUTE_EXCEPTION, ex);

    ProblemDetail problemDetail = ProblemDetail.forStatus(ex.getStatusCode());
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_SERVICE_UNAVAILABLE);
    problemDetail.setDetail(ex.getReason());
    problemDetail.setProperties(
        Map.of(PROBLEMDETAILS_PROPERTY_TIMESTAMP, System.currentTimeMillis()));

    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problemDetail));
  }

  // Tritt auf, wenn die konfigurierte Validierung der Anfrageparameter fehlschlägt.
  @ExceptionHandler(ConstraintViolationException.class)
  public Mono<ResponseEntity<ProblemDetail>> handleConstraintValidationException(
      ConstraintViolationException ex, ServerWebExchange exchange) {
    exchange.getAttributes().put(ATTRIBUTE_EXCEPTION, ex);
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_BAD_REQUEST);
    problemDetail.setDetail(ex.getMessage());
    problemDetail.setProperties(
        Map.of(PROBLEMDETAILS_PROPERTY_TIMESTAMP, System.currentTimeMillis()));

    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail));
  }

  // Tritt auf, wenn es Probleme beim Dekodieren des POST-Requests gibt.
  @ExceptionHandler(DecodingException.class)
  public Mono<ResponseEntity<ProblemDetail>> handleDecodingException(
      DecodingException ex, ServerWebExchange exchange) {
    exchange.getAttributes().put(ATTRIBUTE_EXCEPTION, ex);
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_BAD_REQUEST);
    problemDetail.setDetail(
        "Es scheint ein Problem beim Dekodieren der Anfrage "
            + "aufgetreten zu sein. Bitte überprüfen Sie die Parameter.");
    problemDetail.setProperties(
        Map.of(PROBLEMDETAILS_PROPERTY_TIMESTAMP, System.currentTimeMillis()));

    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail));
  }

  // Occurs, when the request contains an unsupported media type
  @ExceptionHandler(UnsupportedMediaTypeStatusException.class)
  public Mono<ResponseEntity<ProblemDetail>> handleUnsupportedMediaTypeStatusException(
      UnsupportedMediaTypeStatusException ex, ServerWebExchange exchange) {
    exchange.getAttributes().put(ATTRIBUTE_EXCEPTION, ex);
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_UNSUPPORTED_MEDIA_TYPE);
    problemDetail.setDetail(ex.getMessage());
    problemDetail.setProperties(
        Map.of(PROBLEMDETAILS_PROPERTY_TIMESTAMP, System.currentTimeMillis()));

    return Mono.just(ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problemDetail));
  }

  // Occurs, when the request contains an unsupported accept header
  @ExceptionHandler(NotAcceptableStatusException.class)
  public Mono<ResponseEntity<ProblemDetail>> handleNotAcceptableStatusException(
      NotAcceptableStatusException ex, ServerWebExchange exchange) {
    exchange.getAttributes().put(ATTRIBUTE_EXCEPTION, ex);
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_ACCEPTABLE);
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_NOT_ACCEPTABLE);
    problemDetail.setDetail(ex.getMessage());
    problemDetail.setProperties(
        Map.of(PROBLEMDETAILS_PROPERTY_TIMESTAMP, System.currentTimeMillis()));

    return Mono.just(ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(problemDetail));
  }

  // generic exception handler for all other exceptions
  @ExceptionHandler(Exception.class)
  public Mono<ResponseEntity<ProblemDetail>> handleGenericException(Exception ex) {
    // log the error with detail
    log.error("An unexpected error occurred: {}", ex.getMessage(), ex);
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problemDetail.setTitle(PROBLEMDETAILS_TITLE_INTERNAL_SERVER_ERROR);
    problemDetail.setDetail(MESSAGE_GENERIC_INTERNAL_SERVER_ERROR);
    problemDetail.setProperties(
        Map.of(PROBLEMDETAILS_PROPERTY_TIMESTAMP, System.currentTimeMillis()));

    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail));
  }
  // Weitere Exceptions behandeln, falls notwendig

}
