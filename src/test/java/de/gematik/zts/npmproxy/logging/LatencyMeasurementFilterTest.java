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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class LatencyMeasurementFilterTest {

  private MockServerWebExchange mockExchange;
  private WebFilterChain mockChain;

  @BeforeEach
  void setUp() {
    // Mock Exchange und Chain
    mockExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    mockChain = Mockito.mock(WebFilterChain.class);
    Mockito.when(mockChain.filter(mockExchange)).thenReturn(Mono.empty());
  }

  @Test
  void testFilter() {

    LatencyMeasurementFilter latencyMeasurementFilter = new LatencyMeasurementFilter();
    latencyMeasurementFilter.filter(mockExchange, mockChain).block();

    assertNotNull(
        mockExchange.getAttributes().get(LatencyMeasurementFilter.LATENCY),
        "Latency Information in exchange must not be null");
    assertTrue(
        mockExchange
            .getAttributes()
            .get(LatencyMeasurementFilter.LATENCY)
            .toString()
            .matches("^\\d+\\.\\d{4}s$"),
        "Latency Information in exchange must match Pattern: ^\\d+\\.\\d{4}s$");
  }
}
