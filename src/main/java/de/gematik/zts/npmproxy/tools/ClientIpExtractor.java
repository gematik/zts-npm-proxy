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

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

public class ClientIpExtractor {

  private ClientIpExtractor() {
    // Utility-Klasse
  }

  /**
   * Gibt die IP-Adresse des Clients zurück, der die Anfrage an den Server gestellt hat. Die
   * IP-Adresse wird aus dem "X-Forwarded-For" Header oder der Remote-Adresse des Requests
   * extrahiert.
   *
   * @param exchange ServerWebExchange Objekt, das die Anfrage enthält
   * @return IP-Adresse des Clients
   */
  public static String getClientIpAddress(ServerWebExchange exchange) {
    ServerHttpRequest request = exchange.getRequest();

    // Prüfe "X-Forwarded-For" Header (das sollte der Standard für GCP sein)
    String xForwardedForHeader = request.getHeaders().getFirst("X-Forwarded-For");
    if (xForwardedForHeader != null && !xForwardedForHeader.isEmpty()) {
      return xForwardedForHeader.trim();
    }

    var remoteAddress = request.getRemoteAddress();

    // Wenn keine Proxy-Header vorhanden sind, die Remote-Adresse verwenden
    return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
  }
}
