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

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.devtools.artifactregistry.v1.*;
import com.google.devtools.artifactregistry.v1.Package;
import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.model.FhirPackageArtifactRegistryAnnotations;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * This class connects to the Google Artifact Registry and fetches package information.
 *
 * <p>It uses the Google Cloud Java client library to interact with the Artifact Registry API.
 */
@Slf4j
// disable the component, if the property is not set
@ConditionalOnProperty(
    name = "proxy.google.cloud.annotation.processing.enabled",
    havingValue = "true",
    matchIfMissing = false)
@Component
public class ArtifactRegistryConnector {

  private static final String GCLOUD_CREDENTIAL_SCOPE =
      "https://www.googleapis.com/auth/cloud-platform";
  private final NpmProxyConfiguration properties;
  private ArtifactRegistryClient client;
  private RepositoryName repositoryName;
  private String gCloudProjectId;
  private String gCloudLocation;
  private String gCloudRepo;

  public ArtifactRegistryConnector(NpmProxyConfiguration properties) {
    this.properties = properties;
  }

  private static String getLastPathPart(String path) {
    if (path == null || !path.contains("/")) {
      return path; // Or handle accordingly
    }
    int lastSlashIndex = path.lastIndexOf('/');
    return path.substring(lastSlashIndex + 1);
  }

  @PostConstruct
  public void init() {
    try {
      // Check if the Google Cloud service account key is set
      if (StringUtils.isEmpty(properties.getPassword())) {
        log.error("Google Cloud Service Account Key is not set or empty.");
        return;
      }

      // decode the service account key from the properties
      byte[] decodedKey =
          Base64.getDecoder().decode(properties.getPassword().getBytes(StandardCharsets.UTF_8));

      GoogleCredentials credentials =
          GoogleCredentials.fromStream(new ByteArrayInputStream(decodedKey))
              .createScoped(List.of(GCLOUD_CREDENTIAL_SCOPE));

      // Create a credentials provider
      FixedCredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);

      ArtifactRegistrySettings settings =
          ArtifactRegistrySettings.newBuilder().setCredentialsProvider(credentialsProvider).build();

      // extract project-id, location, and repository from the properties target URL
      // https://europe-west3-npm.pkg.dev/gematik-pt-zts-k8s-dev/npm-registry-zts-dev-fhirpackages/
      if (StringUtils.isNoneEmpty(properties.getTargetUrl())) {
        URI uri = new URI(properties.getTargetUrl());
        // Extract <location> from the host: "<location>-npm.pkg.dev"
        String host = uri.getHost(); // e.g. "europe-west3-npm.pkg.dev"
        gCloudLocation = host.split("-npm\\.pkg\\.dev")[0]; // "europe-west3"

        // Extract <projectId> and <repository> from the path: "/<projectId>/<repository>"
        String[] pathParts = uri.getPath().split("/");
        if (pathParts.length < 3) {
          log.error("Invalid target URL format. Expected format: /<projectId>/<repository>");
          return;
        }
        gCloudProjectId = pathParts[1];
        gCloudRepo = pathParts[2];
        log.debug(
            "Extracted Project ID: {}, Location: {} , Repo: {}",
            gCloudProjectId,
            gCloudLocation,
            gCloudRepo);
      } else {
        log.error("Target URL is not set in the configuration.");
        return;
      }

      // Retrieve the Repository
      repositoryName = RepositoryName.of(gCloudProjectId, gCloudLocation, gCloudRepo);

      // Initialize the client
      client = ArtifactRegistryClient.create(settings);
    } catch (IOException e) {
      log.error("Error while initializing ArtifactRegistryConnector: {}", e.getMessage(), e);
    } catch (IllegalArgumentException e) {
      log.error("Invalid Google Cloud Service Account Key format: {}", e.getMessage(), e);
    } catch (URISyntaxException e) {
      log.error("Error parsing the target URL: {}", e.getMessage(), e);
    }
  }

  @PreDestroy
  public void close() {
    if (client != null) {
      client.close();
      log.debug("ArtifactRegistryClient has been closed.");
    }
  }

  public Set<String> fetchMonitoredPackages() {
    ListPackagesRequest listRequest =
        ListPackagesRequest.newBuilder().setParent(repositoryName.toString()).build();

    var packageNames = new HashSet<String>();
    for (Package pkg : client.listPackages(listRequest).iterateAll()) {
      log.info("Package Name: {}", pkg.getName());
      packageNames.add(getLastPathPart(pkg.getName()));
    }
    return packageNames;
  }

  public FhirPackageArtifactRegistryAnnotations getAnnotationsForPackageVersion(
      String packageName, String packageVersion)
      throws MalformedURLException, IllegalArgumentException {
    VersionName versionName =
        VersionName.of(gCloudProjectId, gCloudLocation, gCloudRepo, packageName, packageVersion);
    GetVersionRequest versionRequest =
        GetVersionRequest.newBuilder().setName(versionName.toString()).build();

    var version = client.getVersion(versionRequest);
    return FhirPackageArtifactRegistryAnnotations.fromAnnotationsMap(version.getAnnotationsMap());
  }
}
