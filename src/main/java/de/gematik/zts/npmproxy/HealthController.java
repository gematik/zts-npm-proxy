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

package de.gematik.zts.npmproxy;

import de.gematik.zts.npmproxy.exceptions.ServiceUnavailableException;
import de.gematik.zts.npmproxy.model.HealthStatus;
import de.gematik.zts.npmproxy.repository.LuceneBackedPackageRepository;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Implementiert die von Außen aufrufbaren HEALTH-Operationen für den FHIR-NPM-Proxy. */
@RestController
@Validated
@Order(2)
public class HealthController {

  private final LuceneBackedPackageRepository packageRepository;

  public HealthController(LuceneBackedPackageRepository packageRepository) {
    this.packageRepository = packageRepository;
  }

  /**
   * Abrufen des Readiness Status (Bedingung, ab wann der Service initialisiert wurde und bereits
   * ist Anfragen entgegenzunehmen)
   *
   * @return Readiness Response, die an den Client zurückgegeben wird
   */
  @GetMapping(
      value = "${proxy.health-path:/api/health}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> readiness() {

    // Abbruch der Verarbeitung, falls noch kein initiales Update der Paketinformationen
    // durchgeführt wurde
    if (!packageRepository.isInitialUpdateSucceeded()) {

      return Mono.error(
          new ServiceUnavailableException(
              "Der Dienst wurde nicht korrekt initialisiert. Bitte versuchen Sie es später erneut."));

    } else {

      return Mono.just(
          ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new HealthStatus("UP")));
    }
  }

  /**
   * Abrufen des Liveness Status (Bedingung, ob der Service lebt und sich nicht bspw. in einer
   * Deadlock-Situation befindet)
   *
   * @return Liveness Response, die an den Client zurückgegeben wird
   */
  @GetMapping(value = "/api/health/liveness", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> liveness() {
    return Mono.just(
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new HealthStatus("UP")));
  }
}
