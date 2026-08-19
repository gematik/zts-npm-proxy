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

package de.gematik.zts.npmproxy.ratelimit;

import static org.assertj.core.api.Assertions.*;

import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Slf4j
class RateLimitWebFilterTest {

  private static final int RATE_LIMIT = 2;
  private static final long WINDOW_DURATION = 1000L;
  private static final String HEALTH_PATH = "/health";
  private static final String API_KEY_HEADER = "X-API-Key";
  private static final String API_KEY = "S0m3$up3Rs3cr3tK3y";
  private NpmProxyConfiguration mockConfig;
  private MockServerWebExchange mockExchange;
  private WebFilterChain mockChain;

  // ================================================================================
  // Setup (gültig für einen Großteil der Tests)
  // ================================================================================

  @BeforeEach
  void setUp() {
    // Mock Configuration
    mockConfig = Mockito.mock(NpmProxyConfiguration.class);
    Mockito.when(mockConfig.isRateLimitingEnabled()).thenReturn(true);
    Mockito.when(mockConfig.getRateLimitingLimit()).thenReturn(RATE_LIMIT);
    Mockito.when(mockConfig.getRateLimitingWindowDuration()).thenReturn(WINDOW_DURATION);
    Mockito.when(mockConfig.getHealthPath()).thenReturn(HEALTH_PATH);

    // Mock Exchange und Chain
    mockExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    mockChain = Mockito.mock(WebFilterChain.class);
    Mockito.when(mockChain.filter(Mockito.any(MockServerWebExchange.class)))
        .thenReturn(Mono.empty());
  }

  // ================================================================================
  // Tests
  // ================================================================================

  @Test
  void testApiKeyProvidedNotRateLimited() {

    Mockito.when(mockConfig.getRateLimitingApiKeyHeader()).thenReturn(API_KEY_HEADER);
    Mockito.when(mockConfig.getRateLimitingApiKey()).thenReturn(API_KEY);
    // mock Exchange mit API-Key-Header
    mockExchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test").header(API_KEY_HEADER, API_KEY).build());
    // Filter für Rate Limiting erstellen
    RateLimitWebFilter rateLimitWebFilter = new RateLimitWebFilter(mockConfig);

    // Simuliere mehr als die maximal zulässige Anzahl an Requests von der Whitelist-IP
    for (int i = 0; i < RATE_LIMIT * 2; i++) {
      rateLimitWebFilter.filter(mockExchange, mockChain).block();
      // Stelle sicher, dass kein Fehlerstatus gesetzt wurde
      assertThat(mockExchange.getResponse().getStatusCode())
          .as("Status code should not be set if API key is provided")
          .isNull();
    }
  }

  @Test
  void testEmptyApiKeyProvidedRateLimitExceeded() {

    Mockito.when(mockConfig.getRateLimitingApiKeyHeader()).thenReturn(API_KEY_HEADER);
    Mockito.when(mockConfig.getRateLimitingApiKey()).thenReturn("");
    // mock Exchange mit API-Key-Header
    mockExchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test").header(API_KEY_HEADER, "").build());
    // Filter für Rate Limiting erstellen
    RateLimitWebFilter rateLimitWebFilter = new RateLimitWebFilter(mockConfig);

    // Simuliere mehr als die maximal zulässige Anzahl an Requests von der Whitelist-IP
    for (int i = 0; i < RATE_LIMIT; i++) {
      rateLimitWebFilter.filter(mockExchange, mockChain).block();
      // Stelle sicher, dass kein Fehlerstatus gesetzt wurde
      assertThat(mockExchange.getResponse().getStatusCode())
          .as("Status code should not be set if API key is provided")
          .isNull();
    }

    // Führe einen weiteren Request aus, der das Rate Limit überschreitet
    Mono<Void> result = rateLimitWebFilter.filter(mockExchange, mockChain);
    StepVerifier.create(result).expectComplete().verify();

    // Prüfe den Statuscode der Response
    assertThat(mockExchange.getResponse().getStatusCode())
        .as("429 status code expected, when rate limit is exceeded")
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

    // Prüfe den Content-Type der Response
    assertThat(mockExchange.getResponse().getHeaders().getContentType())
        .as("Content type should be JSON")
        .isEqualTo(MediaType.APPLICATION_JSON);

    // Prüfe den Response-Body
    String responseBody = mockExchange.getResponse().getBodyAsString().block();
    assertThat(responseBody)
        .as("Response body should contain error message")
        .isNotNull()
        .contains("Zu viele Anfragen!");
  }

  @Test
  void testRateLimitDisabled() {

    // Deaktiviere das Rate Limiting in der Konfiguration
    Mockito.when(mockConfig.isRateLimitingEnabled()).thenReturn(false);

    // Filter für Rate Limiting erstellen
    RateLimitWebFilter rateLimitWebFilter = new RateLimitWebFilter(mockConfig);

    // Simuliere mehr als die maximal zulässige Anzahl an Requests vom gleichen Client
    for (int i = 0; i < RATE_LIMIT * 2; i++) {
      rateLimitWebFilter.filter(mockExchange, mockChain).block();
      // Stelle sicher, dass kein Fehlerstatus gesetzt wurde
      assertThat(mockExchange.getResponse().getStatusCode())
          .as("Status code should not be set")
          .isNull();
    }
  }

  @Test
  void testHealthPathIsIgnored() {

    // Filter für Rate Limiting erstellen
    RateLimitWebFilter rateLimitWebFilter = new RateLimitWebFilter(mockConfig);

    // Mock Exchange und Chain
    mockExchange = MockServerWebExchange.from(MockServerHttpRequest.get(HEALTH_PATH).build());
    mockChain = Mockito.mock(WebFilterChain.class);
    Mockito.when(mockChain.filter(mockExchange)).thenReturn(Mono.empty());

    // Simuliere mehr als die maximal zulässige Anzahl an Requests vom gleichen Client
    for (int i = 0; i < RATE_LIMIT * 2; i++) {
      rateLimitWebFilter.filter(mockExchange, mockChain).block();

      // Stelle sicher, dass kein Fehlerstatus gesetzt wurde
      assertThat(mockExchange.getResponse().getStatusCode())
          .as("Status code should be null, as the health path is not rate limited")
          .isNull();
    }
  }

  @Test
  void testRateLimitExceeded() {

    // Filter für Rate Limiting erstellen
    RateLimitWebFilter rateLimitWebFilter = new RateLimitWebFilter(mockConfig);

    // Simuliere die maximal zulässige Anzahl an Requests vom gleichen Client
    for (int i = 0; i < RATE_LIMIT; i++) {
      rateLimitWebFilter.filter(mockExchange, mockChain).block();
      // Stelle sicher, dass kein Fehlerstatus gesetzt wurde
      assertThat(mockExchange.getResponse().getStatusCode())
          .as("Status code should not be set while within rate limit")
          .isNull();
    }

    // Führe einen weiteren Request aus, der das Rate Limit überschreitet
    Mono<Void> result = rateLimitWebFilter.filter(mockExchange, mockChain);
    StepVerifier.create(result).expectComplete().verify();

    // Prüfe den Statuscode der Response
    assertThat(mockExchange.getResponse().getStatusCode())
        .as("429 status code expected, when rate limit is exceeded")
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

    // Prüfe den Content-Type der Response
    assertThat(mockExchange.getResponse().getHeaders().getContentType())
        .as("Content type should be JSON")
        .isEqualTo(MediaType.APPLICATION_JSON);

    // Prüfe den Response-Body
    String responseBody = mockExchange.getResponse().getBodyAsString().block();
    assertThat(responseBody)
        .as("Response body should contain error message")
        .isNotNull()
        .contains("Zu viele Anfragen!");
  }

  @Test
  void testWithinRateLimit() {

    // Filter für Rate Limiting erstellen
    RateLimitWebFilter rateLimitWebFilter = new RateLimitWebFilter(mockConfig);

    // Simuliere die maximal zulässige Anzahl an Requests vom gleichen Client
    for (int i = 0; i < RATE_LIMIT; i++) {
      Mono<Void> result = rateLimitWebFilter.filter(mockExchange, mockChain);
      StepVerifier.create(result).expectComplete().verify();

      // Stelle sicher, dass kein Fehlerstatus gesetzt wurde
      assertThat(mockExchange.getResponse().getStatusCode())
          .as("Status code should not be set")
          .isNull();
    }
  }

  @Test
  void testRateLimitReset() {

    // Filter für Rate Limiting erstellen
    RateLimitWebFilter rateLimitWebFilter = new RateLimitWebFilter(mockConfig);

    // Simuliere die maximal zulässige Anzahl an Requests vom gleichen Client
    for (int i = 0; i < RATE_LIMIT; i++) {
      Mono<Void> result = rateLimitWebFilter.filter(mockExchange, mockChain);
      StepVerifier.create(result).expectComplete().verify();

      // Stelle sicher, dass kein Fehlerstatus gesetzt wurde
      assertThat(mockExchange.getResponse().getStatusCode())
          .as("Status code should not be set")
          .isNull();
    }

    // Warte bis das konfigurierte Zeitfenster abgelaufen ist
    StepVerifier.create(Mono.delay(Duration.ofMillis(WINDOW_DURATION + 100)).then())
        .expectComplete()
        .verify();

    // Simuliere einen weiteren Request
    Mono<Void> result = rateLimitWebFilter.filter(mockExchange, mockChain);
    StepVerifier.create(result).expectComplete().verify();

    // Stelle sicher, dass kein Fehlerstatus gesetzt wurde
    assertThat(mockExchange.getResponse().getStatusCode())
        .as("Status code should not be set")
        .isNull();
  }
}
