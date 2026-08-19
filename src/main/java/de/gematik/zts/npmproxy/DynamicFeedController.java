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
import static de.gematik.zts.npmproxy.NpmProxyConstants.MESSAGE_REGEXP_KEYWORD;

import com.rometools.rome.io.FeedException;
import de.gematik.zts.npmproxy.exceptions.ServiceUnavailableException;
import de.gematik.zts.npmproxy.feeds.DynamicFeedGenerator;
import de.gematik.zts.npmproxy.model.FeedType;
import de.gematik.zts.npmproxy.model.FhirPackageVersionInfo;
import de.gematik.zts.npmproxy.repository.LuceneBackedPackageRepository;
import de.gematik.zts.npmproxy.security.SecureUrlBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@RestController
@Validated
@Order(3)
@Slf4j
@Tag(name = "Feeds-API", description = "API for accessing package feeds")
@RequestMapping("${proxy.feeds-path:/feeds}")
public class DynamicFeedController {

  private final LuceneBackedPackageRepository packageRepository;
  private final DynamicFeedGenerator feedGenerator;
  private final SecureUrlBuilder urlBuilder;

  public DynamicFeedController(
      LuceneBackedPackageRepository packageRepository,
      DynamicFeedGenerator feedGenerator,
      SecureUrlBuilder urlBuilder) {
    this.packageRepository = packageRepository;
    this.feedGenerator = feedGenerator;
    this.urlBuilder = urlBuilder;
  }

  /**
   * Abrufen des RSS-Feeds für Packages, die in Richtung der offiziellen HL7-FHIR Package Registry
   * veröffentlicht werden sollen
   *
   * @return Health Response, die an den Client zurückgegeben wird
   */
  @GetMapping(value = "/**", produces = MediaType.APPLICATION_XML_VALUE)
  @Operation(
      summary = "Retrieve dynamic RSS Feed",
      description = "Fetches the dynamic RSS feed content in XML format.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved dynamic RSS feed",
            content = @Content(mediaType = MediaType.APPLICATION_XML_VALUE))
      })
  public Mono<ResponseEntity<Object>> handleGetDynamicRssFeed(
      @RequestParam(required = false)
          @Parameter(
              description = "Retrieve package feed for a specific publisher",
              example = "BfArM")
          // As there is a problem with unicode characters in the swagger ui when using a pattern,
          // this parameter is validated in the code
          String publisher,
      @RequestParam(required = false)
          @Parameter(
              description = "Retrieve package feed for a specific package name",
              example = "bfarm.terminologien.ops")
          @Pattern(regexp = REGEXP_PACKAGE_NAME, message = MESSAGE_REGEXP_PACKAGE_NAME)
          String packageName,
      @RequestParam(required = false)
          @Parameter(description = "Retrieve package feed for a specific keyword", example = "OPS")
          // As there is a problem with unicode characters in the swagger ui when using a pattern,
          // this parameter is validated in the code
          String keyword,
      @RequestParam(required = false, defaultValue = "package")
          @Parameter(
              description = "Type of the feed, either \"package\" or \"publication\"",
              example = "package")
          FeedType type,
      @RequestParam(required = false, name = "publishToHl7")
          @Parameter(
              description =
                  "Retrieve package feed for packages that are published / not published to the HL7 FHIR package registry - if not set, all packages are returned",
              example = "")
          Boolean publishToHl7,
      ServerHttpRequest request)
      throws FeedException {

    if (!packageRepository.isInitialUpdateSucceeded()) {
      return Mono.error(
          new ServiceUnavailableException("Der Dienst wurde nicht korrekt initialisiert."));
    }

    // validate the publisher name
    if (publisher != null && !publisher.matches(REGEXP_FEED_PUBLISHER)) {
      return Mono.error(new ServerWebInputException(MESSAGE_REGEXP_FEED_PUBLISHER));
    }

    // validate the keywords
    if (keyword != null && !keyword.matches(REGEXP_KEYWORD)) {
      return Mono.error(new ServerWebInputException(MESSAGE_REGEXP_KEYWORD));
    }

    // Build secure URL using configuration instead of request
    String requestUrl = urlBuilder.buildFeedUrl(request.getURI().getQuery());

    List<FhirPackageVersionInfo> packageVersionInfos =
        packageRepository.getPackageVersionInfos(publisher, packageName, keyword, publishToHl7);
    return Mono.just(
        ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(60, TimeUnit.MINUTES).cachePublic().cachePrivate())
            .body(
                feedGenerator.createFeed(
                    packageVersionInfos,
                    publisher,
                    packageName,
                    keyword,
                    requestUrl,
                    type,
                    publishToHl7)));
  }
}
