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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.parseMediaType;

import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.active=test"})
@TestPropertySource(
    locations = "classpath:application-test.properties",
    properties = {"springdoc.api-docs.path=/docs/v3/api-docs"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class OpenApiSpecIT {

  @Value(value = "${local.server.port}")
  private int port;

  private WebTestClient webTestClient;

  @BeforeAll
  void prepareTestClient() {
    String endpointUrl = "http://localhost:" + port;
    log.info("Server running on: {}", endpointUrl);

    // set up the webTestClient
    webTestClient = WebTestClient.bindToServer().baseUrl(endpointUrl).build();
  }

  @Test
  void shouldServeOpenApiSpecAsJson() {
    webTestClient
        .get()
        .uri("/docs/v3/api-docs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.openapi")
        .exists(); // Überprüft, ob das JSON OpenAPI enthält
  }

  @Test
  void shouldServeOpenApiSpecPackagesGroupAsJson() {
    webTestClient
        .get()
        .uri("/docs/v3/api-docs/packages")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.openapi")
        .exists(); // Überprüft, ob das JSON OpenAPI enthält
  }

  @Test
  void shouldServeOpenApiSpecFeedsGroupAsJson() {
    webTestClient
        .get()
        .uri("/docs/v3/api-docs/feeds")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.openapi")
        .exists(); // Überprüft, ob das JSON OpenAPI enthält
  }

  @Test
  void shouldServeOpenApiSpecAsYaml() {
    webTestClient
        .get()
        .uri("/docs/v3/api-docs.yaml")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(parseMediaType("application/vnd.oai.openapi"))
        .expectBody(String.class)
        .consumeWith(
            result -> {
              String body = result.getResponseBody();
              assertThat(body, Matchers.containsString("openapi: 3."));
            });
  }

  @Test
  void shouldServeOpenApiSpecPackagesGroupAsYaml() {
    webTestClient
        .get()
        .uri("/docs/v3/api-docs.yaml/packages")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(parseMediaType("application/vnd.oai.openapi"))
        .expectBody(String.class)
        .consumeWith(
            result -> {
              String body = result.getResponseBody();
              assertThat(body, Matchers.containsString("openapi: 3."));
            });
  }

  @Test
  void shouldServeOpenApiSpecFeedsGroupAsYaml() {
    webTestClient
        .get()
        .uri("/docs/v3/api-docs.yaml/feeds")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(parseMediaType("application/vnd.oai.openapi"))
        .expectBody(String.class)
        .consumeWith(
            result -> {
              String body = result.getResponseBody();
              assertThat(body, Matchers.containsString("openapi: 3."));
            });
  }
}
