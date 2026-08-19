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

import org.reactivestreams.Publisher;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Order(0)
public class ResponseSizeWebFilter implements WebFilter {

  public static final String RESPONSE_SIZE = "responseSize";

  /**
   * Filter, der die Größe der Response berechnet und in den Exchange schreibt. Hinweis: Dieser
   * Filter wird ausschließlich während der Rückgabe von Flux verwendet, da für Mono die Berechnung
   * durch das Framework erfolgt und in den Response-Header geschrieben wird.
   *
   * @param exchange Der ServerWebExchange
   * @param chain Die WebFilterChain
   * @return Mono<Void>
   */
  @Override
  @NonNull
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerHttpResponseDecorator decoratedResponse =
        new ServerHttpResponseDecorator(exchange.getResponse()) {

          private int responseSize = 0;

          @Override
          @NonNull
          public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {

            // Wenn der Body ein Flux ist, dann berechnen wir die Größe. Für Mono wird die Größe
            // durch das Framework berechnet und in den Response-Header geschrieben.
            if (body instanceof Flux<? extends DataBuffer> fluxBody) {

              return super.writeWith(
                  fluxBody
                      .map(
                          dataBuffer -> {
                            // Berechne die Größe des serialisierten Outputs
                            responseSize += dataBuffer.readableByteCount();
                            // Gib den DataBuffer unverändert zurück
                            return dataBuffer;
                          })
                      .doFinally(
                          signalType -> exchange.getAttributes().put(RESPONSE_SIZE, responseSize)));
            }

            return super.writeWith(body);
          }
        };

    return chain.filter(exchange.mutate().response(decoratedResponse).build());
  }
}
