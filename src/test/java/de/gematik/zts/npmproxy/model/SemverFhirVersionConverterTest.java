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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemverFhirVersionConverterTest {

  @Test
  void testGetHighestFhirVersion_withMultipleVersions() {
    List<String> semverList = Arrays.asList("1.0.2", "3.0.2", "4.0.1", "4.3.0", "5.0.0");
    String result = SemverFhirVersionConverter.getHighestFhirVersion(semverList);
    assertEquals("R5", result);
  }

  @Test
  void testGetHighestFhirVersion_withSingleVersion() {
    List<String> semverList = List.of("4.0.1");
    String result = SemverFhirVersionConverter.getHighestFhirVersion(semverList);
    assertEquals("R4", result);
  }

  @Test
  void testGetHighestFhirVersion_withNoVersions() {
    List<String> semverList = List.of();
    String result = SemverFhirVersionConverter.getHighestFhirVersion(semverList);
    assertNull(result);
  }

  @Test
  void testGetHighestFhirVersion_withOutOfOrderVersions() {
    List<String> semverList = Arrays.asList("4.3.0", "1.0.2", "3.0.2");
    String result = SemverFhirVersionConverter.getHighestFhirVersion(semverList);
    assertEquals("R4B", result);
  }

  @Test
  void testGetHighestFhirVersion_withDuplicateVersions() {
    List<String> semverList = Arrays.asList("4.3.0", "4.3.0", "5.0.0");
    String result = SemverFhirVersionConverter.getHighestFhirVersion(semverList);
    assertEquals("R5", result);
  }

  @Test
  void testGetHighestFhirVersion_withVersionsInDescendingOrder() {
    List<String> semverList = Arrays.asList("5.0.0", "4.3.0", "4.0.1");
    String result = SemverFhirVersionConverter.getHighestFhirVersion(semverList);
    assertEquals("R5", result);
  }

  @Test
  void testGetHighestFhirVersion_withNonExistingVersion() {
    List<String> semverList = Arrays.asList("6.0.0", "7.0.0"); // Non-existing versions
    String result = SemverFhirVersionConverter.getHighestFhirVersion(semverList);
    assertNull(result); // Should return null as there is no mapping for these versions
  }
}
