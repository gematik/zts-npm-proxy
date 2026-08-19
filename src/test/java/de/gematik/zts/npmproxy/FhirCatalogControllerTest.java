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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.zts.npmproxy.exceptions.ServiceUnavailableException;
import de.gematik.zts.npmproxy.model.FhirPackageBaseInfo;
import de.gematik.zts.npmproxy.repository.LuceneBackedPackageRepository;
import java.util.*;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Slf4j
class FhirCatalogControllerTest {

  @Mock private LuceneBackedPackageRepository packageRepository;
  @Mock private NpmProxyConfiguration properties;
  @Mock private ServerWebExchange exchange;
  @Mock private Authentication authentication;

  @InjectMocks private FhirCatalogController fhirCatalogController;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  // ================================================================================
  // Tests
  // ================================================================================

  @Test
  void testHandleGetPackageInfoRequest_ServiceUnavailable() {

    // Sicherstellen, dass wir in den Abbruch der Verarbeitung hineinlaufen
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(false);
    // Mocking ServerHttpRequest
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    when(request.getQueryParams()).thenReturn(new LinkedMultiValueMap<>());

    // Ausführen der Methode zum Abrufen der Paketinformationen
    Mono<ResponseEntity<Object>> response =
        fhirCatalogController.handleCatalogRequest(
            exchange,
            authentication,
            "bfarm.terminologies.test",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            request);

    // Prüfen, dass wir eine Response erhalten haben; in unserem Fall muss es sich um eine Exception
    // handeln
    assertNotNull(response);
    StepVerifier.create(response).expectError(ServiceUnavailableException.class).verify();
  }

  @Test
  void testHandleGetPackageInfoRequest_NoPackageFound() {

    String packageName = "bfarm.terminologien.test";

    // Sicherstellen, dass das Repository als initialisiert gilt und wir kein Paket finden
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(true);
    when(packageRepository.searchPackages(any())).thenReturn(Collections.emptyList());

    // Mocking ServerHttpRequest
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    when(request.getQueryParams()).thenReturn(new LinkedMultiValueMap<>());

    // Ausführen der Methode zum Abrufen der Paketinformationen
    Mono<ResponseEntity<Object>> response =
        fhirCatalogController.handleCatalogRequest(
            exchange,
            authentication,
            packageName,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            request);

    // Prüfen, dass wir eine Response erhalten haben; in unserem Fall muss es sich um eine
    // leere Liste handeln
    assertNotNull(response);
    StepVerifier.create(response)
        .expectNextMatches(
            res -> {
              assertEquals(HttpStatusCode.valueOf(200), res.getStatusCode());
              assertInstanceOf(List.class, res.getBody());
              Object body = res.getBody();
              assertTrue(
                  body instanceof List<?>
                      && ((List<?>) body).isEmpty()); // Assert the body is an empty list
              return true; // Assert the body is an empty list
            })
        .verifyComplete();
  }

  @Test
  void testHandleGetPackageInfoRequest_Success() {

    // Paketinformationen vorbereiten
    String packageName = "bfarm.terminologien.test";
    FhirPackageBaseInfo fhirPackageBaseInfo = new FhirPackageBaseInfo();
    fhirPackageBaseInfo.setName(packageName);
    String user = "user";

    var resultList = new ArrayList<FhirPackageBaseInfo>();
    resultList.add(fhirPackageBaseInfo);
    // Sicherstellen, dass das Repository als initialisiert gilt und wir ein passendes Paket finden
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(true);
    when(packageRepository.searchPackages(any())).thenReturn(resultList);

    // Nutzernamen im Authentication-Objekt setzen
    when(authentication.getName()).thenReturn(user);

    // Mocking ServerHttpRequest
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    when(request.getQueryParams()).thenReturn(new LinkedMultiValueMap<>());

    // Ausführen der Methode zum Abrufen der Paketinformationen
    Mono<ResponseEntity<Object>> response =
        fhirCatalogController.handleCatalogRequest(
            exchange,
            authentication,
            packageName,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            request);

    assertNotNull(response);

    StepVerifier.create(response)
        .expectNextMatches(
            entity -> {
              assertNotNull(entity, "ResponseEntity must not be null");
              assertTrue(entity.hasBody(), "ResponseEntity must have a body");
              assertInstanceOf(List.class, entity.getBody(), "Body must be of type List");
              assertEquals(HttpStatus.OK, entity.getStatusCode());
              assertEquals(List.of(fhirPackageBaseInfo), entity.getBody());
              assertEquals(
                  CacheControl.maxAge(600, TimeUnit.SECONDS).cachePublic().getHeaderValue(),
                  entity.getHeaders().getCacheControl());
              return true; // Return true to satisfy the expectNextMatches requirement
            })
        .verifyComplete();

    verify(exchange).getAttributes();
  }

  @Test
  void testHandleGetPackageInfoWithKeywordsRequest_Success() {

    // Paketinformationen vorbereiten
    String packageName = "bfarm.terminologien.test";
    List<String> keywords = List.of("OPS");
    FhirPackageBaseInfo fhirPackageBaseInfo = new FhirPackageBaseInfo();
    fhirPackageBaseInfo.setName(packageName);
    fhirPackageBaseInfo.setKeywords(keywords);
    String user = "user";

    var resultList = new ArrayList<FhirPackageBaseInfo>();
    resultList.add(fhirPackageBaseInfo);
    // Sicherstellen, dass das Repository als initialisiert gilt und wir ein passendes Paket finden
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(true);
    when(packageRepository.searchPackages(any())).thenReturn(resultList);

    // Nutzernamen im Authentication-Objekt setzen
    when(authentication.getName()).thenReturn(user);

    // Mocking ServerHttpRequest
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    var keywordsMap = new LinkedMultiValueMap<String, String>();
    keywordsMap.put("keyword", List.of("OPS"));
    when(request.getQueryParams()).thenReturn(keywordsMap);

    // Ausführen der Methode zum Abrufen der Paketinformationen
    Mono<ResponseEntity<Object>> response =
        fhirCatalogController.handleCatalogRequest(
            exchange,
            authentication,
            packageName,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            keywords,
            request);

    assertNotNull(response);

    StepVerifier.create(response)
        .expectNextMatches(
            entity -> {
              assertNotNull(entity, "ResponseEntity must not be null");
              assertTrue(entity.hasBody(), "ResponseEntity must have a body");
              assertInstanceOf(List.class, entity.getBody(), "Body must be of type List");
              assertEquals(HttpStatus.OK, entity.getStatusCode());
              assertEquals(List.of(fhirPackageBaseInfo), entity.getBody());
              assertEquals(
                  CacheControl.maxAge(600, TimeUnit.SECONDS).cachePublic().getHeaderValue(),
                  entity.getHeaders().getCacheControl());
              return true; // Return true to satisfy the expectNextMatches requirement
            })
        .verifyComplete();

    verify(exchange).getAttributes();
  }

  // ================================================================================

}
