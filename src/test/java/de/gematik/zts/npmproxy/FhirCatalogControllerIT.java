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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.gematik.zts.npmproxy.model.FhirPackageBaseInfo;
import de.gematik.zts.npmproxy.repository.LuceneBackedPackageRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.active=test"})
@TestPropertySource(locations = "classpath:application-test.properties")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
@Import(FhirCatalogControllerIT.TestConfig.class)
class FhirCatalogControllerIT {

  @Autowired private LuceneBackedPackageRepository packageRepository;

  @Autowired private NpmProxyConfiguration properties;

  @TestConfiguration
  static class TestConfig {

    @Bean
    @Primary
    public LuceneBackedPackageRepository packageRepository() {
      return Mockito.mock(LuceneBackedPackageRepository.class);
    }

    @Bean
    public FhirCatalogController fhirCatalogController(
        LuceneBackedPackageRepository packageRepository) {
      return new FhirCatalogController(packageRepository);
    }
  }

  private static final String ENDPOINT = "/packages/catalog";

  @Value(value = "${local.server.port}")
  private int port;

  private WebTestClient webTestClient;

  @BeforeEach
  void setUp() {
    String endpointUrl = "http://localhost:" + port;
    log.info("Server running on: {}", endpointUrl);

    // set up the webTestClient
    webTestClient = WebTestClient.bindToServer().baseUrl(endpointUrl).build();
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(true);
  }

  // ================================================================================
  // Tests for 'name' parameter
  // ================================================================================

  @Test
  void testHandleCatalogRequest_NameParameter_ValidInput() {
    String validName = "bfarm.terminologien.valid";

    // Mock the repository to return some data
    FhirPackageBaseInfo packageInfo = new FhirPackageBaseInfo();
    packageInfo.setName(validName);
    when(packageRepository.searchPackages(any())).thenReturn(List.of(packageInfo));

    webTestClient
        .get()
        .uri(uriBuilder -> uriBuilder.path(ENDPOINT).queryParam("name", validName).build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(FhirPackageBaseInfo.class)
        .hasSize(1)
        .consumeWith(
            response -> {
              List<FhirPackageBaseInfo> body = response.getResponseBody();
              assertNotNull(body);
              assertEquals(validName, body.get(0).getName());
            });
  }

  @Test
  void testHandleCatalogRequest_NameParameter_InvalidInput() {
    String invalidName = "invalid package name!";

    webTestClient
        .get()
        .uri(uriBuilder -> uriBuilder.path(ENDPOINT).queryParam("name", invalidName).build())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertNotNull(responseBody);
              assertTrue(
                  responseBody.contains(NpmProxyConstants.MESSAGE_REGEXP_PACKAGE_NAME_CATALOG));
            });
  }

  // ================================================================================
  // Tests for 'version' parameter
  // ================================================================================

  @Test
  void testHandleCatalogRequest_VersionParameter_ValidInput() {
    String validVersion = "2023.0.0";
    String validName = "bfarm.terminologien.valid";

    // Mock the repository to return some data
    FhirPackageBaseInfo packageInfo = new FhirPackageBaseInfo();
    packageInfo.setName(validName);
    when(packageRepository.searchPackages(any())).thenReturn(List.of(packageInfo));

    webTestClient
        .get()
        .uri(uriBuilder -> uriBuilder.path(ENDPOINT).queryParam("version", validVersion).build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(FhirPackageBaseInfo.class)
        .hasSize(1)
        .consumeWith(
            response -> {
              List<FhirPackageBaseInfo> body = response.getResponseBody();
              assertNotNull(body);
              assertEquals(validName, body.get(0).getName());
            });
  }

  @Test
  void testHandleCatalogRequest_VersionParameter_InvalidInput() {
    String invalidVersion = "invalid_version!";

    webTestClient
        .get()
        .uri(uriBuilder -> uriBuilder.path(ENDPOINT).queryParam("version", invalidVersion).build())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertNotNull(responseBody);
              assertTrue(
                  responseBody.contains(NpmProxyConstants.MESSAGE_REGEXP_PACKAGE_VERSION_CATALOG));
            });
  }

  // ================================================================================
  // Tests for 'canonical' parameter
  // ================================================================================

  @Test
  void testHandleCatalogRequest_CanonicalParameter_ValidInput() {
    String validCanonical = "http://example.com/CodeSystem/valid";
    String validName = "bfarm.terminologien.valid";

    // Mock the repository to return some data
    FhirPackageBaseInfo packageInfo = new FhirPackageBaseInfo();
    packageInfo.setName(validName);
    when(packageRepository.searchPackages(any())).thenReturn(List.of(packageInfo));

    webTestClient
        .get()
        .uri(
            uriBuilder -> uriBuilder.path(ENDPOINT).queryParam("canonical", validCanonical).build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(FhirPackageBaseInfo.class)
        .hasSize(1)
        .consumeWith(
            response -> {
              List<FhirPackageBaseInfo> body = response.getResponseBody();
              assertNotNull(body);
              assertEquals(validName, body.get(0).getName());
            });
  }

  @Test
  void testHandleCatalogRequest_CanonicalParameter_InvalidInput() {
    String invalidCanonical = "invalid uri";

    webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder.path(ENDPOINT).queryParam("canonical", invalidCanonical).build())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertNotNull(responseBody);
              assertTrue(responseBody.contains(NpmProxyConstants.MESSAGE_REGEXP_URI_PART));
            });
  }

  // ================================================================================
  // Tests for 'pkgcanonical' parameter
  // ================================================================================

  @Test
  void testHandleCatalogRequest_PkgCanonicalParameter_ValidInput() {
    String validPkgCanonical = "urn:uuid:123e4567-e89b-12d3-a456-426614174000";

    String validName = "bfarm.terminologien.valid";

    // Mock the repository to return some data
    FhirPackageBaseInfo packageInfo = new FhirPackageBaseInfo();
    packageInfo.setName(validName);
    when(packageRepository.searchPackages(any())).thenReturn(List.of(packageInfo));

    webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder.path(ENDPOINT).queryParam("pkgcanonical", validPkgCanonical).build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(FhirPackageBaseInfo.class)
        .hasSize(1)
        .consumeWith(
            response -> {
              List<FhirPackageBaseInfo> body = response.getResponseBody();
              assertNotNull(body);
              assertEquals(validName, body.get(0).getName());
            });
  }

  @Test
  void testHandleCatalogRequest_PkgCanonicalParameter_InvalidInput() {
    String invalidPkgCanonical = "invalid_pkg_canonical";

    webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder.path(ENDPOINT).queryParam("pkgcanonical", invalidPkgCanonical).build())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertNotNull(responseBody);
              assertTrue(responseBody.contains(NpmProxyConstants.MESSAGE_VALID_PKG_CANONICAL));
            });
  }

  // ================================================================================
  // Tests for 'fhirVersion' parameter
  // ================================================================================

  @Test
  void testHandleCatalogRequest_FhirVersionParameter_ValidInput() {
    String validFhirVersion = "R4";

    // Mock the repository to return some data
    FhirPackageBaseInfo packageInfo = new FhirPackageBaseInfo();
    packageInfo.setFhirVersion(validFhirVersion);
    when(packageRepository.searchPackages(any())).thenReturn(List.of(packageInfo));

    webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder.path(ENDPOINT).queryParam("fhirVersion", validFhirVersion).build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(FhirPackageBaseInfo.class)
        .hasSize(1)
        .consumeWith(
            response -> {
              List<FhirPackageBaseInfo> body = response.getResponseBody();
              assertNotNull(body);
              assertEquals(validFhirVersion, body.get(0).getFhirVersion());
            });
  }

  @Test
  void testHandleCatalogRequest_FhirVersionParameter_InvalidInput() {
    String invalidFhirVersion = "R6";

    webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder.path(ENDPOINT).queryParam("fhirVersion", invalidFhirVersion).build())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertNotNull(responseBody);
              assertTrue(responseBody.contains(NpmProxyConstants.MESSAGE_REGEXP_FHIR_VERSION));
            });
  }

  // ================================================================================
  // Tests for 'keywordParams' parameter
  // ================================================================================

  @Test
  void testHandleCatalogRequest_KeywordParamsParameter_ValidInput() {
    List<String> validKeywords = List.of("keyword1", "keyword2");

    // Mock the repository to return some data
    FhirPackageBaseInfo packageInfo = new FhirPackageBaseInfo();
    packageInfo.setKeywords(validKeywords);
    when(packageRepository.searchPackages(any())).thenReturn(List.of(packageInfo));

    webTestClient
        .get()
        .uri(uriBuilder -> uriBuilder.path(ENDPOINT).queryParam("keyword", validKeywords).build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(FhirPackageBaseInfo.class)
        .hasSize(1)
        .consumeWith(
            response -> {
              List<FhirPackageBaseInfo> body = response.getResponseBody();
              assertNotNull(body);
              assertEquals(validKeywords, body.get(0).getKeywords());
            });
  }

  @Test
  void testHandleCatalogRequest_KeywordParamsParameter_InvalidInput_TooManyKeywords() {
    // Setup keywords.length = MAX_KEYWORDS + 1
    List<String> tooManyKeywords = new ArrayList<>();
    for (int i = 1; i <= NpmProxyConstants.MAX_KEYWORDS + 1; i++) {
      tooManyKeywords.add("keyword" + i);
    }

    webTestClient
        .get()
        .uri(uriBuilder -> uriBuilder.path(ENDPOINT).queryParam("keyword", tooManyKeywords).build())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertNotNull(responseBody);
              assertTrue(responseBody.contains(NpmProxyConstants.MESSAGE_TOO_MANY_KEYWORDS));
            });
  }

  @Test
  void testHandleCatalogRequest_KeywordParamsParameter_InvalidInput_InvalidKeyword() {
    List<String> invalidKeywords = List.of("validKeyword", "invalid keyword!");

    webTestClient
        .get()
        .uri(uriBuilder -> uriBuilder.path(ENDPOINT).queryParam("keyword", invalidKeywords).build())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertNotNull(responseBody);
              assertTrue(responseBody.contains(NpmProxyConstants.MESSAGE_REGEXP_KEYWORD));
            });
  }

  // ================================================================================
  // Tests for 'prerelease' and 'protectedPackage' parameters
  // ================================================================================

  @Test
  void testHandleCatalogRequest_PrereleaseParameter_ValidInput() {
    boolean prerelease = true;

    // Mock the repository to return some data
    when(packageRepository.searchPackages(any())).thenReturn(Collections.emptyList());

    webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path(ENDPOINT)
                    .queryParam("prerelease", Boolean.toString(prerelease))
                    .build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(FhirPackageBaseInfo.class)
        .hasSize(0);
  }

  // Since 'prerelease' and 'protectedPackage' are booleans, and Spring automatically handles
  // parsing,
  // invalid inputs like "notABoolean" would result in a 400 Bad Request due to type mismatch.

  // ================================================================================
  // Test for Service Unavailable Scenario
  // ================================================================================

  @Test
  void testHandleCatalogRequest_ServiceUnavailable() {
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(false);

    webTestClient
        .get()
        .uri(ENDPOINT)
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertNotNull(responseBody);
              assertTrue(responseBody.contains("Der Dienst wurde nicht korrekt initialisiert"));
            });
  }

  // ================================================================================
}
