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

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
class RequestSizeWebFilterTest {

  private static final String BODY_CONTENT = "Terminologieserver finde ich toll!";

  private final RequestSizeWebFilter requestSizeWebFilter = new RequestSizeWebFilter();
  private WebFilterChain bodyConsumingChain;

  @BeforeEach
  void setUp() {

    // Mock FilterChain
    bodyConsumingChain =
        filterExchange -> {

          // Damit der Filter das tut, was er soll, müssen wir erst den Body konsumieren
          filterExchange
              .getRequest()
              .getBody()
              .map(
                  dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    return new String(bytes);
                  })
              .blockFirst();

          return Mono.empty();
        };
  }

  @Test
  void testRequestSizePost() {
    // Mock Exchange mit einer Anfrage mit Body
    byte[] bodyContent = BODY_CONTENT.getBytes();
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/test")
                .body(Flux.just(new DefaultDataBufferFactory().wrap(bodyContent))));

    // Führe den Filter aus
    requestSizeWebFilter.filter(exchange, bodyConsumingChain);

    // Prüfe, ob das Attribut gesetzt wurde
    assertTrue(
        exchange.getAttributes().containsKey(RequestSizeWebFilter.REQUEST_SIZE),
        "Request size should be set for POST requests");
    int requestSize = (int) exchange.getAttributes().get(RequestSizeWebFilter.REQUEST_SIZE);
    assertEquals(bodyContent.length, requestSize, "Request size does not match body size");
  }

  @Test
  void testRequestSizeEmptyPost() {
    // Mock Exchange mit einer Anfrage mit Body
    byte[] bodyContent = "".getBytes(); // 13 Bytes
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/test")
                .body(Flux.just(new DefaultDataBufferFactory().wrap(bodyContent))));

    // Führe den Filter aus
    requestSizeWebFilter.filter(exchange, bodyConsumingChain);

    // Prüfe, ob das Attribut gesetzt wurde
    assertTrue(
        exchange.getAttributes().containsKey(RequestSizeWebFilter.REQUEST_SIZE),
        "Request size should be set for POST requests");
    int requestSize = (int) exchange.getAttributes().get(RequestSizeWebFilter.REQUEST_SIZE);
    assertEquals(bodyContent.length, requestSize, "Request size does not match body size");
  }

  @Test
  void testRequestSizeGet() {
    // Mock Exchange mit einer Anfrage ohne Body
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

    // Führe den Filter aus
    requestSizeWebFilter.filter(exchange, bodyConsumingChain);

    // Für GET Requests sollte die Request-Größe nicht gesetzt werden, da wir keinen Body haben
    assertFalse(
        exchange.getAttributes().containsKey(RequestSizeWebFilter.REQUEST_SIZE),
        "Request size should not be set for GET requests");
  }
}
