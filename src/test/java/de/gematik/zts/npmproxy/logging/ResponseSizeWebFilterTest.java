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
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
class ResponseSizeWebFilterTest {

  private static final String BODY_CONTENT = "Terminologieserver finde ich toll!";

  private final ResponseSizeWebFilter responseSizeWebFilter = new ResponseSizeWebFilter();
  

  @Test
  void testResponseSizeWithFluxBody() {
    // Mock Exchange
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

    // Mock FilterChain
    WebFilterChain responseWritingChain =
        filterExchange -> {

          // Damit der Filter das tut, was er soll, müssen wir erst den Body schreiben
          filterExchange
              .getResponse()
              .writeWith(Flux.just(new DefaultDataBufferFactory().wrap(BODY_CONTENT.getBytes())))
              .block();

          return Mono.empty();
        };

    // Führe den Filter aus
    responseSizeWebFilter.filter(exchange, responseWritingChain).block();

    // Prüfe, ob das Response-Size-Attribut gesetzt wurde
    assertTrue(
        exchange.getAttributes().containsKey(ResponseSizeWebFilter.RESPONSE_SIZE),
        "Response size attribute should be set");
    int responseSize = (int) exchange.getAttributes().get(ResponseSizeWebFilter.RESPONSE_SIZE);
    assertEquals(BODY_CONTENT.length(), responseSize, "Response size does not match body size");
  }

  @Test
  void testResponseSizeWithMonoBody() {
    // Mock Exchange
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

    // Mock FilterChain
    WebFilterChain responseWritingChain =
        filterExchange -> {

          // Damit der Filter das tut, was er soll, müssen wir erst den Body schreiben
          filterExchange
              .getResponse()
              .writeWith(Mono.just(new DefaultDataBufferFactory().wrap(BODY_CONTENT.getBytes())))
              .block();

          return Mono.empty();
        };

    // Führe den Filter aus
    responseSizeWebFilter.filter(exchange, responseWritingChain).block();

    // Prüfe, ob das Attribut nicht gesetzt wurde
    assertFalse(
        exchange.getAttributes().containsKey(ResponseSizeWebFilter.RESPONSE_SIZE),
        "Response size attribute should not be set");
  }
}
