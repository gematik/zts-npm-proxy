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
import java.util.List;
import lombok.*;

@Getter
@Setter
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({"name", "description", "fhirVersion"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "An object describing a package")
public class FhirPackageBaseInfo {

  @JsonProperty("keywords")
  @Schema(description = "Package keywords", example = "ops")
  List<String> keywords;
  @JsonProperty("name")
  @Schema(description = "Package name", example = "bfarm.terminologien.ops")
  private String name;
  @JsonProperty("description")
  @Schema(
      description = "Package description",
      example =
          "Das Package enthält das Systematische Verzeichnis des Operationen- und Prozedurenschlüssels (OPS) im FHIR-Format...")
  private String description;
  @JsonProperty("packageVersions")
  @Schema(
          description = "Package versions that match the search criteria")
  private List<String> packageVersions;
  @JsonProperty("fhirVersion")
  @Schema(description = "Package FHIR version", example = "R4")
  private String fhirVersion;
}
