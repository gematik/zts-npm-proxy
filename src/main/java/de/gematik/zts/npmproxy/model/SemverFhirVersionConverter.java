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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.semver4j.Semver;

@UtilityClass
public class SemverFhirVersionConverter {
  private static final Map<String, String> semverToFhirMap = new HashMap<>();
  private static final Map<String, String> fhirToSemverMap = new HashMap<>();

  static {
    semverToFhirMap.put("1.0.2", "DSTU2");
    semverToFhirMap.put("3.0.2", "STU3");
    semverToFhirMap.put("4.0.1", "R4");
    semverToFhirMap.put("4.3.0", "R4B");
    semverToFhirMap.put("5.0.0", "R5");
  }

  static {
    fhirToSemverMap.put("DSTU2", "1.0.2");
    fhirToSemverMap.put("STU3", "3.0.2");
    fhirToSemverMap.put("R4", "4.0.1");
    fhirToSemverMap.put("R4B", "4.3.0");
    fhirToSemverMap.put("R5", "5.0.0");
  }

  public static String getHighestFhirVersion(List<String> semverList) {
    Semver latest = semverList.stream().map(Semver::new).max(Semver::compareTo).orElse(null);
    return latest != null ? semverToFhirMap.get(latest.getVersion()) : null;
  }

  public static String getSemverFromFhirVersion(String fhirVersion) {
    return fhirToSemverMap.getOrDefault(fhirVersion, null);
  }

  public static String getFhirVersionFromSemver(String semver) {
    return semverToFhirMap.getOrDefault(semver, null);
  }
}
