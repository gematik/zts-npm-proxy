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

import static de.gematik.zts.npmproxy.model.FhirPackageArtifactRegistryAnnotations.*;
import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class FhirPackageArtifactRegistryAnnotationsTest {

  @NotNull
  private static Map<String, String> createAnnotationsMap() {
    Map<String, String> annotationsMap = new HashMap<>();
    annotationsMap.put(KEY_STATUS, "active");
    annotationsMap.put(KEY_PUBLISH_TO_HL7, "true");
    annotationsMap.put(KEY_PROTECTED, "true");
    annotationsMap.put(KEY_VISIBILITY, "false");
    annotationsMap.put(KEY_ADDITIONAL_KEYWORDS, "kw1,kw2");
    annotationsMap.put(KEY_LINK_TO_ZTS, "http://example.com/zts");
    annotationsMap.put(KEY_LINK_TO_CONDITIONS, "http://example.com/conditions");
    annotationsMap.put(KEY_CREATED_AT, "2023-01-01T12:00:00Z");
    annotationsMap.put(KEY_UPDATED_AT, "2023-01-02T12:00:00Z");
    return annotationsMap;
  }

  @Test
  void testFromAnnotationsMap_withAllFields() throws Exception {
    // Arrange
    Map<String, String> annotationsMap = createAnnotationsMap();

    // Act
    FhirPackageArtifactRegistryAnnotations annotations =
        FhirPackageArtifactRegistryAnnotations.fromAnnotationsMap(annotationsMap);

    // Assert
    assertThat(annotations.getStatus())
        .isEqualTo(FhirPackageArtifactRegistryAnnotations.Status.ACTIVE);
    assertThat(annotations.getPublishToHl7()).isTrue();
    assertThat(annotations.getProtectedDownload()).isTrue();
    assertThat(annotations.getVisibility()).isFalse();
    assertThat(annotations.getAdditionalKeywords()).containsExactly("kw1", "kw2");
    assertThat(annotations.getLinkToZts()).isEqualTo(new URI("http://example.com/zts").toURL());
    assertThat(annotations.getLinkToConditions())
        .isEqualTo(new URI("http://example.com/conditions").toURL());
    assertThat(annotations.getCreatedAt()).isEqualTo(Instant.parse("2023-01-01T12:00:00Z"));
    assertThat(annotations.getUpdatedAt()).isEqualTo(Instant.parse("2023-01-02T12:00:00Z"));
  }

  @Test
  void testFromAnnotationsMap_withPartialFields() throws Exception {
    // Arrange
    Map<String, String> annotationsMap = new HashMap<>();
    annotationsMap.put(FhirPackageArtifactRegistryAnnotations.KEY_STATUS, "deprecated");
    // No booleans, no keywords, no links, no timestamps

    // Act
    FhirPackageArtifactRegistryAnnotations annotations =
        FhirPackageArtifactRegistryAnnotations.fromAnnotationsMap(annotationsMap);

    // Assert
    assertThat(annotations.getStatus())
        .isEqualTo(FhirPackageArtifactRegistryAnnotations.Status.DEPRECATED);
    assertThat(annotations.getPublishToHl7()).isNull();
    assertThat(annotations.getProtectedDownload()).isNull();
    assertThat(annotations.getVisibility()).isNull();
    assertThat(annotations.getAdditionalKeywords()).isEmpty();
    assertThat(annotations.getLinkToZts()).isNull();
    assertThat(annotations.getLinkToConditions()).isNull();
    assertThat(annotations.getCreatedAt()).isNull();
    assertThat(annotations.getUpdatedAt()).isNull();
  }

  @Test
  void testFromAnnotationsMap_withMalformedUrl() {
    // Arrange
    Map<String, String> annotationsMap = new HashMap<>();
    annotationsMap.put(FhirPackageArtifactRegistryAnnotations.KEY_LINK_TO_ZTS, "not-a-valid-url");

    // Act & Assert
    assertThatThrownBy(
            () -> FhirPackageArtifactRegistryAnnotations.fromAnnotationsMap(annotationsMap))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testFromAnnotationsMap_withInvalidInstant() {
    // Arrange
    Map<String, String> annotationsMap = new HashMap<>();
    annotationsMap.put(FhirPackageArtifactRegistryAnnotations.KEY_CREATED_AT, "invalid-date");

    // Act & Assert
    assertThatThrownBy(
            () -> FhirPackageArtifactRegistryAnnotations.fromAnnotationsMap(annotationsMap))
        .isInstanceOf(java.time.format.DateTimeParseException.class);
  }

  @Test
  void testToAnnotationsMap_withAllFields() throws Exception {
    // Arrange
    FhirPackageArtifactRegistryAnnotations annotations =
        FhirPackageArtifactRegistryAnnotations.builder()
            .status(FhirPackageArtifactRegistryAnnotations.Status.IN_DEVELOPMENT)
            .publishToHl7(true)
            .protectedDownload(false)
            .visibility(true)
            .additionalKeywords(Arrays.asList("k1", "k2"))
            .linkToZts(new URI("http://zts.example.com").toURL())
            .linkToConditions(new URI("http://conditions.example.com").toURL())
            .createdAt(Instant.parse("2023-01-03T12:00:00Z"))
            .updatedAt(Instant.parse("2023-01-04T12:00:00Z"))
            .build();

    // Act
    Map<String, String> resultMap = annotations.toAnnotationsMap();

    // Assert
    assertThat(resultMap)
        .containsEntry(KEY_STATUS, "in-development")
        .containsEntry(KEY_PUBLISH_TO_HL7, "true")
        .containsEntry(KEY_PROTECTED, "false")
        .containsEntry(KEY_VISIBILITY, "true")
        .containsEntry(KEY_ADDITIONAL_KEYWORDS, "k1,k2")
        .containsEntry(KEY_LINK_TO_ZTS, "http://zts.example.com")
        .containsEntry(KEY_LINK_TO_CONDITIONS, "http://conditions.example.com")
        .containsEntry(KEY_CREATED_AT, "2023-01-03T12:00:00Z")
        .containsEntry(KEY_UPDATED_AT, "2023-01-04T12:00:00Z");
  }

  @Test
  void testIsValid() throws Exception {
    // Arrange
    FhirPackageArtifactRegistryAnnotations annotations =
        FhirPackageArtifactRegistryAnnotations.builder()
            .status(FhirPackageArtifactRegistryAnnotations.Status.ACTIVE)
            .publishToHl7(true)
            .additionalKeywords(Collections.singletonList("kw"))
            .protectedDownload(true)
            .visibility(true)
            .linkToZts(new URI("http://example.com/zts").toURL())
            .linkToConditions(new URI("http://example.com/conditions").toURL())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    // Act & Assert
    assertThat(annotations.isValid()).isTrue();

    // Make one field null
    annotations.setStatus(null);
    assertThat(annotations.isValid()).isFalse();
  }

  @Test
  void testEqualsAndHashCode() throws Exception {
    // Arrange
    Instant now = Instant.now();
    FhirPackageArtifactRegistryAnnotations annotations1 =
        FhirPackageArtifactRegistryAnnotations.builder()
            .status(FhirPackageArtifactRegistryAnnotations.Status.ACTIVE)
            .publishToHl7(true)
            .protectedDownload(false)
            .visibility(true)
            .additionalKeywords(Arrays.asList("abc", "def"))
            .linkToZts(new URI("http://example.com/zts").toURL())
            .linkToConditions(new URI("http://example.com/conditions").toURL())
            .createdAt(now)
            .updatedAt(now)
            .build();

    FhirPackageArtifactRegistryAnnotations annotations2 =
        FhirPackageArtifactRegistryAnnotations.builder()
            .status(FhirPackageArtifactRegistryAnnotations.Status.ACTIVE)
            .publishToHl7(true)
            .protectedDownload(false)
            .visibility(true)
            .additionalKeywords(Arrays.asList("abc", "def"))
            .linkToZts(new URI("http://example.com/zts").toURL())
            .linkToConditions(new URI("http://example.com/conditions").toURL())
            .createdAt(now)
            .updatedAt(now)
            .build();

    // Act & Assert
    assertThat(annotations1).isEqualTo(annotations2);
    assertThat(annotations1.hashCode()).hasSameHashCodeAs(annotations2.hashCode());

    annotations2.setStatus(FhirPackageArtifactRegistryAnnotations.Status.DEPRECATED);
    assertThat(annotations1).isNotEqualTo(annotations2);
    assertThat(annotations1.hashCode()).isNotEqualTo(annotations2.hashCode());
  }

  @Test
  void testToString() {
    // Just verify it doesn't throw and contains class name
    FhirPackageArtifactRegistryAnnotations annotations =
        new FhirPackageArtifactRegistryAnnotations();
    assertThat(annotations.toString()).contains("FhirPackageArtifactRegistryAnnotations");
  }
}
