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

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

class FhirPackageInfoTest {

  @Test
  void testGetLatestDistTags_withVersions() {
    ConcurrentHashMap<String, FhirPackageVersionInfo> versions = new ConcurrentHashMap<>();
    versions.put("1.0.0", new FhirPackageVersionInfo());
    versions.put("2.0.0", new FhirPackageVersionInfo());
    versions.put("1.5.0", new FhirPackageVersionInfo());

    FhirPackageInfo fhirPackageInfo = new FhirPackageInfo();
    fhirPackageInfo.setVersions(versions);

    ConcurrentMap<String, String> latestDistTags = fhirPackageInfo.getLatestDistTags();

    assertNotNull(latestDistTags);
    assertEquals(1, latestDistTags.size());
    assertEquals("2.0.0", latestDistTags.get("latest"));
  }

  @Test
  void testGetLatestDistTags_withoutVersions() {
    FhirPackageInfo fhirPackageInfo = new FhirPackageInfo();
    fhirPackageInfo.setVersions(null);

    ConcurrentMap<String, String> latestDistTags = fhirPackageInfo.getLatestDistTags();

    assertNotNull(latestDistTags);
    assertTrue(latestDistTags.isEmpty());
  }

  @Test
  void testGetLatestDistTags_withEmptyVersions() {
    FhirPackageInfo fhirPackageInfo = new FhirPackageInfo();
    fhirPackageInfo.setVersions(new ConcurrentHashMap<>());

    ConcurrentMap<String, String> latestDistTags = fhirPackageInfo.getLatestDistTags();

    assertNotNull(latestDistTags);
    assertTrue(latestDistTags.isEmpty());
  }

  @Test
  void testGetLatestDescription_withVersions() {
    ConcurrentHashMap<String, FhirPackageVersionInfo> versions = new ConcurrentHashMap<>();
    FhirPackageVersionInfo version1 = new FhirPackageVersionInfo();
    version1.setDescription("Description for 1.0.0");
    versions.put("1.0.0", version1);

    FhirPackageVersionInfo version2 = new FhirPackageVersionInfo();
    version2.setDescription("Description for 2.0.0");
    versions.put("2.0.0", version2);

    FhirPackageVersionInfo version3 = new FhirPackageVersionInfo();
    version3.setDescription("Description for 1.5.0");
    versions.put("1.5.0", version3);

    FhirPackageInfo fhirPackageInfo = new FhirPackageInfo();
    fhirPackageInfo.setVersions(versions);

    String latestDescription = fhirPackageInfo.getLatestDescription();

    assertNotNull(latestDescription);
    assertEquals("Description for 2.0.0", latestDescription);
  }

  @Test
  void testGetLatestDescription_withoutVersions() {
    FhirPackageInfo fhirPackageInfo = new FhirPackageInfo();
    fhirPackageInfo.setVersions(null);

    String latestDescription = fhirPackageInfo.getLatestDescription();

    assertNull(latestDescription);
  }

  @Test
  void testGetLatestDescription_withEmptyVersions() {
    FhirPackageInfo fhirPackageInfo = new FhirPackageInfo();
    fhirPackageInfo.setVersions(new ConcurrentHashMap<>());

    String latestDescription = fhirPackageInfo.getLatestDescription();

    assertNull(latestDescription);
  }

  @Test
  void testGetLatestDescription_withSingleVersion() {
    ConcurrentHashMap<String, FhirPackageVersionInfo> versions = new ConcurrentHashMap<>();
    FhirPackageVersionInfo version1 = new FhirPackageVersionInfo();
    version1.setDescription("Description for 1.0.0");
    versions.put("1.0.0", version1);

    FhirPackageInfo fhirPackageInfo = new FhirPackageInfo();
    fhirPackageInfo.setVersions(versions);

    String latestDescription = fhirPackageInfo.getLatestDescription();

    assertNotNull(latestDescription);
    assertEquals("Description for 1.0.0", latestDescription);
  }

  @Test
  void testGetLatestDescription_withRemovedVersion() {
    ConcurrentHashMap<String, FhirPackageVersionInfo> versions = new ConcurrentHashMap<>();
    FhirPackageVersionInfo version1 = new FhirPackageVersionInfo();
    version1.setDescription("Description for 1.0.0");
    versions.put("1.0.0", version1);

    FhirPackageVersionInfo version2 = new FhirPackageVersionInfo();
    version2.setDescription("Description for 2.0.0");
    versions.put("2.0.0", version2);

    // Now, let's assume we remove the latest version
    versions.remove("2.0.0");

    FhirPackageInfo fhirPackageInfo = new FhirPackageInfo();
    fhirPackageInfo.setVersions(versions);

    String latestDescription = fhirPackageInfo.getLatestDescription();

    assertNotNull(latestDescription);
    assertEquals("Description for 1.0.0", latestDescription);
  }

  @Test
  void testGetLatestDescription_withVersionWithoutDescription() {
    ConcurrentHashMap<String, FhirPackageVersionInfo> versions = new ConcurrentHashMap<>();

    // Version with a description
    FhirPackageVersionInfo version1 = new FhirPackageVersionInfo();
    version1.setDescription("Description for 1.0.0");
    versions.put("1.0.0", version1);

    // Latest version without a description
    FhirPackageVersionInfo version2 = new FhirPackageVersionInfo();
    version2.setDescription(null); // No description
    versions.put("2.0.0", version2);

    FhirPackageVersionInfo version3 = new FhirPackageVersionInfo();
    version3.setDescription("Description for 1.5.0");
    versions.put("1.5.0", version3);

    FhirPackageInfo fhirPackageInfo = new FhirPackageInfo();
    fhirPackageInfo.setVersions(versions);

    String latestDescription = fhirPackageInfo.getLatestDescription();

    // The latest version (2.0.0) has no description, so it should return null
    assertNull(latestDescription);
  }
}
