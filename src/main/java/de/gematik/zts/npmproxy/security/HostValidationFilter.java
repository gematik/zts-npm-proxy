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

import java.util.HashSet;
import java.util.Set;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(1)
@Slf4j
public class HostValidationFilter implements WebFilter {

  private static final Set<String> LOCALHOST_EQUIVALENTS = Set.of("localhost", "127.0.0.1");
  private final Set<String> allowedHostnames;
  private final String healthPath;

  public HostValidationFilter(
      @Value("${proxy.hostname}") String configuredHostname,
      @Value("${proxy.health-path:/api/health}") String healthPath) {

    this.allowedHostnames = new HashSet<>();
    this.healthPath = healthPath;
    // Strip protocol and port from configured hostname
    String cleanHostname = configuredHostname.replaceFirst("^https?://", "").split(":")[0];

    allowedHostnames.add(cleanHostname);

    // If configured as localhost/127.0.0.1, treat them as equivalent
    if (LOCALHOST_EQUIVALENTS.contains(cleanHostname)) {
      allowedHostnames.addAll(LOCALHOST_EQUIVALENTS);
    }

    log.info("Host validation filter initialized. Allowed hosts: {}", allowedHostnames);
  }

  @Override
  @NonNull
  public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {

    String path = exchange.getRequest().getPath().value();
    // Skip host validation for health check endpoint and its subpaths
    if (path.equals(healthPath) || path.startsWith(healthPath + "/")) {
      log.debug("Skipping host validation for health check endpoint: {}", path);
      return chain.filter(exchange);
    }

    String host = exchange.getRequest().getHeaders().getFirst("Host");

    if (host == null) {
      log.warn("Request without Host header from: {}", exchange.getRequest().getRemoteAddress());
      exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
      return exchange.getResponse().setComplete();
    }

    // Remove port if present for comparison
    String hostWithoutPort = host.split(":")[0];

    if (!allowedHostnames.contains(hostWithoutPort)) {
      log.warn(
          "Request from unauthorized host: {} (allowed: {}), IP: {}",
          host,
          allowedHostnames,
          exchange.getRequest().getRemoteAddress());
      exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
      return exchange.getResponse().setComplete();
    }

    log.debug("Request from allowed host: {}", host);
    return chain.filter(exchange);
  }
}
