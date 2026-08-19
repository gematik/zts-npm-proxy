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

package de.gematik.zts.npmproxy.model;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
// Importing LocalDateTime
import java.util.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class FhirPackageArtifactRegistryAnnotations {

  // Constants for map keys
  public static final String KEY_STATUS = "status";
  public static final String KEY_PUBLISH_TO_HL7 = "publish-to-hl7";
  public static final String KEY_ADDITIONAL_KEYWORDS = "additional-keywords";
  public static final String KEY_PROTECTED = "protected";
  public static final String KEY_VISIBILITY = "visibility";
  public static final String KEY_LINK_TO_ZTS = "link-to-zts";
  public static final String KEY_LINK_TO_CONDITIONS = "link-to-conditions";
  public static final String KEY_CREATED_AT = "created-at"; // New constant for created_at
  public static final String KEY_UPDATED_AT = "updated-at"; // New constant for updated_at
  private static final String KEYWORD_DELIMITER_CHAR = ",";
  private Status status;
  private Boolean publishToHl7; // Changed to Boolean to allow null
  private List<String> additionalKeywords;
  private Boolean protectedDownload; // Changed to Boolean to allow null
  private Boolean visibility; // Changed to Boolean to allow null
  private URL linkToZts;
  private URL linkToConditions;
  private Instant createdAt; // New field for created timestamp
  private Instant updatedAt; // New field for updated timestamp

  public static FhirPackageArtifactRegistryAnnotations fromAnnotationsMap(Map<String, String> map)
      throws IllegalArgumentException, MalformedURLException {
    FhirPackageArtifactRegistryAnnotations annotations =
        new FhirPackageArtifactRegistryAnnotations();

    // Set enum status, will remain null if key is missing
    annotations.setStatus(Status.fromString(map.get(KEY_STATUS)));

    // Set boolean values, will remain null if key is missing
    String publishedToHl7Value = map.get(KEY_PUBLISH_TO_HL7);
    annotations.setPublishToHl7(
        publishedToHl7Value != null ? Boolean.parseBoolean(publishedToHl7Value) : null);

    String protectedValue = map.get(KEY_PROTECTED);
    annotations.setProtectedDownload(
        protectedValue != null ? Boolean.parseBoolean(protectedValue) : null);

    String visibilityValue = map.get(KEY_VISIBILITY);
    annotations.setVisibility(
        visibilityValue != null ? Boolean.parseBoolean(visibilityValue) : null);

    // Set additional keywords, will remain null if key is missing
    String keywords = map.get(KEY_ADDITIONAL_KEYWORDS);
    annotations.setAdditionalKeywords(
        keywords != null
            ? Arrays.asList(keywords.split(KEYWORD_DELIMITER_CHAR))
            : new ArrayList<>());

    // Set URLs, will remain null if keys are missing
    String ztsUrl = map.get(KEY_LINK_TO_ZTS);
    String conditionsUrl = map.get(KEY_LINK_TO_CONDITIONS);
    if (ztsUrl != null) {
      annotations.setLinkToZts(URI.create(ztsUrl).toURL());
    }
    if (conditionsUrl != null) {
      annotations.setLinkToConditions(URI.create(conditionsUrl).toURL());
    }

    // Set LocalDateTime, will remain null if keys are missing
    String createdAtValue = map.get(KEY_CREATED_AT);
    annotations.setCreatedAt(createdAtValue != null ? Instant.parse(createdAtValue) : null);

    String updatedAtValue = map.get(KEY_UPDATED_AT);
    annotations.setUpdatedAt(updatedAtValue != null ? Instant.parse(updatedAtValue) : null);

    return annotations;
  }

  public Map<String, String> toAnnotationsMap() {
    Map<String, String> map = new HashMap<>();

    // Add status
    if (status != null) {
      map.put(KEY_STATUS, status.getName());
    }

    // Add boolean values
    if (publishToHl7 != null) {
      map.put(KEY_PUBLISH_TO_HL7, publishToHl7.toString());
    }
    if (protectedDownload != null) {
      map.put(KEY_PROTECTED, protectedDownload.toString());
    }
    if (visibility != null) {
      map.put(KEY_VISIBILITY, visibility.toString());
    }

    // Add additional keywords
    if (additionalKeywords != null && !additionalKeywords.isEmpty()) {
      map.put(KEY_ADDITIONAL_KEYWORDS, String.join(KEYWORD_DELIMITER_CHAR, additionalKeywords));
    }

    // Add URLs
    if (linkToZts != null) {
      map.put(KEY_LINK_TO_ZTS, linkToZts.toString());
    }
    if (linkToConditions != null) {
      map.put(KEY_LINK_TO_CONDITIONS, linkToConditions.toString());
    }

    // Add LocalDateTime
    if (createdAt != null) {
      map.put(KEY_CREATED_AT, createdAt.toString());
    }
    if (updatedAt != null) {
      map.put(KEY_UPDATED_AT, updatedAt.toString());
    }

    return map;
  }

  @Override
  public String toString() {
    return "FhirPackageArtifactRegistryAnnotations{"
        + "status="
        + status
        + ", publishToHl7="
        + publishToHl7
        + ", additionalKeywords="
        + additionalKeywords
        + ", protectedDownload="
        + protectedDownload
        + ", visibility="
        + visibility
        + ", linkToZts="
        + linkToZts
        + ", linkToConditions="
        + linkToConditions
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + '}';
  }

  public boolean isValid() {
    return status != null
        && publishToHl7 != null
        && additionalKeywords != null
        && visibility != null
        && linkToZts != null
        && (!protectedDownload || linkToConditions != null)
        && createdAt != null
        && updatedAt != null;
  }

  @Getter
  public enum Status {
    ACTIVE("active"),
    DEPRECATED("deprecated"),
    IN_DEVELOPMENT("in-development");

    // Map to store string to enum mappings
    private static final Map<String, Status> NAME_TO_STATUS_MAP = createNameMap();
    private final String name;

    Status(String name) {
      this.name = name;
    }

    private static Map<String, Status> createNameMap() {
      Map<String, Status> map = new HashMap<>();
      for (Status s : values()) {
        map.put(s.name, s);
      }
      return Collections.unmodifiableMap(map);
    }

    public static Status fromString(String name) {
      if (name == null) {
        return null; // Return null if the input is null
      }
      return NAME_TO_STATUS_MAP.get(name.toLowerCase());
    }
  }
}
