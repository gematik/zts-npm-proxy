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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@Slf4j
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.active=test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(locations = "classpath:application-test.properties")
class StaticFeedControllerTest {

  static {
    System.out.println("Prepare test packages");
    // Copy test packages to cache directory
    ServiceHelper.copyTestPackage(
            "testPackages/bfarm.terminologien.test-1.0.0.tgz",
            System.getProperty("java.io.tmpdir"));
  }

  @Value(value="${local.server.port}")
  private int port;

  private WebTestClient webTestClient;

  @Autowired private NpmProxyConfiguration npmProxyConfiguration;

  @BeforeAll
  void prepareTestPackages() {
    String endpointUrl = "http://localhost:" + port;
    log.info("Server running on: {}", endpointUrl);

    // set up the webTestClient
    webTestClient =
            WebTestClient.bindToServer().baseUrl(endpointUrl).build();

    // Wait until server http response is 200
    ServiceHelper.retryUntilReady(endpointUrl + "/api/health", 10, 5);

    npmProxyConfiguration.setTargetUrl(endpointUrl + npmProxyConfiguration.getTargetUrl());
  }

  @AfterAll
  void closeApplicationContext() {
    // delete the test packages
    if (Files.exists(
        Path.of(
            npmProxyConfiguration.getPackageCacheDir(), "bfarm.terminologien.test-1.0.0.tgz"))) {
      try {
        Files.delete(
            Path.of(
                npmProxyConfiguration.getPackageCacheDir(), "bfarm.terminologien.test-1.0.0.tgz"));
        System.out.println("Test package deleted");
      } catch (IOException e) {
        System.err.printf("Error deleting test package: %s%n", e.getMessage());
      }
    }
  }

  @Test
  void testHandleGetRssFeedHl7() {

    webTestClient
        .get()
        .uri(npmProxyConfiguration.getFeedPath())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .xpath("/rss/channel/title")
        .isEqualTo("BfArM FHIR Packages (Terminologies)")
        .xpath("/rss/channel/link")
        .isEqualTo(npmProxyConfiguration.getHostName() + npmProxyConfiguration.getFeedPath())
        .xpath("/rss/channel/description")
        .isEqualTo(
            "References to publicly available FHIR Terminology Packages (published by BfArM) will be available in this channel")
        .xpath("/rss/channel/generator")
        .isEqualTo("ZTS Publication Tooling")
        .xpath("/rss/channel/language")
        .isEqualTo("en")
        // TODO: Make namespace aware - Komischerweise scheint namespace-uri() nicht zu
        // funktionieren
        // .xpath("/rss/channel/*[namespace-uri()='http://www.w3.org/2005/Atom' and
        // local-name()='link']/@href")
        .xpath("/rss/channel/*[local-name()='link']/@href")
        .isEqualTo(npmProxyConfiguration.getHostName() + npmProxyConfiguration.getFeedPath())
        .xpath("/rss/channel/lastBuildDate")
        .isEqualTo("Fri, 20 Dec 2024 17:00:00 +0100")
        .xpath("/rss/channel/pubDate")
        .isEqualTo("Fri, 20 Dec 2024 17:00:00 +0100")
        .xpath("/rss/channel/ttl")
        .isEqualTo("60");
  }
}
