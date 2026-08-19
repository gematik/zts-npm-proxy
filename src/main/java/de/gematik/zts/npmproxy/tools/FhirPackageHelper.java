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

package de.gematik.zts.npmproxy.tools;

import de.gematik.zts.npmproxy.exceptions.FhirVersionException;
import de.gematik.zts.npmproxy.model.SemverFhirVersionConverter;
import java.util.List;

public class FhirPackageHelper {

  private FhirPackageHelper() {}

  public static String checkFhirVersion(List<String> fhirVersions) throws FhirVersionException {
    // we only allow one fhirVersion
    if ((long) fhirVersions.size() != 1) {
      throw new FhirVersionException("FhirVersions size is not 1: " + fhirVersions.size());
    }
    // make sure fhirVersion is R4
    String fhirVersion = SemverFhirVersionConverter.getHighestFhirVersion(fhirVersions);
    if (fhirVersion == null) {
      throw new FhirVersionException("FhirVersion is null");
    }
    if (!fhirVersion.equals("R4")) {
      throw new FhirVersionException("FhirVersion is not R4: " + fhirVersion);
    }

    return fhirVersion;
  }
}
