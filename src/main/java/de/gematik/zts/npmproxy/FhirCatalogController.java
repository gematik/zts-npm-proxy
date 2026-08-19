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

import de.gematik.zts.npmproxy.exceptions.ServiceUnavailableException;
import de.gematik.zts.npmproxy.model.FhirPackageBaseInfo;
import de.gematik.zts.npmproxy.model.SearchPackageParameters;
import de.gematik.zts.npmproxy.repository.LuceneBackedPackageRepository;
import de.gematik.zts.npmproxy.validation.ValidUri;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

/** Implementiert die von Außen aufrufbaren REST-Operationen für den FHIR-NPM-Proxy. */
@RestController
@Validated
@Order(2)
@Slf4j
@Tag(name = "Package-API", description = "API for accessing FHIR NPM packages")
public class FhirCatalogController {

  private final LuceneBackedPackageRepository packageRepository;

  public FhirCatalogController(LuceneBackedPackageRepository packageRepository) {
    this.packageRepository = packageRepository;
  }

  @GetMapping(value = "${proxy.npm-path}/catalog", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Search FHIR packages",
      description =
          "Search for FHIR packages by name, canonical and other attributes.\n* Can be used for populating intellisense dropdowns for package search.\n* Does not intend to follow NPM in all aspects and adds extra FHIR specific searches, like canonicals and FHIR versions.\n")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "A list of package objects",
            content =
                @Content(
                    mediaType = "application/json",
                    array =
                        @ArraySchema(
                            schema = @Schema(implementation = FhirPackageBaseInfo.class)))),
      })
  public Mono<ResponseEntity<Object>> handleCatalogRequest(
      ServerWebExchange exchange,
      Authentication authentication,
      @RequestParam(required = false)
          @Parameter(
              description = "Search by (part of) a package name",
              example = "bfarm.terminologien.ops")
          @Pattern(
              regexp = REGEXP_PACKAGE_NAME_CATALOG,
              message = MESSAGE_REGEXP_PACKAGE_NAME_CATALOG)
          String name,
      @RequestParam(required = false)
          @Parameter(
              description = "Search for packages with a version containing this term",
              example = "2025.0.0")
          @Pattern(
              regexp = REGEXP_PACKAGE_VERSION_CATALOG,
              message = MESSAGE_REGEXP_PACKAGE_VERSION_CATALOG)
          String version,
      @RequestParam(required = false)
          @Parameter(
              description =
                  "Search for packages or resource contained in it with this term in their canonical. <br/>"
                      + "The canonical can be a full URL or a part of it, e.g. `http://fhir.de/CodeSystem/bfarm/ops` or `http://fhir.de/CodeSystem`.<br/>"
                      + "The canonical can also contain a (full) version, e.g. `http://fhir.de/CodeSystem/bfarm/ops|2025`.<br/>"
                      + "Note that the pipe `|` must be URL-encoded as `%7C`.<br/>"
                      + "URIs are also supported, e.g. `urn:oid:1.2.276.0.76.5.549`, for those the same rules apply as for URLs.<br/>",
              example = "http://fhir.de/CodeSystem/bfarm/ops|2025")
          // As there is a problem with unicode characters in the swagger ui when using a pattern,
          // this parameter is validated in the code
          String canonical,
      @RequestParam(required = false)
          @Parameter(description = "Search for packages with this exact canonical", example = "")
          @ValidUri(message = MESSAGE_VALID_PKG_CANONICAL)
          String pkgcanonical,
      @RequestParam(required = false)
          @Parameter(description = "Limit search by FHIR version", example = "R4")
          @Pattern(regexp = REGEXP_FHIR_VERSION, message = MESSAGE_REGEXP_FHIR_VERSION)
          String fhirVersion,
      @RequestParam(required = false, defaultValue = "false")
          @Parameter(
              description = "Whether to include or exclude prerelease package versions",
              example = "false")
          Boolean prerelease,
      @RequestParam(required = false, defaultValue = "false")
          @Parameter(
              description =
                  "Whether to include or exclude packages, where all package versions are unlisted",
              example = "false")
          Boolean unlisted,
      @RequestParam(required = false, name = "protected")
          @Parameter(
              description =
                  "Whether to include or exclude protected packages - if not set, all packages are returned",
              example = "")
          Boolean protectedPackage,
      @RequestParam(required = false, name = "keyword")
          @Parameter(
              description =
                  "A list of keywords to search for in the package metadata.\n* Keywords can be separated by comma for a `logical OR` (e.g. keyword=OPS,ICD-10-GM). \n* If the parameter is repeated, the keywords are combined with a `logical AND` (e.g. keyword=OPS&keyword=ICD-10-GM).",
              example = "[\"ops\"]")
          @Size(max = MAX_KEYWORDS, message = MESSAGE_TOO_MANY_KEYWORDS)
          // As there is a problem with unicode characters in the swagger ui when using a pattern,
          // this parameter is validated in the code
          // additionally the keywords are directly taken from the request, because Spring Boot is
          // interpreting comma-separated parameters as a list
          List<String> keywordParams,
      ServerHttpRequest request) {

    // Abbruch der Verarbeitung, falls noch kein initiales Update der Paketinformationen
    // durchgeführt wurde
    if (!packageRepository.isInitialUpdateSucceeded()) {
      return Mono.error(
          new ServiceUnavailableException(
              "Der Dienst wurde nicht korrekt initialisiert. Bitte versuchen Sie es später erneut."));
    }

    // validate the uri parts
    String canonicalValue = null;
    String canonicalVersion = null;

    if (canonical != null) {
      if (!canonical.matches(REGEXP_URI_PART_WITH_VERSION)) {
        return Mono.error(new ServerWebInputException(MESSAGE_REGEXP_URI_PART));
      }

      String[] parts = canonical.split(REGEXP_CANONICAL_VERSION_SEPARATOR);
      if (parts.length > 2) {
        return Mono.error(new ServerWebInputException(MESSAGE_CANONICAL_VERSION_MISSING));
      }

      canonicalValue = parts[0];
      canonicalVersion = (parts.length == 2) ? parts[1] : null;
    }
    // get the keywords from the request, as Spring boot creates multiple entries in the list, if
    // the parameter values are separated by comma
    List<String> keywords = request.getQueryParams().get("keyword");

    if (keywords == null) {
      keywords = Collections.emptyList();
    }
    // check the keywords for validity
    if (keywords.stream().anyMatch(keyword -> !keyword.matches(REGEXP_KEYWORD))) {
      return Mono.error(new ServerWebInputException(MESSAGE_REGEXP_KEYWORD));
    }

    List<String> sanitizedKeywords = keywords.stream().map(this::sanitizeKeyword).toList();

    // Übernahme des Nutzernamens aus dem Token in die Exchange-Attribute
    exchange.getAttributes().put(ATTRIBUTE_USER, authentication.getName());

    SearchPackageParameters searchPackageParameters =
        new SearchPackageParameters(
            Optional.ofNullable(name),
            Optional.ofNullable(version),
            Optional.ofNullable(canonicalValue),
            Optional.ofNullable(canonicalVersion),
            Optional.ofNullable(pkgcanonical),
            Optional.ofNullable(fhirVersion),
            Optional.ofNullable(prerelease),
            Optional.ofNullable(unlisted),
            Optional.ofNullable(protectedPackage),
            !sanitizedKeywords.isEmpty() ? Optional.of(sanitizedKeywords) : Optional.empty());

    List<FhirPackageBaseInfo> matchingPackageBaseInfo =
        packageRepository.searchPackages(searchPackageParameters);

    return Mono.just(
        ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(600, TimeUnit.SECONDS).cachePublic())
            .body(matchingPackageBaseInfo));
  }

  private String sanitizeKeyword(String keyword) {
    if (keyword == null) {
      return null;
    }
    // Remove any leading/trailing whitespace
    return keyword.trim();
  }
}
