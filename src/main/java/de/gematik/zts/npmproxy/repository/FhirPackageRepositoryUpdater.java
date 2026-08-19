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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.NpmProxyConstants;
import de.gematik.zts.npmproxy.exceptions.NpmProxyException;
import de.gematik.zts.npmproxy.exceptions.RepositoryUpdaterException;
import de.gematik.zts.npmproxy.model.FhirPackageArtifactRegistryAnnotations;
import de.gematik.zts.npmproxy.model.FhirPackageInfo;
import de.gematik.zts.npmproxy.model.FhirPackageVersionInfo;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class FhirPackageRepositoryUpdater {

  private final NpmProxyConfiguration properties;
  private final LuceneBackedPackageRepository repository;
  private final ArtifactRegistryConnector artifactRegistryConnector;
  private Set<String> monitoredPackages;

  public FhirPackageRepositoryUpdater(
      NpmProxyConfiguration properties,
      LuceneBackedPackageRepository repository,
      @Autowired(required = false) ArtifactRegistryConnector artifactRegistryConnector) {
    this.properties = properties;
    this.repository = repository;
    this.artifactRegistryConnector = artifactRegistryConnector;
    // Set monitored packages based on the configuration if gCloud is not enabled
    if (!properties.isGCloudAnnotationProcessingEnabled()) {
      log.info(
          "GCloud is not enabled. Using monitored packages from configuration: {}",
          properties.getMonitoredPackages());
      monitoredPackages = properties.getMonitoredPackages();
    }
  }

  /**
   * Workaround, um sicherzustellen, dass ein Repository ohne persistenten Cache überhaupt Daten
   * hat, bevor es genutzt werden kann. Diese Lösung ist nicht schön! Perspektivisch müssten wir
   * sicherstellen, dass beim initialen fetchData() erst NACH dem erfolgreichen Download und
   * Initialisieren das Repositories entsprechend gekennzeichnet wird. Durch die reaktive Umsetzung
   * erfordert dies jedoch eine umfassende Überarbeitung des Codes.
   */
  @Scheduled(initialDelayString = "${proxy.enable-service-delay-ms:30000}", fixedRate = 60000)
  public void enableService() {

    // Stellt sicher, dass das Repository erst genutzt werden kann, wenn mindestens ein Paket
    // enthalten ist
    if (repository.getPackageCount() > 0 && !repository.isInitialUpdateSucceeded()) {
      log.info("Data available. Service is now enabled.");
      repository.setInitialUpdateSucceeded(true);
    }
  }

  /**
   * Wir starten in regelmäßigen Abständen Anfragen an die Backend-NPM-Registry, um zu überprüfen,
   * ob es neue Pakete gibt
   */
  @Scheduled(fixedRateString = "${proxy.update-interval-in-ms}", initialDelay = 10000)
  public void fetchData() {

    // Vorbedingung: Das lokale repository muss bereits initialisiert sein, sonst starten wir keine
    // Anfragen ans Backend
    if (repository.isInitialized()) {

      // if gCloud is enabled, fetch monitored packages from Artifact Registry
      if (properties.isGCloudAnnotationProcessingEnabled()) {
        try {
          // update monitored packages
          monitoredPackages = artifactRegistryConnector.fetchMonitoredPackages();
          log.info("Monitored packages updated: {}", monitoredPackages);
        } catch (Exception e) {
          log.error("Error while fetching monitored packages: {}", e.getMessage(), e);
          return;
        }
      } else {
        // otherwise get monitored packages from properties
        monitoredPackages = properties.getMonitoredPackages();
      }
      // Vorbereiten des Clients
      WebClient webClient = webClient(properties.getTargetUrl());

      // Get all packages from artifact registry
      for (String packageName : monitoredPackages) {

        try {

          // Herunterladen der Paketinformationen
          String responseBody =
              webClient.get().uri("/" + packageName).retrieve().bodyToMono(String.class).block();

          ObjectMapper objectMapper = new ObjectMapper();
          FhirPackageInfo packageInfo = objectMapper.readValue(responseBody, FhirPackageInfo.class);

          log.info("{} {}", packageInfo.getName(), packageInfo.getVersions().keySet());

          // download packages not in the package index or update the index with annotations
          // information
          packageInfo.getVersions().values().forEach(this::updatePackageIndex);

        } catch (JsonProcessingException e) {
          log.error("Error while parsing the JSON response: {}", e.getMessage(), e);
        } catch (Exception e) {
          log.error("Error while fetching data: {}", e.getMessage(), e);
        }
      }
    }
  }

  private void handlePackageUpdateGCloudDisabled(FhirPackageVersionInfo fhirPackageVersionInfo) {

    // check if the package is already present in the index
    boolean packagePresent =
        repository
            .getPackagePathIndex(
                fhirPackageVersionInfo.getName(), fhirPackageVersionInfo.getVersion())
            .isPresent();

    // if it's not present, download and index the package
    if (!packagePresent) {
      downloadAndIndexPackage(fhirPackageVersionInfo, null);
    } else {
      // update the version info with the remote version info
      var versionInfoUpdated = repository.updateVersionInfo(fhirPackageVersionInfo);

      log.debug(
          "VersionInfo for package {} version {} updated: {}",
          fhirPackageVersionInfo.getName(),
          fhirPackageVersionInfo.getVersion(),
          versionInfoUpdated);

      // versionInfo was updated, reindex the package
      if (versionInfoUpdated) {
        repository.reIndexPackage(fhirPackageVersionInfo, null);
      }
    }
  }

  private void handlePackageUpdateGCloud(FhirPackageVersionInfo fhirPackageVersionInfo) {

    FhirPackageArtifactRegistryAnnotations annotations;
    try {
      annotations =
          artifactRegistryConnector.getAnnotationsForPackageVersion(
              fhirPackageVersionInfo.getName(), fhirPackageVersionInfo.getVersion());
    } catch (Exception e) {
      throw new RepositoryUpdaterException(
          String.format(
              "Failed to get annotations for %s#%s",
              fhirPackageVersionInfo.getName(), fhirPackageVersionInfo.getVersion()),
          e);
    }

    // if no annotations are found or annotations are not valid, skip download /
    // reindex
    if (annotations == null || !annotations.isValid()) {
      log.error(
          "Annotations for package {} version {} not found or not valid. Skipping download / reindex.",
          fhirPackageVersionInfo.getName(),
          fhirPackageVersionInfo.getVersion());
      return;
    } else {
      log.debug(
          "Annotations for package {} version {} found and valid",
          fhirPackageVersionInfo.getName(),
          fhirPackageVersionInfo.getVersion());
    }

    // check if the package is already present in the index
    boolean packagePresent =
        repository
            .getPackagePathIndex(
                fhirPackageVersionInfo.getName(), fhirPackageVersionInfo.getVersion())
            .isPresent();

    // if it's not present, download and index the package
    if (!packagePresent) {
      downloadAndIndexPackage(fhirPackageVersionInfo, annotations);
    } else {
      // update the annotations for a specific version
      boolean versionInfoUpdated =
          repository.updateVersionInfo(
              fhirPackageVersionInfo.getName(), fhirPackageVersionInfo.getVersion(), annotations);

      log.debug(
          "Annotations for package {} version {} updated: {}",
          fhirPackageVersionInfo.getName(),
          fhirPackageVersionInfo.getVersion(),
          versionInfoUpdated);

      // if the versionInfo was updated, reindex the package
      if (versionInfoUpdated) {
        repository.reIndexPackage(fhirPackageVersionInfo, annotations);
      }
    }
  }

  private void updatePackageIndex(FhirPackageVersionInfo fhirPackageVersionInfo) {
    if (properties.isGCloudAnnotationProcessingEnabled()) {
      handlePackageUpdateGCloud(fhirPackageVersionInfo);
    } else {
      handlePackageUpdateGCloudDisabled(fhirPackageVersionInfo);
    }
  }

  /**
   * Erstellt in Abhängigkeit vom konfigurierten backend-mode einen passend ausgestalteten WebClient
   *
   * @param baseUrl Zu verwendende Base-Url für die Requests
   * @return erzeugter WebClient
   */
  public WebClient webClient(String baseUrl) {

    if (properties.getBackendMode().contentEquals(NpmProxyConstants.BACKEND_MODE_GITLAB)) {

      return WebClient.builder()
          .baseUrl(baseUrl)
          .filter(this::handleRedirects)
          .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getGitlabToken())
          .build();
    } else if (properties
        .getBackendMode()
        .contentEquals(NpmProxyConstants.BACKEND_MODE_BASICAUTH)) {
      return WebClient.builder()
          .baseUrl(baseUrl)
          .filter(this::handleRedirects)
          .defaultHeader(HttpHeaders.AUTHORIZATION, generateAuthorizationHeaderValueForBasicAuth())
          .build();
    }

    throw new NpmProxyException("Es wurde kein gültiger [backend-mode] konfiguriert.");
  }

  /**
   * Erzeugt auf Grundlage der Dienstkonfiguration einen passenden Wert für einen
   * BasicAuth-Authorization Header
   *
   * @return Wert für Authorization Header
   */
  private String generateAuthorizationHeaderValueForBasicAuth() {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString(
                (properties.getUsername() + ":" + properties.getPassword())
                    .getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Lädt ein Paket in einer bestimmten Version vom Backend-NPM-Service herunter
   *
   * @param fhirPackageVersionInfo Informationen zum Paket und der Version
   */
  private void downloadAndIndexPackage(
      FhirPackageVersionInfo fhirPackageVersionInfo,
      FhirPackageArtifactRegistryAnnotations annotations) {

    var path = fhirPackageVersionInfo.getDist().getTarball();
    var packageName = fhirPackageVersionInfo.getName();
    var version = fhirPackageVersionInfo.getVersion();
    log.info("Downloading and indexing package: {}", path);

    try {
      // Download URL
      URL url = URI.create(path).toURL();

      // Dateipfad, unter welchem das heruntergeladene Paket gespeichert werden soll
      Path fileStoragePath =
          Paths.get(properties.getPackageCacheDir(), packageName + "-" + version + ".tgz");

      String baseUrl = url.getProtocol() + "://" + url.getHost();
      if (url.getPort() != -1) {
        baseUrl += ":" + url.getPort();
      }
      // Initialisieren des Downloads

      WebClient webClient = webClient(baseUrl);

      Flux<DataBuffer> dataBufferFlux =
          webClient.get().uri(url.getPath()).retrieve().bodyToFlux(DataBuffer.class);

      DataBufferUtils.write(dataBufferFlux, fileStoragePath)
          .block(); // Warten, bis der Schreibvorgang abgeschlossen ist

      log.info("Package stored: {}", fileStoragePath);
      repository.indexPackageFile(fileStoragePath, fhirPackageVersionInfo, annotations);

    } catch (IllegalArgumentException e) {
      throw new RepositoryUpdaterException(
          "Die für den Download angegebene URL (" + path + ") scheint nicht valide zu sein.");
    } catch (Exception e) {
      throw new RepositoryUpdaterException(
          "Fehler beim Download oder Speichern des Pakets von URL ("
              + path
              + "): "
              + e.getMessage());
    }
  }

  /**
   * Einige Registries (z.B. google Artifact Registry) antworten mit temporären oder permanenten
   * Redirects. Da sich der WebClient anscheinend nicht selbständig darum kümmert, übernehmen wir
   * hier diese Aufgabe.
   *
   * @param request ursprünglicher Request
   * @param next ExchangeFunction, die den angepassten (neuen) Request übergeben bekommt
   * @return entsprechende ClientResponse
   */
  private Mono<ClientResponse> handleRedirects(ClientRequest request, ExchangeFunction next) {
    return next.exchange(request)
        .flatMap(
            response -> {
              // Prüfen, ob der Status ein Redirect ist (3xx)
              if (response.statusCode().is3xxRedirection()) {
                log.debug("STATUS: {}", response.statusCode().value());

                URI redirectUri = response.headers().asHttpHeaders().getLocation();

                if (redirectUri != null && !redirectUri.isAbsolute()) {
                  redirectUri = request.url().resolve(redirectUri);
                }
                log.debug("REDIRECT_TO: {}", redirectUri);

                if (redirectUri != null) {
                  // Erstellt eine neue Anfrage zur Redirection-URL und übernimmt dabei alle
                  // Header aus dem ursprünglichen Request
                  ClientRequest newRequest =
                      ClientRequest.create(request.method(), redirectUri)
                          .headers(headers -> headers.addAll(request.headers()))
                          .build();
                  return next.exchange(newRequest);
                }
              }
              return Mono.just(response);
            });
  }
}
