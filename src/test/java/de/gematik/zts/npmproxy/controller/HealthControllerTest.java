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

package de.gematik.zts.npmproxy.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.zts.npmproxy.HealthController;
import de.gematik.zts.npmproxy.exceptions.ServiceUnavailableException;
import de.gematik.zts.npmproxy.model.HealthStatus;
import de.gematik.zts.npmproxy.repository.LuceneBackedPackageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

  @InjectMocks private HealthController healthController;
  @Mock private LuceneBackedPackageRepository packageRepository;

  @Test
  void testReadiness_healty() {

    // Mocking des Repository Status vorbereiten
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(true);

    // health Methode aufrufen und Ergebnis prüfen
    Mono<ResponseEntity<Object>> responseMono = healthController.readiness();
    StepVerifier.create(responseMono)
        .assertNext(
            response -> {
              assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
              assertInstanceOf(HealthStatus.class, response.getBody());
            })
        .verifyComplete();
  }

  @Test
  void testReadiness_uninitialized() {

    // Mocking des Repository Status vorbereiten
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(false);

    // health Methode aufrufen und Ergebnis prüfen
    Mono<ResponseEntity<Object>> responseMono = healthController.readiness();
    StepVerifier.create(responseMono).expectError(ServiceUnavailableException.class).verify();
  }

  @Test
  void testLiveness_healty() {
    // health Methode aufrufen und Ergebnis prüfen
    Mono<ResponseEntity<Object>> responseMono = healthController.liveness();
    StepVerifier.create(responseMono)
        .assertNext(
            response -> {
              assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
              assertInstanceOf(HealthStatus.class, response.getBody());
            })
        .verifyComplete();
  }
}
