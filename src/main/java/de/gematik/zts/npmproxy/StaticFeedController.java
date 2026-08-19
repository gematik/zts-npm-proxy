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

import de.gematik.zts.npmproxy.exceptions.ServiceUnavailableException;
import de.gematik.zts.npmproxy.feeds.FeedGenerator;
import de.gematik.zts.npmproxy.repository.LuceneBackedPackageRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Validated
@Order(3)
@Slf4j
@Tag(name = "Feeds-API", description = "API for accessing package feeds")
@RequestMapping("${proxy.feeds-path:/feeds}")
public class StaticFeedController {

  private final LuceneBackedPackageRepository packageRepository;
  private final FeedGenerator feedGenerator;

  public StaticFeedController(
      LuceneBackedPackageRepository packageRepository, FeedGenerator feedGenerator) {
    this.packageRepository = packageRepository;
    this.feedGenerator = feedGenerator;
  }

  /**
   * Abrufen des RSS-Feeds für Packages, die in Richtung der offiziellen HL7-FHIR Package Registry
   * veröffentlicht werden sollen
   *
   * @return Health Response, die an den Client zurückgegeben wird
   */
  @GetMapping(
      value = "${proxy.feeds-path-hl7:/package-feed.xml}",
      produces = MediaType.APPLICATION_XML_VALUE)
  @Operation(
      summary = "Retrieve HL7 RSS Feed",
      description = "Fetches the HL7 RSS feed content in XML format.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved the HL7 RSS feed.",
            content = @Content(mediaType = MediaType.APPLICATION_XML_VALUE))
      })
  public Mono<ResponseEntity<Object>> handleGetRssFeedHl7() {

    // Abbruch der Verarbeitung, falls noch kein initiales Update der Paketinformationen
    // durchgeführt wurde. Dies wäre aktuell zwar noch nicht notwendig, da wir den Feed (noch) nicht
    // dynamisch mit Inhalt füllen. Für später wollen wir das jedoch nicht vergessen.
    if (!packageRepository.isInitialUpdateSucceeded()) {
      return Mono.error(
          new ServiceUnavailableException("Der Dienst wurde nicht korrekt initialisiert."));
    }

    return Mono.just(
        ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(60, TimeUnit.MINUTES).cachePublic().cachePrivate())
            .body(feedGenerator.getFeedContent()));
  }
}
