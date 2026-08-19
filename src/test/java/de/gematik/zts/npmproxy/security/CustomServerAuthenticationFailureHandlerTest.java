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

import static de.gematik.zts.npmproxy.NpmProxyConstants.PROBLEMDETAILS_TITLE_UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CustomServerAuthenticationFailureHandlerTest {

  private static final String REQUEST_PATH = "/test";

  @InjectMocks private CustomServerAuthenticationFailureHandler handler;

  @Mock private WebFilterExchange webFilterExchange;
  @Mock private ServerWebExchange serverWebExchange;
  @Mock private AuthenticationException exception;

  private MockServerHttpResponse response;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    response = new MockServerHttpResponse();
    when(webFilterExchange.getExchange()).thenReturn(serverWebExchange);
    when(serverWebExchange.getResponse()).thenReturn(response);
    when(serverWebExchange.getRequest()).thenReturn(mock(ServerHttpRequest.class));
    when(serverWebExchange.getRequest().getPath()).thenReturn(mock(RequestPath.class));
    when(serverWebExchange.getRequest().getPath().value()).thenReturn(REQUEST_PATH);
  }

  @Test
  void testOnAuthenticationFailure() {

    // Vorbereiten der Exception-Message
    when(exception.getMessage()).thenReturn("Test error");

    Mono<Void> result = handler.onAuthenticationFailure(webFilterExchange, exception);

    StepVerifier.create(result).verifyComplete();

    // Response prüfen
    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());

    // Response-Body parsen
    ObjectMapper objectMapper = new ObjectMapper();
    ProblemDetail problemDetail = null;
    try {
      problemDetail =
          objectMapper.readValue(response.getBodyAsString().block(), ProblemDetail.class);
    } catch (Exception e) {
      fail("Error parsing response body", e);
    }

    // Response-Body prüfen
    assertNotNull(problemDetail);
    assertEquals(HttpStatus.UNAUTHORIZED.value(), problemDetail.getStatus());
    assertEquals(PROBLEMDETAILS_TITLE_UNAUTHORIZED, problemDetail.getTitle());
    assertEquals("Authentifizierungsfehler: " + exception.getMessage(), problemDetail.getDetail());
    assertEquals(REQUEST_PATH, problemDetail.getInstance().toString());
    assertNotNull(problemDetail.getProperties().get("timestamp"));
  }

  @Test
  void testOnAuthenticationFailureWithException()
      throws JsonProcessingException, IllegalAccessException, NoSuchFieldException {
    // Vorbereiten der Exception-Message
    when(exception.getMessage()).thenReturn("Test error");

    // Mock JsonProcessingException, um eine Exception auszulösen
    JsonProcessingException mockJsonProcessingException = mock(JsonProcessingException.class);
    when(mockJsonProcessingException.getMessage()).thenReturn("Test exception");

    // Mock ObjectMapper, um eine Exception auszulösen
    ObjectMapper mockObjectMapper = mock(ObjectMapper.class);
    when(mockObjectMapper.writeValueAsBytes(any(ProblemDetail.class)))
        .thenThrow(mockJsonProcessingException);

    // Injizieren des Mock-ObjectMappers über Reflection
    Field field = CustomServerAuthenticationFailureHandler.class.getDeclaredField("objectMapper");
    field.setAccessible(true);
    field.set(handler, mockObjectMapper);

    Mono<Void> result = handler.onAuthenticationFailure(webFilterExchange, exception);

    StepVerifier.create(result).verifyComplete();

    // Response prüfen
    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());

    // Response-Body prüfen
    assertEquals("{}", response.getBodyAsString().block());
  }
}
