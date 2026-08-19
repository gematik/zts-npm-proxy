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

import static de.gematik.zts.npmproxy.NpmProxyConstants.*;

import de.gematik.zts.npmproxy.exceptions.PackageAccessDeniedException;
import de.gematik.zts.npmproxy.exceptions.ServiceUnavailableException;
import de.gematik.zts.npmproxy.model.FhirPackageInfo;
import de.gematik.zts.npmproxy.repository.LuceneBackedPackageRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Implementiert die von Außen aufrufbaren REST-Operationen für den FHIR-NPM-Proxy. */
@RestController
@Validated
@Order(1)
@Tag(name = "Package-API", description = "API for accessing FHIR NPM packages")
public class FhirNpmController {

  private final LuceneBackedPackageRepository packageRepository;
  private final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

  public FhirNpmController(LuceneBackedPackageRepository packageRepository) {
    this.packageRepository = packageRepository;
  }

  /**
   * Auflisten aller Paketinformationen - Wichtig: Diese Schnittstelle wird grundsätzlich nicht auf
   * Paketebene geschützt, d.h. jeder kann sich entsprechende Metadaten herunterladen. Lediglich der
   * Download der Pakete wird reglementiert.
   *
   * @param exchange Exchange-Informationen
   * @param authentication Authentisierungsinformationen, die wir - falls gewünscht - für
   *     feingranulares Access Control verwenden können
   * @param packageName Pfadvariable (in unserem Fall ist das der Paketname)
   * @return Response, die an den Client zurückgegeben wird
   */
  @GetMapping(
      value = "${proxy.npm-path}/{packageName}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List package versions",
      description =
          "Endpoint to retrieve all versions for a package name.\n* The payload is compliant with the NPM package version listing.\n* The `dist-tags` element will provide tags on certain versions, like the label of which version is the `latest`.\n* In calculating `latest` the highest stable semver version is used, not the most recently published version.\n* If an author has indicated that the package should no longer be used, the element `unlisted` will be populated for a version.\n")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "A package object with all available versions",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = FhirPackageInfo.class))),
        @ApiResponse(
            responseCode = "404",
            description = "No packages found for the package name",
            content = @Content())
      })
  public Mono<ResponseEntity<Object>> handleGetPackageInfoRequest(
      ServerWebExchange exchange,
      Authentication authentication,
      @PathVariable
          @Pattern(regexp = REGEXP_PACKAGE_NAME, message = MESSAGE_REGEXP_PACKAGE_NAME)
          @Parameter(description = "Name of the package", example = "bfarm.terminologien.ops")
          String packageName) {

    // Hinweis: hier müssen wir uns nicht um besondere Sicherheitsthemen kümmern. Alle Nutzer
    // (authentifiziert und nicht authentifiziert) dürfen sich zumindest die Paketbeschreibungen
    // laden. Das indirekte Akzeptieren der Downloadbedingungen über das Generieren eines Tokens
    // ist hier noch nicht notwendig und greift erst, wenn Pakete tatsächlich heruntergeladen werden
    // sollen.

    // Abbruch der Verarbeitung, falls noch kein initiales Update der Paketinformationen
    // durchgeführt wurde
    if (!packageRepository.isInitialUpdateSucceeded()) {
      return Mono.error(
          new ServiceUnavailableException(
              "Der Dienst wurde nicht korrekt initialisiert. Bitte versuchen Sie es später erneut."));
    }

    // Übernahme des Nutzernamens aus dem Token in die Exchange-Attribute
    exchange.getAttributes().put(ATTRIBUTE_USER, authentication.getName());

    // Laden der Paketinformationen
    Optional<FhirPackageInfo> fhirPackageInfo = packageRepository.findPackageByName(packageName);

    if (fhirPackageInfo.isEmpty()) {
      // Sollte das Paket nicht gefunden werden, dann geben wir ein 404 zurück

      return Mono.error(
          new NoResourceFoundException(
              exchange.getRequest().getURI(),
              "Das angeforderte Paket konnte nicht gefunden werden: " + packageName));
    } else {
      // Response vorbereiten und Caching-Header setzen
      // Prinzipiell wollen wir Inhalte cachen, um den Service zu entlasten. Allerdings müssen wir
      // hier aufpassen, dass wir nicht zu lange cachen, da sich die Metadaten der Pakete durchaus
      // ändern können, z.B. wenn neue Versionen veröffentlicht werden.
      return Mono.just(
          ResponseEntity.ok()
              .cacheControl(CacheControl.maxAge(600, TimeUnit.SECONDS).cachePublic())
              .body(fhirPackageInfo.get()));
    }
  }

  /**
   * Download einer konkreten Paketversion - Wichtig: Bestimmte Pakete sind Zugriffs-geschützt und
   * können nur mit gültigem und passendem Token heruntergeladen werden.
   *
   * @param exchange Exchange-Informationen
   * @param authentication Authentisierungsinformationen, die wir für feingranulares Access Control
   *     verwenden können
   * @param packageName Pfadvariable (Paketname)
   * @param packageVersion Pfadvariable (Paketversion)
   * @return Response, die an den Client zurückgegeben wird
   */
  @GetMapping(value = "${proxy.npm-path}/{packageName}/{packageVersion}")
  @Operation(
      summary = "Download a package version",
      description = "Download a specific package version.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "A package object in binary format",
            content =
                @Content(
                    mediaType = "application/tar+gzip",
                    schema = @Schema(implementation = Object.class))),
        @ApiResponse(
            responseCode = "404",
            description = "No packages found for the package name and version",
            content = @Content())
      })
  @SecurityRequirement(name = "BearerAuthentication")
  public ResponseEntity<Flux<DataBuffer>> handleGetPackageVersionRequest(
      ServerWebExchange exchange,
      Authentication authentication,
      @PathVariable
          @Pattern(regexp = REGEXP_PACKAGE_NAME, message = MESSAGE_REGEXP_PACKAGE_NAME)
          @Parameter(description = "Name of the package", example = "bfarm.terminologien.ops")
          String packageName,
      @PathVariable
          @Pattern(regexp = REGEXP_PACKAGE_VERSION, message = MESSAGE_REGEXP_PACKAGE_VERSION)
          @Parameter(description = "Version of the package", example = "2025.0.0")
          String packageVersion) {

    // Abbruch der Verarbeitung, falls noch kein initiales Update der Paketinformationen
    // durchgeführt wurde
    if (!packageRepository.isInitialUpdateSucceeded()) {
      throw new ServiceUnavailableException(
          "Der Dienst wurde nicht korrekt initialisiert. Bitte versuchen Sie es später erneut.");
    }

    // Übernahme des Nutzernamens aus dem Token in die Exchange-Attribute
    exchange.getAttributes().put(ATTRIBUTE_USER, authentication.getName());

    // Ermitteln, ob das Paket auf der Liste der geschützten Pakete steht
    boolean isProtectedPackage =
        packageRepository.isPackageVersionProtected(packageName, packageVersion);

    // Access Control - Bedingungen für den Zugriff gelten nur, wenn das Paket auf der Liste der
    // geschützten Pakete steht. Wir prüfen anhand der Angaben aus dem Token, ob die
    // Downloadbedingungen irgendwann mal akzeptiert wurden. Voraussetzung ist natürlich, dass der
    // Nutzer als authentifiziert gilt.
    if (isProtectedPackage && !authentication.isAuthenticated()) {
      throw new AuthenticationCredentialsNotFoundException(
          "Der Zugriff auf geschützte Terminologiepakete ist nur für Nutzer mit gültigem Token möglich. "
              + "Bitte akzeptieren Sie die Downloadbedingungen und lassen Sie sich ein entsprechendes Token ausstellen.");
    }

    if (isProtectedPackage
        && authentication.isAuthenticated()
        && !authentication.getAuthorities().contains(new SimpleGrantedAuthority(packageName))) {
      throw new PackageAccessDeniedException(
          "Der Zugriff auf das angeforderte Paket ist mit dem beigefügten Token nicht zulässig. "
              + "Bitte akzeptieren Sie die Downloadbedingungen für das angeforderten Paket und lassen Sie sich ein neues Token ausstellen.");
    }

    // Ermitteln des Dateipfades
    Optional<Path> path = packageRepository.getPackagePathIndex(packageName, packageVersion);

    // Sollte es den Pfad nicht geben, dann geben wir ein 404 zurück
    if (path.isEmpty()) {
      throw new NoResourceFoundException(
          exchange.getRequest().getURI(),
          "Die angeforderte Paketversion konnte nicht gefunden werden: "
              + packageName
              + "#"
              + packageVersion);
    }

    // Datei laden
    Flux<DataBuffer> dataBufferFlux = DataBufferUtils.read(path.get(), dataBufferFactory, 4096);

    // Response vorbereiten
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    headers.setContentDisposition(
        ContentDisposition.attachment()
            .filename(packageName + "-" + packageVersion + ".tar.gz")
            .build());

    // In Abhängikeit davon, ob das Paket geschützt ist oder nicht, setzen wir den
    // Cache-Control-Header.
    if (isProtectedPackage) {
      // Wenn das Paket geschützt ist, dann darf es nur private gecached werden.
      headers.setCacheControl(CacheControl.maxAge(3600, TimeUnit.SECONDS).cachePrivate());
    } else {
      // Wenn das Paket nicht geschützt ist, dann cachen wir es für eine Stunde
      // Hinweis: Sollten Anfragen für ungeschützte Pakete trotzdem einen Authorization-Header
      // enthalten, dann wird der Cache-Control-Header von Proxies häufig ignoriert und der Content
      // wird nicht gecacht.
      headers.setCacheControl(CacheControl.maxAge(3600, TimeUnit.SECONDS).cachePublic());
    }

    return new ResponseEntity<>(dataBufferFlux, headers, HttpStatus.OK);
  }
}
