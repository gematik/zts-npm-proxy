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

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({"name", "version", "description"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    description = "An object describing a specific version of a package",
    title = "FhirPackageVersionInfo")
public class FhirPackageVersionInfo {

  public static final String PACKAGE_DEPRECATION_MESSAGE =
      "This version is deprecated. Please use a different version.";

  @JsonProperty("name")
  @Schema(description = "Package name", example = "bfarm.terminologien.ops")
  private String name;

  @JsonProperty("version")
  @Schema(description = "Package version", example = "2025.0.0")
  private String version;

  @JsonProperty("description")
  @Schema(
      description = "Package description",
      example =
          "Das Package enthält das Systematische Verzeichnis des Operationen- und Prozedurenschlüssels (OPS) im FHIR-Format...")
  private String description;

  @JsonProperty("dist")
  @Schema(
      description = "Distribution information for the version",
      implementation = FhirPackageVersionDistInfo.class)
  private FhirPackageVersionDistInfo dist;

  @JsonProperty("fhirVersion")
  @Schema(description = "Package FHIR version", example = "R4")
  private String fhirVersion;

  @JsonProperty("url")
  @Schema(
      description = "Url for downloading this package",
      example = "https://terminologien.bfarm.de/packages/bfarm.terminologien.ops/2025.0.0")
  private String url;

  @JsonProperty("unlisted")
  @Schema(description = "Indicates if the version is unlisted", example = "false")
  private String unlisted;

  @JsonProperty(value = "deprecated", access = JsonProperty.Access.WRITE_ONLY)
  private String deprecated;

  @JsonProperty("protected")
  @Schema(description = "Indicates if the version of the package is protected", example = "true")
  private Boolean protectedPackage;

  @JsonProperty("download-conditions")
  @Schema(
      description = "Link to the download conditions for the version, if package is protected",
      example = "https://terminologien.bfarm.de/ops-conditions.html")
  private String downloadConditions;

  @JsonProperty("keywords")
  @Schema(description = "A list of keywords associated with the package version", example = "OPS")
  private List<String> keywords;

  @JsonProperty("author")
  @Schema(
      description = "Information about the author of the package version",
      implementation = FhirPackageAuthor.class,
      example = "{ \"name\": \"BfArM\" }")
  private FhirPackageAuthor author;

  @JsonProperty("title")
  @Schema(
      description = "Title of the package version",
      example = "Operationen- und Prozedurenschlüssel (OPS)")
  private String title;

  @JsonProperty("altTitle")
  @Schema(description = "An alternative title of the package version", example = "OPS")
  private String altTitle;

  @JsonIgnore private Set<String> staticKeywords;
  @JsonIgnore private Set<String> dynamicKeywords;
  @JsonIgnore private FhirPackageArtifactRegistryAnnotations annotations;
  @JsonIgnore private Instant createdAt;
  @JsonIgnore private Instant updatedAt;
  @JsonIgnore private String linkToZts;
  @JsonIgnore private Boolean publishToHl7;

  public void setDist(FhirPackageVersionDistInfo dist) {
    // set dist and update url accordingly
    this.dist = dist;
    if (dist != null && dist.getTarball() != null) {
      this.url = dist.getTarball();
    } else {
      this.url = null;
    }
  }

  public List<String> getKeywords() {

    var combined = new HashSet<String>();
    if (staticKeywords != null && !staticKeywords.isEmpty()) {
      combined.addAll(staticKeywords);
    }

    if (dynamicKeywords != null && !dynamicKeywords.isEmpty()) {
      combined.addAll(dynamicKeywords);
    }

    return combined.stream().toList();
  }

  public void setAnnotations(FhirPackageArtifactRegistryAnnotations annotations) {
    // set annotations and update fields accordingly
    this.annotations = annotations;
    if (annotations != null) {
      this.protectedPackage =
          annotations.getProtectedDownload() != null && annotations.getProtectedDownload();
      this.dynamicKeywords =
          annotations.getAdditionalKeywords() != null
              ? annotations.getAdditionalKeywords().stream()
                  .map(String::toLowerCase)
                  .collect(Collectors.toSet())
              : new HashSet<>();
      this.downloadConditions =
          annotations.getLinkToConditions() != null
              ? annotations.getLinkToConditions().toString()
              : null;

      this.linkToZts =
          annotations.getLinkToZts() != null ? annotations.getLinkToZts().toString() : null;

      if (annotations.getStatus() == FhirPackageArtifactRegistryAnnotations.Status.DEPRECATED) {
        this.unlisted = PACKAGE_DEPRECATION_MESSAGE;
        this.deprecated = PACKAGE_DEPRECATION_MESSAGE;
      } else {
        this.unlisted = null;
        this.deprecated = null;
      }

      this.createdAt = annotations.getCreatedAt();
      this.updatedAt = annotations.getUpdatedAt();

      this.publishToHl7 = annotations.getPublishToHl7() != null && annotations.getPublishToHl7();
    }
  }
}
