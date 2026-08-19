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

import de.gematik.zts.npmproxy.model.FhirPackageInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
class NpmProxyApplicationIT {

  private static final String TEST_PACKAGE = "bfarm.terminologien.test-1.0.0.tgz";

  static {
    log.info("Prepare test packages");
    // Copy test packages to cache directory
    ServiceHelper.copyTestPackage(
        "testPackages/".concat(TEST_PACKAGE), System.getProperty("java.io.tmpdir"));
  }

  @Value(value = "${local.server.port}")
  private int port;

  private WebTestClient webTestClient;

  @Autowired private NpmProxyConfiguration npmProxyConfiguration;

  @BeforeAll
  void prepareTestPackages() {
    String endpointUrl = "http://localhost:" + port;
    log.info("Server running on: {}", endpointUrl);

    // set up the webTestClient
    webTestClient = WebTestClient.bindToServer().baseUrl(endpointUrl).build();

    // Wait until server http response is 200
    ServiceHelper.retryUntilReady(endpointUrl + "/api/health", 10, 5);

    npmProxyConfiguration.setTargetUrl(endpointUrl + npmProxyConfiguration.getTargetUrl());
  }

  @AfterAll
  void closeApplicationContext() {
    // delete the test packages
    if (Files.exists(Path.of(npmProxyConfiguration.getPackageCacheDir(), TEST_PACKAGE))) {
      try {
        Files.delete(Path.of(npmProxyConfiguration.getPackageCacheDir(), TEST_PACKAGE));
        log.info("Test package deleted");
      } catch (IOException e) {
        log.error("Error deleting test package: {}", e.getMessage());
      }
    }
  }

  @Test
  void testGetPackageList() {
    var path = npmProxyConfiguration.getNpmPath().concat("/bfarm.terminologien.test");
    webTestClient
        .get()
        .uri(path)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(FhirPackageInfo.class);
  }
}
