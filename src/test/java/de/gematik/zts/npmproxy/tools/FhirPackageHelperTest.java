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

import static org.junit.jupiter.api.Assertions.*;

import de.gematik.zts.npmproxy.exceptions.FhirVersionException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class FhirPackageHelperTest {

  @Test
  void testCheckFhirVersion_ValidR4()  {
    List<String> fhirVersions = List.of("4.0.1");
    String result = FhirPackageHelper.checkFhirVersion(fhirVersions);
    assertEquals("R4", result);
  }

  @Test
  void testCheckFhirVersion_InvalidR4B() {
    List<String> fhirVersions = List.of("4.3.0");
    Exception exception =
        assertThrows(
                FhirVersionException.class,
            () -> {
              FhirPackageHelper.checkFhirVersion(fhirVersions);
            });
    assertEquals("FhirVersion is not R4: R4B", exception.getMessage());
  }

  @Test
  void testCheckFhirVersion_InvalidSize() {
    List<String> fhirVersions = List.of("4.0.1", "4.3.0");
    Exception exception =
        assertThrows(
                FhirVersionException.class,
            () -> {
              FhirPackageHelper.checkFhirVersion(fhirVersions);
            });
    assertEquals("FhirVersions size is not 1: 2", exception.getMessage());
  }

  @Test
  void testCheckFhirVersion_NullFhirVersion() {
    List<String> fhirVersions = Collections.singletonList("1.0.0");
    Exception exception =
        assertThrows(
                FhirVersionException.class,
            () -> {
              FhirPackageHelper.checkFhirVersion(fhirVersions);
            });
    assertEquals("FhirVersion is null", exception.getMessage());
  }

  @Test
  void testCheckFhirVersion_NotR4() {
    List<String> fhirVersions = List.of("3.0.2");
    Exception exception =
        assertThrows(
                FhirVersionException.class,
            () -> {
              FhirPackageHelper.checkFhirVersion(fhirVersions);
            });
    assertEquals("FhirVersion is not R4: STU3", exception.getMessage());
  }
}
