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

package de.gematik.zts.npmproxy.component;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.model.FhirPackageInfo;
import de.gematik.zts.npmproxy.model.FhirPackageVersionDistInfo;
import de.gematik.zts.npmproxy.model.FhirPackageVersionInfo;
import de.gematik.zts.npmproxy.repository.FhirPackageRepositoryUpdater;
import de.gematik.zts.npmproxy.repository.LuceneBackedPackageRepository;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

class FhirPackageRepositoryUpdaterTest {

  private MockWebServer mockWebServer;

  @Mock private NpmProxyConfiguration properties;

  @Mock private LuceneBackedPackageRepository repository;

  @Spy @InjectMocks
  private FhirPackageRepositoryUpdater updater; // Use @Spy to allow partial mocking

  private static FhirPackageInfo createTestFhirPackageInfo(MockWebServer mockServer) {
    FhirPackageInfo packageInfo = new FhirPackageInfo();
    packageInfo.setName("bfarm.terminologien.test");

    FhirPackageVersionDistInfo distInfo = new FhirPackageVersionDistInfo();
    distInfo.setTarball(mockServer.url("/bfarm.terminologien.test-1.0.0.tgz").toString());

    FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();
    versionInfo.setName("bfarm.terminologien.test");
    versionInfo.setVersion("1.0.0");
    versionInfo.setDist(distInfo);

    var versions = new ConcurrentHashMap<String, FhirPackageVersionInfo>();
    versions.put("1.0.0", versionInfo);
    packageInfo.setVersions(versions);

    return packageInfo;
  }

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start(); // Start the MockWebServer
    MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown(); // Stop the MockWebServer after each test
    // delete the test packages
    Path tmpPath = Path.of("/tmp", "bfarm.terminologien.test-1.0.0.tgz");
    if (Files.exists(tmpPath)) {
      try {
        Files.delete(tmpPath);
        System.out.println("Test package deleted");
      } catch (IOException e) {
        System.err.printf("Error deleting test package: %s%n", e.getMessage());
      }
    }
  }

  @Test
  void testEnableService_whenPackageCountIsGreaterThanZeroAndInitialUpdateNotSucceeded() {
    when(repository.getPackageCount()).thenReturn(1);
    when(repository.isInitialUpdateSucceeded()).thenReturn(false);

    updater.enableService();

    verify(repository).setInitialUpdateSucceeded(true);
  }

  @Test
  void testFetchData_whenRepositoryIsInitialized() throws IOException {
    when(repository.isInitialized()).thenReturn(true);
    when(properties.getMonitoredPackages()).thenReturn(Set.of("bfarm.terminologien.test"));
    when(properties.getBackendMode()).thenReturn("basicauth");
    when(properties.getPackageCacheDir()).thenReturn("/tmp");
    // Set the target URL for the updater to the MockWebServer
    when(properties.getTargetUrl()).thenReturn(mockWebServer.url("/").toString());

    // create a test FhirPackageInfo object
    FhirPackageInfo packageInfo = createTestFhirPackageInfo(mockWebServer);

    ObjectMapper objectMapper = new ObjectMapper();

    // Setup MockWebServer response to return the test FhirPackageInfo object
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody(objectMapper.writeValueAsString(packageInfo)));

    // Load the test tgz file from resources
    byte[] tgzFileBytes =
        Files.readAllBytes(
            Paths.get("src/test/resources/testPackages/bfarm.terminologien.test-1.0.0.tgz"));

    // Setup MockWebServer response with the tgz file
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(new String(tgzFileBytes))); // Set the tgz file content as response body

    // Call the fetchData method
    updater.fetchData();

    // wait until the repository indexPackageFile method is called
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              verify(repository)
                  .indexPackageFile(
                      eq(Path.of("/tmp/bfarm.terminologien.test-1.0.0.tgz")), any(), isNull());
            });

    verify(repository).isInitialized();
  }
}
