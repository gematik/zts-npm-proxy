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

package de.gematik.zts.npmproxy.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

class ClientIpExtractorTest {

  @Mock private ServerWebExchange exchange;
  @Mock private ServerHttpRequest request;
  @Mock private HttpHeaders headers;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(exchange.getRequest()).thenReturn(request);
    when(request.getHeaders()).thenReturn(headers);
  }

  @Test
  void testGetClientIpAddress_FromXForwardedForHeader() {
    String ip = "192.168.1.1";
    when(headers.getFirst("X-Forwarded-For")).thenReturn(ip);
    assertEquals(ip, ClientIpExtractor.getClientIpAddress(exchange));
  }

  @Test
  void testGetClientIpAddress_FromRemoteAddress() {
    String ip = "192.168.1.1";
    InetSocketAddress remoteAddress = new InetSocketAddress(ip, 8080);
    when(request.getRemoteAddress()).thenReturn(remoteAddress);
    when(headers.getFirst("X-Forwarded-For")).thenReturn(null);

    assertEquals(ip, ClientIpExtractor.getClientIpAddress(exchange));
  }

  @Test
  void testGetClientIpAddress_Unknown() {
    when(headers.getFirst("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddress()).thenReturn(null);

    String clientIp = ClientIpExtractor.getClientIpAddress(exchange);

    assertEquals("unknown", clientIp);
  }
}
