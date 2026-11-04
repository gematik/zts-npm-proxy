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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.devtools.artifactregistry.v1.ArtifactRegistryClient;
import com.google.devtools.artifactregistry.v1.ArtifactRegistrySettings;
import com.google.devtools.artifactregistry.v1.GetVersionRequest;
import com.google.devtools.artifactregistry.v1.ListPackagesRequest;
import com.google.devtools.artifactregistry.v1.Package;
import com.google.devtools.artifactregistry.v1.Version;
import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.model.FhirPackageArtifactRegistryAnnotations;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for {@link ArtifactRegistryConnector} */
@ExtendWith(MockitoExtension.class)
@Slf4j
class ArtifactRegistryConnectorWithWIFTest {

  @Mock private NpmProxyConfiguration mockConfig;
  @Mock private ArtifactRegistryClient mockClient;
  @Mock private Logger mockLogger;
  @InjectMocks private ArtifactRegistryConnector connector;

  @Captor private ArgumentCaptor<ListPackagesRequest> listPackagesRequestCaptor;
  @Captor private ArgumentCaptor<GetVersionRequest> getVersionRequestCaptor;

  @BeforeEach
  void setUp() {
    // Provide feature flag behavior
    lenient().when(mockConfig.isUseWif()).thenReturn(true);

    // Provide default behavior for the mocked config
    lenient()
        .when(mockConfig.getTargetUrl())
        .thenReturn("https://europe-west3-npm.pkg.dev/my-project-id/my-repo");
    // Use ReflectionTestUtils to set the private field
    ReflectionTestUtils.setField(connector, "client", mockClient);
  }

  @Test
  void testInitSuccess() {

    // Simulate success reading credentials
    try (MockedStatic<GoogleCredentials> credentialsStaticMock =
            Mockito.mockStatic(GoogleCredentials.class)) {

      GoogleCredentials mockCredentials = mock(GoogleCredentials.class);

      credentialsStaticMock
          .when(GoogleCredentials::getApplicationDefault)
          .thenReturn(mockCredentials);

      when(mockCredentials.createScoped(anyList())).thenReturn(mockCredentials);

      // We also mock the ArtifactRegistryClient static creation
      try (MockedStatic<ArtifactRegistryClient> clientStaticMock =
          Mockito.mockStatic(ArtifactRegistryClient.class)) {
        clientStaticMock
            .when(() -> ArtifactRegistryClient.create(any(ArtifactRegistrySettings.class)))
            .thenReturn(mockClient);

        // Call init
        connector.init();

        // Verify the client was created
        clientStaticMock.verify(
            () -> ArtifactRegistryClient.create(any(ArtifactRegistrySettings.class)));
      }
    }
  }

  @Test
  void testInitIOException() {
    // Force IOException through GoogleCredentials.getApplicationDefault(...)
    try (MockedStatic<GoogleCredentials> credentialsStaticMock =
            Mockito.mockStatic(GoogleCredentials.class)) {

      credentialsStaticMock
          .when(GoogleCredentials::getApplicationDefault)
          .thenThrow(new IOException("File not found"));

      connector.init();

      // The client should not be created in this scenario
      verifyNoInteractions(mockClient);
    }
  }

  @Test
  void testCloseWhenClientIsNotNull() {
    // Mock ByteArrayInputStream to avoid real file IO
    try (MockedStatic<GoogleCredentials> credentialsStaticMock =
            Mockito.mockStatic(GoogleCredentials.class);
        // Mock ArtifactRegistryClient.create(...) to return our mockClient
        MockedStatic<ArtifactRegistryClient> clientStaticMock =
            Mockito.mockStatic(ArtifactRegistryClient.class)) {

      // Set up a mock for GoogleCredentials
      GoogleCredentials mockCredentials = mock(GoogleCredentials.class);
      credentialsStaticMock
          .when(GoogleCredentials::getApplicationDefault)
          .thenReturn(mockCredentials);
      when(mockCredentials.createScoped(anyList())).thenReturn(mockCredentials);

      // Ensure that when ArtifactRegistryClient.create(...) is called, it returns the mockClient
      clientStaticMock
          .when(() -> ArtifactRegistryClient.create(any(ArtifactRegistrySettings.class)))
          .thenReturn(mockClient);

      // This will now succeed (no real file is opened, credentials & client are mocked)
      connector.init();

      // This should call mockClient.close()
      connector.close();

      // Verify that close() was indeed called on the mock
      verify(mockClient, times(1)).close();
    }
  }

  @Test
  void testFetchMonitoredPackagesNoPackages() {
    // Mock everything so init() doesn't fail:
    try (MockedStatic<GoogleCredentials> credentialsStaticMock =
            Mockito.mockStatic(GoogleCredentials.class);
        MockedStatic<ArtifactRegistryClient> clientStaticMock =
            Mockito.mockStatic(ArtifactRegistryClient.class)) {

      // Mock credentials
      GoogleCredentials mockCredentials = mock(GoogleCredentials.class);
      credentialsStaticMock
          .when(GoogleCredentials::getApplicationDefault)
          .thenReturn(mockCredentials);
      when(mockCredentials.createScoped(anyList())).thenReturn(mockCredentials);

      // Mock the static creation of ArtifactRegistryClient to return our mockClient
      clientStaticMock
          .when(() -> ArtifactRegistryClient.create(any(ArtifactRegistrySettings.class)))
          .thenReturn(mockClient);

      // Call init to set repositoryName and client
      connector.init();

      // Now we can mock the response for listPackages
      Iterable<Package> mockIterable = Collections::emptyIterator;
      ArtifactRegistryClient.ListPackagesPagedResponse mockResponse =
          mock(ArtifactRegistryClient.ListPackagesPagedResponse.class);
      when(mockClient.listPackages(any(ListPackagesRequest.class))).thenReturn(mockResponse);
      when(mockResponse.iterateAll()).thenReturn(mockIterable);

      // Execute method
      Set<String> result = connector.fetchMonitoredPackages();

      // Capture and verify
      verify(mockClient).listPackages(listPackagesRequestCaptor.capture());
      ListPackagesRequest capturedRequest = listPackagesRequestCaptor.getValue();
      assertThat(capturedRequest.getParent())
          .isEqualTo("projects/my-project-id/locations/europe-west3/repositories/my-repo");
      // Verify that the result is empty
      assertThat(result).isEmpty();
    }
  }

  @Test
  void testFetchMonitoredPackagesWithSomePackages() {
    try (MockedStatic<GoogleCredentials> credentialsMock =
            Mockito.mockStatic(GoogleCredentials.class);
        MockedStatic<ArtifactRegistryClient> clientMock =
            Mockito.mockStatic(ArtifactRegistryClient.class)) {

      // 1. Mock the credentials
      GoogleCredentials mockCredentials = mock(GoogleCredentials.class);
      credentialsMock
          .when(GoogleCredentials::getApplicationDefault)
          .thenReturn(mockCredentials);
      when(mockCredentials.createScoped(anyList())).thenReturn(mockCredentials);

      // 2. Mock the ArtifactRegistryClient creation to return mockClient
      clientMock
          .when(() -> ArtifactRegistryClient.create(any(ArtifactRegistrySettings.class)))
          .thenReturn(mockClient);

      // 3. Initialize connector so it sets repositoryName and client
      connector.init();

      // 4. Now mock the response
      Package pkg1 =
          Package.newBuilder()
              .setName("projects/my-proj/locations/eu/repositories/my-repo/packages/packageA")
              .build();
      Package pkg2 =
          Package.newBuilder()
              .setName("projects/my-proj/locations/eu/repositories/my-repo/packages/packageB")
              .build();
      ArtifactRegistryClient.ListPackagesPagedResponse mockResponse =
          mock(ArtifactRegistryClient.ListPackagesPagedResponse.class);
      when(mockClient.listPackages(any(ListPackagesRequest.class))).thenReturn(mockResponse);
      when(mockResponse.iterateAll()).thenReturn(Arrays.asList(pkg1, pkg2));

      // 5. Execute and verify
      Set<String> result = connector.fetchMonitoredPackages();
      assertThat(result).containsExactlyInAnyOrder("packageA", "packageB");
    }
  }

  @Test
  void testGetAnnotationsForPackageVersion() throws MalformedURLException {
    // create a mock response for getVersion
    String packageName = "test-package";
    String packageVersion = "1.0.0";
    Map<String, String> annotations =
        Map.of("status", "active", "additional-keywords", "keyword1,keyword2");

    Version versionResponse = Version.newBuilder().putAllAnnotations(annotations).build();
    when(mockClient.getVersion(any(GetVersionRequest.class))).thenReturn(versionResponse);

    // set the fields in the connector required for the test
    ReflectionTestUtils.setField(connector, "gCloudProjectId", "my-project-id");
    ReflectionTestUtils.setField(connector, "gCloudLocation", "us-central1");
    ReflectionTestUtils.setField(connector, "gCloudRepo", "my-repo");
    // Retrieve annotations using the connector
    FhirPackageArtifactRegistryAnnotations result =
        connector.getAnnotationsForPackageVersion(packageName, packageVersion);

    // verify the request was made correctly
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(FhirPackageArtifactRegistryAnnotations.Status.ACTIVE);
    assertThat(result.getAdditionalKeywords()).contains("keyword1", "keyword2");
  }

  @Test
  void testGetAnnotationsForPackageVersionWithException() {
    // 1. Mock the static GoogleCredentials
    try (
        MockedStatic<GoogleCredentials> credentialsMock =
            Mockito.mockStatic(GoogleCredentials.class);
        // 2. Mock the static ArtifactRegistryClient
        MockedStatic<ArtifactRegistryClient> clientMock =
            Mockito.mockStatic(ArtifactRegistryClient.class)) {
      // Mock credentials
      GoogleCredentials mockCredentials = mock(GoogleCredentials.class);
      credentialsMock
          .when(GoogleCredentials::getApplicationDefault)
          .thenReturn(mockCredentials);
      when(mockCredentials.createScoped(anyList())).thenReturn(mockCredentials);

      // Mock AR client creation
      clientMock
          .when(() -> ArtifactRegistryClient.create(any(ArtifactRegistrySettings.class)))
          .thenReturn(mockClient);

      // Call init so client is set
      connector.init();

      // Now we can stub getVersion(...) to throw
      when(mockClient.getVersion(any(GetVersionRequest.class)))
          .thenThrow(new RuntimeException("Some error"));

      assertThatThrownBy(() -> connector.getAnnotationsForPackageVersion("packageA", "1.0.0"))
          .isInstanceOf(RuntimeException.class);
    }
  }

  @Test
  void testInitWithValidUrl() {

    // A valid URL with expected segments
    when(mockConfig.getTargetUrl())
        .thenReturn("https://europe-west3-npm.pkg.dev/gematik-pt-zts-k8s-dev/npm-registry-zts-dev");

    // We need to mock creation of the client so it doesn't fail
    initConnectorWithMockedCredentials();

    // Check that fields got populated
    String projectId = (String) ReflectionTestUtils.getField(connector, "gCloudProjectId");
    String location = (String) ReflectionTestUtils.getField(connector, "gCloudLocation");
    String repo = (String) ReflectionTestUtils.getField(connector, "gCloudRepo");

    assertThat(projectId).isEqualTo("gematik-pt-zts-k8s-dev");
    assertThat(location).isEqualTo("europe-west3");
    assertThat(repo).isEqualTo("npm-registry-zts-dev");
  }

  @Test
  void testInitWithInvalidUrlSyntax() {
    // This should lead to URISyntaxException
    when(mockConfig.getTargetUrl()).thenReturn(" invalid! ");

    // We need to mock creation of the client so it doesn't fail
    initConnectorWithMockedCredentials();

    // The catch block for URISyntaxException is triggered, no fields set
    String projectId = (String) ReflectionTestUtils.getField(connector, "gCloudProjectId");
    String location = (String) ReflectionTestUtils.getField(connector, "gCloudLocation");
    String repo = (String) ReflectionTestUtils.getField(connector, "gCloudRepo");
    assertThat(projectId).isNull();
    assertThat(location).isNull();
    assertThat(repo).isNull();
  }

  @Test
  void testInitWithInsufficientPathSegments() {

    // This URL has only two path segments after the domain => insufficient
    when(mockConfig.getTargetUrl()).thenReturn("https://some-loc-npm.pkg.dev/only-one");

    // We need to mock creation of the client so it doesn't fail
    initConnectorWithMockedCredentials();

    // so the fields remain null
    String projectId = (String) ReflectionTestUtils.getField(connector, "gCloudProjectId");
    String location = (String) ReflectionTestUtils.getField(connector, "gCloudLocation");
    String repo = (String) ReflectionTestUtils.getField(connector, "gCloudRepo");

    // We expect at least 2 path segments: pathParts[1], pathParts[2]
    // so only location should be set
    assertThat(projectId).isNull();
    assertThat(location).isEqualTo("some-loc");
    assertThat(repo).isNull();
  }

  @Test
  void testInitWithNoTargetUrl() {

    // Empty target URL triggers the early return
    when(mockConfig.getTargetUrl()).thenReturn(StringUtils.EMPTY);

    // We need to mock creation of the client so it doesn't fail
    initConnectorWithMockedCredentials();

    // Verify that fields remain null
    String projectId = (String) ReflectionTestUtils.getField(connector, "gCloudProjectId");
    String location = (String) ReflectionTestUtils.getField(connector, "gCloudLocation");
    String repo = (String) ReflectionTestUtils.getField(connector, "gCloudRepo");

    assertThat(projectId).isNull();
    assertThat(location).isNull();
    assertThat(repo).isNull();
  }

  // helper methods for mocking and assertions

  private void initConnectorWithMockedCredentials() {
    // We need to mock creation of the client so it doesn't fail
    try (MockedStatic<ArtifactRegistryClient> clientStaticMock =
            Mockito.mockStatic(ArtifactRegistryClient.class);)
        {
      GoogleCredentials mockCredentials = mock(GoogleCredentials.class);
      try (MockedStatic<GoogleCredentials> credentialsStaticMock =
          Mockito.mockStatic(GoogleCredentials.class)) {
        credentialsStaticMock
            .when(GoogleCredentials::getApplicationDefault)
            .thenReturn(mockCredentials);
        when(mockCredentials.createScoped(anyList())).thenReturn(mockCredentials);

        clientStaticMock
            .when(() -> ArtifactRegistryClient.create(any(ArtifactRegistrySettings.class)))
            .thenReturn(mockClient);

        // Call init
        connector.init();
      }
    }
  }
}
