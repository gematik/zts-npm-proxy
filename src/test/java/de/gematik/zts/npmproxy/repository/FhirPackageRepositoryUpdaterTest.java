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

package de.gematik.zts.npmproxy.repository;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.NpmProxyConstants;
import de.gematik.zts.npmproxy.exceptions.NpmProxyException;
import de.gematik.zts.npmproxy.model.FhirPackageArtifactRegistryAnnotations;
import de.gematik.zts.npmproxy.model.FhirPackageInfo;
import de.gematik.zts.npmproxy.model.FhirPackageVersionDistInfo;
import de.gematik.zts.npmproxy.model.FhirPackageVersionInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

class FhirPackageRepositoryUpdaterTest {

  private static final String TEST_PACKAGE_FILENAME = "bfarm.terminologien.test-1.0.0.tgz";

  private NpmProxyConfiguration properties;
  private MockWebServer mockWebServer;
  private LuceneBackedPackageRepository repository;
  private FhirPackageRepositoryUpdater updater;
  private ArtifactRegistryConnector artifactRegistryConnector;

  // -----------------------------------------------------------------------
  // Helper Methods
  // -----------------------------------------------------------------------

  /**
   * Creates a simple FhirPackageInfo with one version that references the mockWebServer as tarball
   * URL.
   */
  private static FhirPackageInfo createTestFhirPackageInfo(MockWebServer mockServer) {
    FhirPackageInfo packageInfo = new FhirPackageInfo();
    packageInfo.setName("bfarm.terminologien.test");

    FhirPackageVersionDistInfo distInfo = new FhirPackageVersionDistInfo();
    distInfo.setTarball(mockServer.url("/" + TEST_PACKAGE_FILENAME).toString());

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
    mockWebServer.start();

    properties = Mockito.mock(NpmProxyConfiguration.class);
    when(properties.getMonitoredPackages()).thenReturn(Set.of("bfarm.terminologien.test"));
    when(properties.getBackendMode()).thenReturn(NpmProxyConstants.BACKEND_MODE_BASICAUTH);
    when(properties.getUsername()).thenReturn("testuser");
    when(properties.getPassword()).thenReturn("testpassword");
    when(properties.getPackageCacheDir()).thenReturn("/tmp");
    when(properties.getTargetUrl()).thenReturn(mockWebServer.url("/").toString());

    repository = Mockito.mock(LuceneBackedPackageRepository.class);

    artifactRegistryConnector = Mockito.mock(ArtifactRegistryConnector.class);

    updater = new FhirPackageRepositoryUpdater(properties, repository, artifactRegistryConnector);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  private byte[] loadTestPackageBytes() throws IOException {
    // Load the .tgz file from resources (testPackages/bfarm.terminologien.test-1.0.0.tgz)
    String resourceName = "testPackages/" + TEST_PACKAGE_FILENAME;
    try (InputStream resourceStream =
        FhirPackageRepositoryUpdaterTest.class.getClassLoader().getResourceAsStream(resourceName)) {
      if (resourceStream == null) {
        throw new IllegalStateException("Could not find test file: " + resourceName);
      }
      return resourceStream.readAllBytes();
    }
  }

  // -----------------------------------------------------------------------
  // Tests for enableService
  // -----------------------------------------------------------------------

  @Test
  void testEnableService_whenPackageCountIsGreaterThanZeroAndInitialUpdateNotSucceeded() {
    when(repository.getPackageCount()).thenReturn(1);
    when(repository.isInitialUpdateSucceeded()).thenReturn(false);

    updater.enableService();

    verify(repository).setInitialUpdateSucceeded(true);
  }

  @Test
  void testEnableService_whenPackageCountIsZeroAndInitialUpdateNotSucceeded() {
    when(repository.getPackageCount()).thenReturn(0);
    when(repository.isInitialUpdateSucceeded()).thenReturn(false);

    updater.enableService();

    verify(repository, never()).setInitialUpdateSucceeded(true);
  }

  // -----------------------------------------------------------------------
  // Tests for fetchData
  // -----------------------------------------------------------------------

  @Test
  void testFetchData_whenRepositoryIsNotInitialized() {
    when(repository.isInitialized()).thenReturn(false);

    updater.fetchData();

    // No remote calls or indexing
    verify(properties, never()).getTargetUrl();
    verify(repository, never()).indexPackageFile(any(), any(), any());
  }

  @Test
  void testFetchData_whenRepositoryIsInitialized_success() throws IOException {
    when(repository.isInitialized()).thenReturn(true);

    // We are NOT in gCloud mode
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(false);

    FhirPackageInfo packageInfo = createTestFhirPackageInfo(mockWebServer);
    ObjectMapper objectMapper = new ObjectMapper();

    // Mock the packageInfo response
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody(objectMapper.writeValueAsString(packageInfo)));

    // Mock the .tgz file response
    Buffer buffer = new Buffer();
    buffer.write(loadTestPackageBytes());
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(buffer));

    // fetchData
    updater.fetchData();

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                verify(repository)
                    .indexPackageFile(
                        eq(Path.of(properties.getPackageCacheDir() + "/" + TEST_PACKAGE_FILENAME)),
                        any(),
                        isNull()));
  }

  @Test
  void testFetchData_whenRepositoryIsInitializedButWeGetAnJsonProcessingException() {
    when(repository.isInitialized()).thenReturn(true);
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(false);

    // Return invalid JSON
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody("Invalid JSON content..."));

    updater.fetchData();

    // Should not attempt to index
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(repository, never()).indexPackageFile(any(), any(), any()));
  }

  @Test
  void testFetchData_whenRepositoryIsInitializedAndPackageExists() throws JsonProcessingException {
    when(repository.isInitialized()).thenReturn(true);
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(false);

    FhirPackageInfo packageInfo = createTestFhirPackageInfo(mockWebServer);
    ObjectMapper objectMapper = new ObjectMapper();

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody(objectMapper.writeValueAsString(packageInfo)));

    // The repo says the package is already present
    when(repository.getPackagePathIndex(any(), any())).thenReturn(Optional.of(mock(Path.class)));

    updater.fetchData();

    // No indexing if the package is already present
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(repository, never()).indexPackageFile(any(), any(), any()));
  }

  // -----------------------------------------------------------------------
  // New tests focusing on isGCloudEnabled and handlePackageUpdateGCloud
  // -----------------------------------------------------------------------

  @Test
  void testFetchData_whenIsGCloudEnabled_andArtifactRegistryConnectorThrowsException() {
    // Setup
    when(repository.isInitialized()).thenReturn(true);
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(true);

    // Force artifactRegistryConnector to throw an exception
    when(artifactRegistryConnector.fetchMonitoredPackages())
        .thenThrow(new ConcurrentModificationException("Simulated error"));

    // Act
    updater.fetchData();

    // Assert: We expect the method to catch and log an error, then return
    // so no further steps are taken
    verify(repository, never()).indexPackageFile(any(), any(), any());
    verify(repository, never()).updateVersionInfo(any());
  }

  @Test
  void testFetchData_whenIsGCloudEnabled_andAnnotationsNull_skipUpdate()
      throws JsonProcessingException, MalformedURLException {
    // Setup
    when(repository.isInitialized()).thenReturn(true);
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(true);

    // Provide some monitored packages from ArtifactRegistry
    when(artifactRegistryConnector.fetchMonitoredPackages())
        .thenReturn(Set.of("bfarm.terminologien.test"));

    // A valid FhirPackageInfo
    FhirPackageInfo packageInfo = createTestFhirPackageInfo(mockWebServer);
    ObjectMapper objectMapper = new ObjectMapper();

    // The server returns the package info
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody(objectMapper.writeValueAsString(packageInfo)));

    // artifactRegistryConnector returns null annotations
    when(artifactRegistryConnector.getAnnotationsForPackageVersion(
            "bfarm.terminologien.test", "1.0.0"))
        .thenReturn(null);

    // Act
    updater.fetchData();

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              // Because annotations == null, we skip download/reindex
              verify(repository, never()).indexPackageFile(any(), any(), any());
              verify(repository, never()).reIndexPackage(any(), any());
            });
  }

  @Test
  void testFetchData_whenIsGCloudEnabled_andAnnotationsNotValid_skipUpdate()
      throws JsonProcessingException, MalformedURLException {
    // Setup
    when(repository.isInitialized()).thenReturn(true);
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(true);

    // Provide some monitored packages from ArtifactRegistry
    when(artifactRegistryConnector.fetchMonitoredPackages())
        .thenReturn(Set.of("bfarm.terminologien.test"));

    FhirPackageInfo packageInfo = createTestFhirPackageInfo(mockWebServer);
    ObjectMapper objectMapper = new ObjectMapper();

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody(objectMapper.writeValueAsString(packageInfo)));

    // Return invalid annotations
    FhirPackageArtifactRegistryAnnotations annotations =
        mock(FhirPackageArtifactRegistryAnnotations.class);
    when(annotations.isValid()).thenReturn(false);
    when(artifactRegistryConnector.getAnnotationsForPackageVersion(
            "bfarm.terminologien.test", "1.0.0"))
        .thenReturn(annotations);

    // Act
    updater.fetchData();

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              // Because annotations not valid, skip
              verify(repository, never()).indexPackageFile(any(), any(), any());
              verify(repository, never()).reIndexPackage(any(), any());
            });
  }

  @Test
  @SneakyThrows
  void testFetchData_whenIsGCloudEnabled_andAnnotationsValid_packageNotPresent()
      throws JsonProcessingException, MalformedURLException {
    // Setup
    when(repository.isInitialized()).thenReturn(true);
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(true);
    when(artifactRegistryConnector.fetchMonitoredPackages())
        .thenReturn(Set.of("bfarm.terminologien.test"));

    // Return a normal package info from mock server
    FhirPackageInfo packageInfo = createTestFhirPackageInfo(mockWebServer);
    ObjectMapper objectMapper = new ObjectMapper();

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody(objectMapper.writeValueAsString(packageInfo)));

    // .tgz response
    Buffer buffer = new Buffer();
    buffer.write(loadTestPackageBytes());
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(buffer));

    // Valid annotations
    FhirPackageArtifactRegistryAnnotations annotations =
        mock(FhirPackageArtifactRegistryAnnotations.class);
    when(annotations.isValid()).thenReturn(true);
    when(artifactRegistryConnector.getAnnotationsForPackageVersion(
            "bfarm.terminologien.test", "1.0.0"))
        .thenReturn(annotations);

    // Return empty => means package not present
    when(repository.getPackagePathIndex("bfarm.terminologien.test", "1.0.0"))
        .thenReturn(Optional.empty());

    // Act
    updater.fetchData();

    // Because package isn't present, we should see a download (indexPackageFile)
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                verify(repository)
                    .indexPackageFile(
                        eq(Path.of("/tmp/bfarm.terminologien.test-1.0.0.tgz")),
                        any(FhirPackageVersionInfo.class),
                        eq(annotations)));
  }

  @Test
  void testFetchData_whenIsGCloudEnabled_andAnnotationsValid_packageAlreadyPresent()
      throws JsonProcessingException, MalformedURLException {
    // Setup
    when(repository.isInitialized()).thenReturn(true);
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(true);

    // Provide some monitored packages from ArtifactRegistry
    when(artifactRegistryConnector.fetchMonitoredPackages())
        .thenReturn(Set.of("bfarm.terminologien.test"));

    FhirPackageInfo packageInfo = createTestFhirPackageInfo(mockWebServer);
    ObjectMapper objectMapper = new ObjectMapper();

    // Return package info
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody(objectMapper.writeValueAsString(packageInfo)));

    // Valid annotations
    FhirPackageArtifactRegistryAnnotations annotations =
        mock(FhirPackageArtifactRegistryAnnotations.class);
    when(annotations.isValid()).thenReturn(true);
    when(artifactRegistryConnector.getAnnotationsForPackageVersion(
            "bfarm.terminologien.test", "1.0.0"))
        .thenReturn(annotations);

    // Package is present => check if version info was updated => if updated => reIndex
    when(repository.getPackagePathIndex("bfarm.terminologien.test", "1.0.0"))
        .thenReturn(Optional.of(mock(Path.class)));

    // Suppose updateVersionInfo returns true => triggers reindex
    when(repository.updateVersionInfo("bfarm.terminologien.test", "1.0.0", annotations))
        .thenReturn(true);

    // Act
    updater.fetchData();

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                verify(repository)
                    .reIndexPackage(any(FhirPackageVersionInfo.class), eq(annotations)));
  }

  // -----------------------------------------------------------------------
  // Tests for webClient
  // -----------------------------------------------------------------------

  @Test
  void testWebClientGitlab() throws InterruptedException {
    when(properties.getBackendMode()).thenReturn(NpmProxyConstants.BACKEND_MODE_GITLAB);
    when(properties.getGitlabToken()).thenReturn("testtoken");

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody("Hallo Welt"));
    WebClient webClient = updater.webClient(properties.getTargetUrl());

    webClient.get().uri("/test").retrieve().bodyToMono(String.class).block();

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals(
        "Bearer " + properties.getGitlabToken(),
        recordedRequest.getHeader(HttpHeaders.AUTHORIZATION));
  }

  @Test
  void testWebClientBasicAuth() throws InterruptedException {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody("Hallo Welt"));
    WebClient webClient = updater.webClient(properties.getTargetUrl());

    webClient.get().uri("/test").retrieve().bodyToMono(String.class).block();

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals(
        "Basic "
            + Base64.getEncoder()
                .encodeToString(
                    (properties.getUsername() + ":" + properties.getPassword())
                        .getBytes(StandardCharsets.UTF_8)),
        recordedRequest.getHeader(HttpHeaders.AUTHORIZATION));
  }

  @Test
  void testWebClientUnknownBackendMode() {
    when(properties.getBackendMode()).thenReturn("unknown");
    String targetUrl = properties.getTargetUrl();

    assertThrows(NpmProxyException.class, () -> updater.webClient(targetUrl));
  }

  // -----------------------------------------------------------------------
  // Tests for redirection handling
  // -----------------------------------------------------------------------

  @Test
  void testWebClientBasicAuthWithRedirectionAbsoluteUrl() {
    String body = "Hallo Welt";

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_MOVED_PERM)
            .addHeader("Location", mockWebServer.url("/redirect").toString()));
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody(body));

    WebClient webClient = updater.webClient(properties.getTargetUrl());
    String finalResponseBody =
        webClient.get().uri("/test").retrieve().bodyToMono(String.class).block();

    assertEquals(body, finalResponseBody);
  }

  @Test
  void testWebClientBasicAuthWithRedirectionRelativeUrl() {
    String body = "Hallo Welt";

    when(properties.getTargetUrl()).thenReturn(mockWebServer.url("/packages/1234").toString());
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_MOVED_PERM)
            .addHeader("Location", "../redirectLocation"));
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody(body));

    WebClient webClient = updater.webClient(properties.getTargetUrl());
    String finalResponseBody =
        webClient.get().uri("/test").retrieve().bodyToMono(String.class).block();

    assertEquals(body, finalResponseBody);
  }

  // -----------------------------------------------------------------------
  // Additional test for malformed URL in package tarball
  // -----------------------------------------------------------------------

  @Test
  void testFetchData_downloadAndIndexPackage_malformedURLException() throws Exception {
    when(repository.isInitialized()).thenReturn(true);

    FhirPackageInfo packageInfo = new FhirPackageInfo();
    packageInfo.setName("bfarm.terminologien.test");

    FhirPackageVersionDistInfo distInfo = new FhirPackageVersionDistInfo();
    distInfo.setTarball("ht!tp://invalid-url"); // invalid

    FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();
    versionInfo.setName("bfarm.terminologien.test");
    versionInfo.setVersion("1.0.0");
    versionInfo.setDist(distInfo);

    var versions = new ConcurrentHashMap<String, FhirPackageVersionInfo>();
    versions.put("1.0.0", versionInfo);
    packageInfo.setVersions(versions);

    ObjectMapper objectMapper = new ObjectMapper();
    String packageInfoJson = objectMapper.writeValueAsString(packageInfo);

    // Return invalid tarball
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody(packageInfoJson));

    updater.fetchData();

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> verify(repository, never()).indexPackageFile(any(Path.class), any(), any()));
  }
}
