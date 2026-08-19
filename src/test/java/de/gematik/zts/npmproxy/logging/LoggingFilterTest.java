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

package de.gematik.zts.npmproxy.logging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import de.gematik.zts.npmproxy.tools.ClientIpExtractor;
import java.net.URI;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.server.reactive.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoggingFilterTest {

  private LoggingFilter loggingFilter;

  private Logger logger;
  private TestAppender testAppender;

  private ServerWebExchange exchange;
  private WebFilterChain chain;

  private ServerHttpResponse response;

  @BeforeEach
  void setUp() {
    loggingFilter = new LoggingFilter();

    // Einrichten des Loggers und des Appenders
    logger = (Logger) LoggerFactory.getLogger(LoggingFilter.class);
    testAppender = new TestAppender();
    testAppender.setContext(logger.getLoggerContext());
    logger.addAppender(testAppender);
    testAppender.start();

    // Mocks erstellen
    exchange = mock(ServerWebExchange.class);
    chain = mock(WebFilterChain.class);
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    response = mock(ServerHttpResponse.class);
    HttpHeaders requestHeaders = mock(HttpHeaders.class);
    HttpHeaders responseHeaders = mock(HttpHeaders.class);

    // Standardverhalten der Mocks definieren
    when(exchange.getRequest()).thenReturn(request);
    when(exchange.getResponse()).thenReturn(response);

    when(request.getHeaders()).thenReturn(requestHeaders);
    when(request.getURI()).thenReturn(URI.create("http://localhost/test"));
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    when(requestHeaders.getFirst("User-Agent")).thenReturn("JUnit Test");

    when(response.getHeaders()).thenReturn(responseHeaders);
    when(response.getStatusCode()).thenReturn(HttpStatusCode.valueOf(200));
    when(responseHeaders.getContentLength()).thenReturn(0L); // Setzen des Content-Length

    when(exchange.getAttributes()).thenReturn(new HashMap<>());
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(testAppender);
    testAppender.stop();
  }

  @Test
  void testFilterLogsInfoForSuccessfulRequest() {
    // Arrange
    when(chain.filter(exchange)).thenReturn(Mono.empty());

    // Mock ClientIpExtractor
    try (MockedStatic<ClientIpExtractor> mockedStatic =
        Mockito.mockStatic(ClientIpExtractor.class)) {
      mockedStatic
          .when(() -> ClientIpExtractor.getClientIpAddress(exchange))
          .thenReturn("127.0.0.1");

      // Act
      Mono<Void> result = loggingFilter.filter(exchange, chain);

      // Assert
      StepVerifier.create(result).verifyComplete();

      // Warten, bis Logs geschrieben wurden
      Awaitility.await()
          .atMost(1, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                // Überprüfen, ob ein INFO-Log mit der erwarteten Nachricht vorhanden ist
                assertTrue(
                    testAppender.contains("HTTP Request processed", Level.INFO),
                    "Es wurde kein erwarteter INFO-Log gefunden");
              });
    }
  }

  @Test
  void testFilterLogsWarnForClientError() {
    // Arrange
    when(chain.filter(exchange)).thenReturn(Mono.error(new RuntimeException("Client error")));
    when(response.getStatusCode()).thenReturn(HttpStatusCode.valueOf(400));

    // Mock ClientIpExtractor
    try (MockedStatic<ClientIpExtractor> mockedStatic =
        Mockito.mockStatic(ClientIpExtractor.class)) {
      mockedStatic
          .when(() -> ClientIpExtractor.getClientIpAddress(exchange))
          .thenReturn("127.0.0.1");

      // Act
      Mono<Void> result = loggingFilter.filter(exchange, chain);

      // Assert
      StepVerifier.create(result).expectError().verify();

      // Warten, bis Logs geschrieben wurden
      Awaitility.await()
          .atMost(1, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                // Überprüfen, ob ein WARN-Log mit der erwarteten Nachricht vorhanden ist
                assertTrue(
                    testAppender.contains("HTTP Request processed", Level.WARN),
                    "Es wurde kein erwarteter WARN-Log gefunden");
              });
    }
  }

  @Test
  void testFilterLogsWarnForServerError() {
    // Arrange
    when(chain.filter(exchange)).thenReturn(Mono.error(new RuntimeException("Server error")));
    when(response.getStatusCode()).thenReturn(HttpStatusCode.valueOf(500));

    // Mock ClientIpExtractor
    try (MockedStatic<ClientIpExtractor> mockedStatic =
        Mockito.mockStatic(ClientIpExtractor.class)) {
      mockedStatic
          .when(() -> ClientIpExtractor.getClientIpAddress(exchange))
          .thenReturn("127.0.0.1");

      // Act
      Mono<Void> result = loggingFilter.filter(exchange, chain);

      // Assert
      StepVerifier.create(result).expectError().verify();

      // Warten, bis Logs geschrieben wurden
      Awaitility.await()
          .atMost(1, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                // Überprüfen, ob ein WARN-Log mit der erwarteten Nachricht vorhanden ist
                assertTrue(
                    testAppender.contains("HTTP Request processed", Level.WARN),
                    "Es wurde kein erwarteter WARN-Log gefunden");
              });
    }
  }

  @Test
  void testFilterLogsWarnForNullStatusCode() {
    // Arrange
    when(chain.filter(exchange)).thenReturn(Mono.empty());
    when(response.getStatusCode()).thenReturn(null);

    // Mock ClientIpExtractor
    try (MockedStatic<ClientIpExtractor> mockedStatic =
        Mockito.mockStatic(ClientIpExtractor.class)) {
      mockedStatic
          .when(() -> ClientIpExtractor.getClientIpAddress(exchange))
          .thenReturn("127.0.0.1");

      // Act
      Mono<Void> result = loggingFilter.filter(exchange, chain);

      // Assert
      StepVerifier.create(result).verifyComplete();

      // Warten, bis Logs geschrieben wurden
      Awaitility.await()
          .atMost(1, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                // Überprüfen, ob ein WARN-Log mit der erwarteten Nachricht vorhanden ist
                assertTrue(
                    testAppender.contains("HTTP Request processed", Level.WARN),
                    "Es wurde kein erwarteter WARN-Log gefunden");
              });
    }
  }
}
