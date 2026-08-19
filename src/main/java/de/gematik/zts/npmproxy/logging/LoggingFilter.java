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

import static de.gematik.zts.npmproxy.NpmProxyConstants.*;
import static de.gematik.zts.npmproxy.logging.ResponseSizeWebFilter.RESPONSE_SIZE;
import static net.logstash.logback.marker.Markers.append;

import de.gematik.zts.npmproxy.tools.ClientIpExtractor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.marker.LogstashMarker;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingFilter implements WebFilter {

  @Override
  @NonNull
  public Mono<Void> filter(@NonNull ServerWebExchange exchange, WebFilterChain chain) {
    return chain.filter(exchange).doOnTerminate(() -> logRequest(exchange));
  }

  private void logRequest(ServerWebExchange exchange) {
    String clientIp = ClientIpExtractor.getClientIpAddress(exchange);
    String user = getUser(exchange);
    String logMessage = getLogMessage(exchange);
    String requestSize = getRequestSize(exchange);
    String responseSize = getResponseSize(exchange);
    String latency = getLatency(exchange);

    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();

    HttpRequestLogEntry entry =
        HttpRequestLogEntry.builder()
            .remoteIp(clientIp)
            .requestUrl(exchange.getRequest().getURI().toString())
            .requestMethod(exchange.getRequest().getMethod().name())
            .status(statusCode != null ? statusCode.value() : 0)
            .protocol(exchange.getRequest().getURI().getScheme())
            .requestSize(requestSize)
            .responseSize(responseSize)
            .userAgent(exchange.getRequest().getHeaders().getFirst("User-Agent"))
            .latency(latency)
            .build();

    LogstashMarker marker = append("user", user).and(append("httpRequest", entry));

    if (statusCode == null || statusCode.isError()) {
      Exception exception = getException(exchange);
      log.warn(marker, exception != null ? exception.getMessage() : logMessage, exception);
    } else {
      log.info(marker, "{}", logMessage);
    }
  }

  private Exception getException(ServerWebExchange exchange) {
    return exchange.getAttributes().containsKey(ATTRIBUTE_EXCEPTION)
        ? (Exception) exchange.getAttributes().get(ATTRIBUTE_EXCEPTION)
        : null;
  }

  private String getUser(ServerWebExchange exchange) {
    return exchange.getAttributes().containsKey(ATTRIBUTE_USER)
        ? exchange.getAttributes().get(ATTRIBUTE_USER).toString()
        : "invalid/not-needed";
  }

  private String getLogMessage(ServerWebExchange exchange) {
    return exchange.getAttributes().containsKey(ATTRIBUTE_LOG_MESSAGE)
        ? exchange.getAttributes().get(ATTRIBUTE_LOG_MESSAGE).toString()
        : "HTTP Request processed";
  }

  private String getRequestSize(ServerWebExchange exchange) {
    return exchange.getAttributes().containsKey(RequestSizeWebFilter.REQUEST_SIZE)
        ? exchange.getAttributes().get(RequestSizeWebFilter.REQUEST_SIZE).toString()
        : "0";
  }

  private String getResponseSize(ServerWebExchange exchange) {
    if (exchange.getResponse().getHeaders().getContentLength() != -1) {
      return String.valueOf(exchange.getResponse().getHeaders().getContentLength());
    }
    return exchange.getAttributes().containsKey(RESPONSE_SIZE)
        ? exchange.getAttributes().get(RESPONSE_SIZE).toString()
        : "0";
  }

  private String getLatency(ServerWebExchange exchange) {
    return exchange.getAttributes().containsKey(LatencyMeasurementFilter.LATENCY)
        ? exchange.getAttributes().get(LatencyMeasurementFilter.LATENCY).toString()
        : "0s";
  }
}
