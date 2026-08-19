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
import lombok.*;
import org.semver4j.Semver;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
@Setter
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({"_id", "name", "description", "dist-tags", "versions"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "An object listing package metadata and all individual versions")
public class FhirPackageInfo {

    @JsonProperty("_id")
    @Schema(description = "Package Id", example = "bfarm.terminologien.ops")
    private String id;

    @JsonProperty("name")
    @Schema(description = "Package name", example = "bfarm.terminologien.ops")
    private String name;

    @JsonProperty("description")
    @Schema(description = "Package description",
            example = "Das Package enthält das Systematische Verzeichnis des Operationen- und Prozedurenschlüssels (OPS) im FHIR-Format...")
    private String description;

    @JsonProperty("versions")
    @Schema(
            description = "Dictionary object of package versions",
            title = "Package versions",
            additionalPropertiesSchema = FhirPackageVersionInfo.class)
    private ConcurrentHashMap<String, FhirPackageVersionInfo> versions;

    @JsonProperty("dist-tags")
    @Schema(
            title = "Distribution tags", description = "Tags describing specific package versions")
    private ConcurrentMap<String, String> distTags;

    @JsonIgnore
    public String getLatestDescription() {
        if (versions != null) {
            List<Semver> versionList = versions.keySet().stream().map(Semver::new).toList();
            Semver latest = versionList.stream().max(Semver::compareTo).orElse(null);
            if (latest != null) {
                return versions.get(latest.getVersion()).getDescription();
            }
        }
        return null;
    }

    @JsonIgnore
    public ConcurrentMap<String, String> getLatestDistTags() {

        List<Semver> versionList;
        if (versions != null) {
            versionList = versions.keySet().stream().map(Semver::new).toList();
        } else {
            versionList = List.of();
        }

        Semver latest = versionList.stream().max(Semver::compareTo).orElse(null);
        if (latest != null) {

            return new ConcurrentHashMap<>(Map.of("latest", latest.getVersion()));
        } else {
            return new ConcurrentHashMap<>();
        }
    }
}
