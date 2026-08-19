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

import static de.gematik.zts.npmproxy.NpmProxyConstants.PROBLEMDETAILS_TITLE_TOO_MANY_REQUESTS;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.tools.ClientIpExtractor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Simple Implementierung, um die Anzahl an Requests pro Zeitfenster und IP zu begrenzen.
 * Perspektivisch kann man hier sicherlich auch noch auf eine spezialisierte Bibliothek wechseln,
 */
@Slf4j
@Component
@Order(1)
public class RateLimitWebFilter implements WebFilter {

  private final NpmProxyConfiguration config;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final ConcurrentHashMap<String, RequestData> requestCounts = new ConcurrentHashMap<>();

  private final boolean rateLimitingEnabled;
  private final int limit;
  private final long windowDuration;
  private final String apiKey;

  @Autowired
  public RateLimitWebFilter(NpmProxyConfiguration config) {
    this.config = config;
    rateLimitingEnabled = config.isRateLimitingEnabled();
    limit = config.getRateLimitingLimit();
    windowDuration = config.getRateLimitingWindowDuration();
    apiKey = StringUtils.trim(config.getRateLimitingApiKey());

    log.info("Rate limiting enabled: {}", rateLimitingEnabled);
    if (rateLimitingEnabled) {
      log.info("limit (count): {}", limit);
      log.info("window (ms): {}", windowDuration);
      if (StringUtils.isEmpty(apiKey)) {
        log.warn("API key: not set or empty");
      } else {
        log.info("API key header set to {}", config.getRateLimitingApiKeyHeader());
        log.info("API key: set");
      }
    }
  }

  @Override
  @NonNull
  public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {

    if (rateLimitingEnabled
        && !exchange.getRequest().getPath().toString().equals(config.getHealthPath())) {

      String clientApiKey =
          exchange.getRequest().getHeaders().getFirst(config.getRateLimitingApiKeyHeader());

      if (StringUtils.isNotEmpty(clientApiKey) && clientApiKey.equals(apiKey)) {
        return chain.filter(exchange);
      }

      // Client IP-Adresse aus Request extrahieren
      String clientIp = ClientIpExtractor.getClientIpAddress(exchange);

      RequestData requestData = requestCounts.computeIfAbsent(clientIp, k -> new RequestData());

      synchronized (requestData) {
        // Prüfen, ob das Zeitfenster abgelaufen ist
        if (Instant.now().toEpochMilli() - requestData.startTime >= windowDuration) {
          requestData.reset(); // Zähler zurücksetzen und neues Zeitfenster starten
        }

        if (requestData.requestCount.incrementAndGet() > limit) {

          // Hinweis:
          // Wir behandeln die Ausnahme an dieser Stelle ganz bewusst manuell und erzeugen eine
          // eigene
          // Response. Es wird keine Exception geworfen, da diese sonst in einem WebExceptionHandler
          // landen würde und wir dann keinen validen Log-Eintrag mehr bekommen (Hintergrund: Die
          // Filterkette wird dann verlassen und wir hätten die Info nur noch in der doFinally
          // Methode des Filters, welche allerdings nicht in der erwarteten Reihenfolge aufgerufen
          // wird).
          var response = exchange.getResponse();
          response.setStatusCode(HttpStatusCode.valueOf(429));
          response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
          return response.writeWith(
              Mono.fromSupplier(
                  () -> {
                    try {

                      ProblemDetail problemDetail =
                          ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
                      problemDetail.setTitle(PROBLEMDETAILS_TITLE_TOO_MANY_REQUESTS);
                      problemDetail.setDetail(
                          "Zu viele Anfragen! Bitte reduzieren Sie die Frequenz ihrer Anfragen.");
                      problemDetail.setInstance(
                          URI.create(exchange.getRequest().getPath().value()));
                      problemDetail.setProperties(Map.of("timestamp", System.currentTimeMillis()));

                      byte[] bytes = objectMapper.writeValueAsBytes(problemDetail);
                      return response.bufferFactory().wrap(bytes);
                    } catch (Exception e) {
                      log.error("Error writing response: {}", e.getMessage(), e);
                      return response.bufferFactory().wrap("{}".getBytes(StandardCharsets.UTF_8));
                    }
                  }));
        }
      }
    }

    return chain.filter(exchange);
  }

  // Klasse zum Speichern der Anfragedaten für jede IP
  private static class RequestData {
    private final AtomicInteger requestCount = new AtomicInteger(0); // Zähler für die Anfragen
    private long startTime = Instant.now().toEpochMilli(); // Startzeit des Zeitfensters

    public void reset() {
      requestCount.set(0);
      startTime = Instant.now().toEpochMilli();
    }
  }
}
