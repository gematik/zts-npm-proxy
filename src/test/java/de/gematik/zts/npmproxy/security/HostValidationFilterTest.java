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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class HostValidationFilterTest {

  @Mock private ServerWebExchange exchange;
  @Mock private ServerHttpRequest request;
  @Mock private ServerHttpResponse response;
  @Mock private WebFilterChain chain;
  @Mock private HttpHeaders headers;

  private HostValidationFilter filter;

  @BeforeEach
  void setUp() {
    lenient().when(exchange.getRequest()).thenReturn(request);
    lenient().when(exchange.getResponse()).thenReturn(response);
    lenient().when(request.getHeaders()).thenReturn(headers);
    lenient().when(response.setComplete()).thenReturn(Mono.empty());
    lenient().when(chain.filter(exchange)).thenReturn(Mono.empty());
  }

  private void mockRequestPath(String path) {
    RequestPath requestPath = mock(RequestPath.class);
    lenient().when(requestPath.value()).thenReturn(path);
    lenient().when(request.getPath()).thenReturn(requestPath);
  }

  @Nested
  class WithStandardConfiguration {

    @BeforeEach
    void setUp() {
      filter = new HostValidationFilter("dev.terminologien.bfarm.de", "/api/health");
      mockRequestPath("/api/proxy");
    }

    @Test
    void shouldAllowRequestWithConfiguredHostname() {
      // Given
      when(headers.getFirst("Host")).thenReturn("dev.terminologien.bfarm.de");

      // When
      Mono<Void> result = filter.filter(exchange, chain);

      // Then
      StepVerifier.create(result).verifyComplete();
      verify(chain).filter(exchange);
      verify(response, never()).setStatusCode(any());
    }

    @Test
    void shouldAllowRequestWithConfiguredHostnameAndPort() {
      // Given
      when(headers.getFirst("Host")).thenReturn("dev.terminologien.bfarm.de:8080");

      // When
      Mono<Void> result = filter.filter(exchange, chain);

      // Then
      StepVerifier.create(result).verifyComplete();
      verify(chain).filter(exchange);
      verify(response, never()).setStatusCode(any());
    }

    @Test
    void shouldRejectRequestWithUnauthorizedHost() {
      // Given
      when(headers.getFirst("Host")).thenReturn("malicious.example.com");
      when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.1", 12345));

      // When
      Mono<Void> result = filter.filter(exchange, chain);

      // Then
      StepVerifier.create(result).verifyComplete();
      verify(response).setStatusCode(HttpStatus.FORBIDDEN);
      verify(chain, never()).filter(exchange);
    }

    @Test
    void shouldRejectRequestWithPodIp() {
      // Given
      when(headers.getFirst("Host")).thenReturn("10.1.1.9");
      when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("35.191.237.74", 44640));

      // When
      Mono<Void> result = filter.filter(exchange, chain);

      // Then
      StepVerifier.create(result).verifyComplete();
      verify(response).setStatusCode(HttpStatus.FORBIDDEN);
      verify(chain, never()).filter(exchange);
    }

    @Test
    void shouldRejectRequestWithoutHostHeader() {
      // Given
      when(headers.getFirst("Host")).thenReturn(null);
      when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.1", 12345));

      // When
      Mono<Void> result = filter.filter(exchange, chain);

      // Then
      StepVerifier.create(result).verifyComplete();
      verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
      verify(chain, never()).filter(exchange);
    }
  }

  @Nested
  class WithLocalhostConfiguration {

    @BeforeEach
    void setUp() {
      filter = new HostValidationFilter("localhost", "/api/health");
      mockRequestPath("/api/proxy");
    }

    @ParameterizedTest(name = "should allow host: {0}")
    @ValueSource(strings = {"localhost", "127.0.0.1", "localhost:8080"})
    void shouldAllowLocalhostEquivalents(String hostHeader) {
      // Given
      when(headers.getFirst("Host")).thenReturn(hostHeader);

      // When
      Mono<Void> result = filter.filter(exchange, chain);

      // Then
      StepVerifier.create(result).verifyComplete();
      verify(chain).filter(exchange);
    }
  }

  @Nested
  class WithHealthCheckEndpoint {

    @BeforeEach
    void setUp() {
      filter = new HostValidationFilter("dev.terminologien.bfarm.de", "/api/health");
    }

    @ParameterizedTest(name = "should bypass validation for path: {0}")
    @ValueSource(strings = {"/api/health", "/api/health/readiness", "/api/health/liveness"})
    void shouldBypassValidationForHealthPaths(String path) {
      // Given
      mockRequestPath(path);
      lenient().when(headers.getFirst("Host")).thenReturn("10.1.1.9"); // Pod IP

      // When
      Mono<Void> result = filter.filter(exchange, chain);

      // Then
      StepVerifier.create(result).verifyComplete();
      verify(chain).filter(exchange);
      verify(response, never()).setStatusCode(any());
    }

    @Test
    void shouldNotBypassValidationForSimilarPath() {
      // Given
      mockRequestPath("/api/health-check"); // Different path
      when(headers.getFirst("Host")).thenReturn("10.1.1.9");
      when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("35.191.237.74", 44640));

      // When
      Mono<Void> result = filter.filter(exchange, chain);

      // Then
      StepVerifier.create(result).verifyComplete();
      verify(response).setStatusCode(HttpStatus.FORBIDDEN);
      verify(chain, never()).filter(exchange);
    }

    @Test
    void shouldNotBypassValidationForPathContainingHealthPath() {
      // Given
      mockRequestPath("/api/other/api/health"); // Contains health path but not at start
      when(headers.getFirst("Host")).thenReturn("10.1.1.9");
      when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("35.191.237.74", 44640));

      // When
      Mono<Void> result = filter.filter(exchange, chain);

      // Then
      StepVerifier.create(result).verifyComplete();
      verify(response).setStatusCode(HttpStatus.FORBIDDEN);
      verify(chain, never()).filter(exchange);
    }
  }

  @Nested
  class WithCustomHealthPath {

    @BeforeEach
    void setUp() {
      filter = new HostValidationFilter("dev.terminologien.bfarm.de", "/actuator/health");
    }

    @Test
    void shouldBypassValidationForCustomHealthPath() {
      // Given
      mockRequestPath("/actuator/health");
      lenient().when(headers.getFirst("Host")).thenReturn("10.1.1.9");

      // When
      Mono<Void> result = filter.filter(exchange, chain);

      // Then
      StepVerifier.create(result).verifyComplete();
      verify(chain).filter(exchange);
      verify(response, never()).setStatusCode(any());
    }

    @Test
    void shouldBypassValidationForCustomHealthSubpath() {
      // Given
      mockRequestPath("/actuator/health/liveness");
      lenient().when(headers.getFirst("Host")).thenReturn("10.1.1.9");

      // When
      Mono<Void> result = filter.filter(exchange, chain);

      // Then
      StepVerifier.create(result).verifyComplete();
      verify(chain).filter(exchange);
      verify(response, never()).setStatusCode(any());
    }

    @Test
    void shouldNotBypassValidationForDefaultHealthPath() {
      // Given
      mockRequestPath("/api/health");
      when(headers.getFirst("Host")).thenReturn("10.1.1.9");
      when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("35.191.237.74", 44640));

      // When
      Mono<Void> result = filter.filter(exchange, chain);

      // Then
      StepVerifier.create(result).verifyComplete();
      verify(response).setStatusCode(HttpStatus.FORBIDDEN);
      verify(chain, never()).filter(exchange);
    }
  }

  @Nested
  class WithProtocolAndPortInConfiguration {

    @ParameterizedTest(name = "should strip configuration: {0}")
    @ValueSource(
        strings = {
          "https://dev.terminologien.bfarm.de",
          "http://dev.terminologien.bfarm.de",
          "dev.terminologien.bfarm.de:8080",
          "https://dev.terminologien.bfarm.de:8443"
        })
    void shouldStripProtocolAndPortFromConfiguration(String configuredHostname) {
      // Given
      filter = new HostValidationFilter(configuredHostname, "/api/health");
      mockRequestPath("/api/proxy");
      when(headers.getFirst("Host")).thenReturn("dev.terminologien.bfarm.de");

      // When
      Mono<Void> result = filter.filter(exchange, chain);

      // Then
      StepVerifier.create(result).verifyComplete();
      verify(chain).filter(exchange);
    }
  }
}
