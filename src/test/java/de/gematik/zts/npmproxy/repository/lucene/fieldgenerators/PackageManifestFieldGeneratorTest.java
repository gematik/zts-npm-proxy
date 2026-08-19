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

package de.gematik.zts.npmproxy.repository.lucene.fieldgenerators;

import static de.gematik.zts.npmproxy.repository.lucene.fields.BaseFieldNames.*;
import static org.junit.jupiter.api.Assertions.*;

import de.gematik.zts.npmproxy.exceptions.PackageIndexException;
import de.gematik.zts.npmproxy.model.FhirPackageManifest;
import de.gematik.zts.npmproxy.model.NpmProxyLuceneDocument;
import java.util.Arrays;
import java.util.Collections;
import org.apache.lucene.index.IndexableField;
import org.junit.jupiter.api.Test;

class PackageManifestFieldGeneratorTest {

  @Test
  void testCreateFieldsWithValidManifest() {
    // Arrange
    NpmProxyLuceneDocument document = new NpmProxyLuceneDocument();
    FhirPackageManifest manifest = new FhirPackageManifest();
    manifest.setName("test.package");
    manifest.setVersion("1.0.0");
    manifest.setCanonical("http://example.org/fhir/StructureDefinition/test");
    manifest.setFhirVersions(Collections.singletonList("4.0.1"));
    manifest.setKeywords(Arrays.asList("keyword1", "keyword2"));

    // Act
    PackageManifestFieldGenerator.createFields(document, manifest);

    // Assert
    // Feldnamen gemäß der Konvention konstruieren
    String contentTypeFieldName = CONTENT_TYPE + SEPARATOR + SUFFIX_STRING;
    String packageNameFieldName = PACKAGE_NAME + SEPARATOR + SUFFIX_STRING;
    String packageNameSortFieldName = PACKAGE_NAME + SEPARATOR + SUFFIX_STRING_SORT;
    String packageVersionFieldName = PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING;
    String isPrereleaseFieldName = PACKAGE_ISPRERELEASE + SEPARATOR + SUFFIX_STRING;
    String canonicalFieldName = CANONICAL + SEPARATOR + SUFFIX_STRING;
    String packageCanonicalFieldName = PACKAGE_CANONICAL + SEPARATOR + SUFFIX_STRING;
    String fhirVersionFieldName = PACKAGE_FHIRVERSION + SEPARATOR + SUFFIX_STRING;
    String keywordsFieldName = PACKAGE_KEYWORDS + SEPARATOR + SUFFIX_STRING;

    // Überprüfen des CONTENT_TYPE-Feldes
    IndexableField contentTypeField = document.getField(contentTypeFieldName);
    assertNotNull(contentTypeField);
    assertEquals(CONTENT_TYPE_PACKAGE, contentTypeField.stringValue());

    // Überprüfen der PACKAGE_NAME-Felder
    IndexableField[] packageNameFields = document.getFields(packageNameFieldName);
    assertNotNull(packageNameFields);
    // Es sollten mehrere Felder vorhanden sein (gespeichertes Feld und Teile des Namens)
    assertTrue(packageNameFields.length >= 1);
    // Überprüfen, ob das gespeicherte vollständige Paketnamenfeld vorhanden ist
    boolean foundStoredFullNameField = false;
    for (IndexableField field : packageNameFields) {
      if (field.stringValue().equals("test.package") && field.fieldType().stored()) {
        foundStoredFullNameField = true;
        break;
      }
    }
    assertTrue(
        foundStoredFullNameField, "Gespeichertes vollständiges Paketnamenfeld nicht gefunden");

    // Überprüfen des Sortierfeldes für PACKAGE_NAME
    IndexableField packageNameSortField = document.getField(packageNameSortFieldName);
    assertNotNull(packageNameSortField);
    assertEquals("test.package", packageNameSortField.binaryValue().utf8ToString());

    // Überprüfen des PACKAGE_VERSION-Feldes
    IndexableField packageVersionField = document.getField(packageVersionFieldName);
    assertNotNull(packageVersionField);
    assertEquals("1.0.0", packageVersionField.stringValue());
    assertTrue(packageVersionField.fieldType().stored());

    // Überprüfen des PACKAGE_ISPRERELEASE-Feldes
    IndexableField isPrereleaseField = document.getField(isPrereleaseFieldName);
    assertNotNull(isPrereleaseField);
    assertEquals("false", isPrereleaseField.stringValue());

    // Überprüfen der CANONICAL-Felder
    IndexableField[] canonicalFields = document.getFields(canonicalFieldName);
    assertNotNull(canonicalFields);
    assertTrue(canonicalFields.length >= 1);
    boolean foundCanonical = false;
    for (IndexableField field : canonicalFields) {
      if (field.stringValue().equals("http://example.org/fhir/structuredefinition/test")) {
        foundCanonical = true;
        break;
      }
    }
    assertTrue(foundCanonical, "Canonical-Feld nicht gefunden");

    // Überprüfen des PACKAGE_CANONICAL-Feldes
    IndexableField packageCanonicalField = document.getField(packageCanonicalFieldName);
    assertNotNull(packageCanonicalField);
    assertEquals(
        "http://example.org/fhir/StructureDefinition/test", packageCanonicalField.stringValue());

    // Überprüfen des PACKAGE_FHIRVERSION-Feldes
    IndexableField fhirVersionField = document.getField(fhirVersionFieldName);
    assertNotNull(fhirVersionField);
    assertEquals("R4", fhirVersionField.stringValue());

    // Überprüfen der PACKAGE_KEYWORDS-Felder
    IndexableField[] keywordFields = document.getFields(keywordsFieldName);
    assertNotNull(keywordFields);
    assertEquals(2, keywordFields.length);
    assertTrue(Arrays.asList("keyword1", "keyword2").contains(keywordFields[0].stringValue()));
    assertTrue(Arrays.asList("keyword1", "keyword2").contains(keywordFields[1].stringValue()));
  }

  @Test
  void testCreateFieldsWithPrereleaseVersion() {
    // Arrange
    NpmProxyLuceneDocument document = new NpmProxyLuceneDocument();
    FhirPackageManifest manifest = new FhirPackageManifest();
    manifest.setName("test.package");
    manifest.setVersion("1.0.0-beta");
    manifest.setCanonical("http://example.org/fhir/StructureDefinition/test");
    manifest.setFhirVersions(Collections.singletonList("4.0.1"));
    manifest.setKeywords(Collections.singletonList("keyword"));

    // Act
    PackageManifestFieldGenerator.createFields(document, manifest);

    // Assert
    String isPrereleaseFieldName = PACKAGE_ISPRERELEASE + SEPARATOR + SUFFIX_STRING;
    IndexableField isPrereleaseField = document.getField(isPrereleaseFieldName);
    assertNotNull(isPrereleaseField);
    assertEquals("true", isPrereleaseField.stringValue());
  }

  @Test
  void testCreateFieldsWithUnsupportedFhirVersion() {
    // Arrange
    NpmProxyLuceneDocument document = new NpmProxyLuceneDocument();
    FhirPackageManifest manifest = new FhirPackageManifest();
    manifest.setName("test.package");
    manifest.setVersion("1.0.0");
    manifest.setCanonical("http://example.org/fhir/StructureDefinition/test");
    manifest.setFhirVersions(Collections.singletonList("3.0.1"));

    // Act & Assert
    Exception exception =
        assertThrows(
            PackageIndexException.class,
            () -> PackageManifestFieldGenerator.createFields(document, manifest));

    String expectedMessage = "Unsupported FHIR-Version: 3.0.1";
    String actualMessage = exception.getMessage();

    assertEquals(expectedMessage, actualMessage);
  }

  @Test
  void testCreateFieldsWithEmptyValues() {
    // Arrange
    NpmProxyLuceneDocument document = new NpmProxyLuceneDocument();
    FhirPackageManifest manifest = new FhirPackageManifest();
    manifest.setName("");
    manifest.setVersion("");
    manifest.setCanonical("");
    manifest.setFhirVersions(Collections.emptyList());
    manifest.setKeywords(Collections.emptyList());

    // Act
    PackageManifestFieldGenerator.createFields(document, manifest);

    // Assert
    String contentTypeFieldName = CONTENT_TYPE + SEPARATOR + SUFFIX_STRING;

    // Es sollten keine Felder außer CONTENT_TYPE hinzugefügt worden sein
    assertNull(document.getField(PACKAGE_NAME + SEPARATOR + SUFFIX_STRING));
    assertNull(document.getField(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING));
    assertNull(document.getField(CANONICAL + SEPARATOR + SUFFIX_STRING));
    assertNull(document.getField(PACKAGE_CANONICAL + SEPARATOR + SUFFIX_STRING));
    assertNull(document.getField(PACKAGE_FHIRVERSION + SEPARATOR + SUFFIX_STRING));
    assertNull(document.getField(PACKAGE_KEYWORDS + SEPARATOR + SUFFIX_STRING));
    assertNull(document.getField(PACKAGE_ISPRERELEASE + SEPARATOR + SUFFIX_STRING));

    // Das CONTENT_TYPE-Feld sollte vorhanden sein
    IndexableField contentTypeField = document.getField(contentTypeFieldName);
    assertNotNull(contentTypeField);
    assertEquals(CONTENT_TYPE_PACKAGE, contentTypeField.stringValue());
  }

  @Test
  void testCreateFieldsWithNullValues() {
    // Arrange
    NpmProxyLuceneDocument document = new NpmProxyLuceneDocument();
    FhirPackageManifest manifest = new FhirPackageManifest();
    manifest.setName(null);
    manifest.setVersion(null);
    manifest.setCanonical(null);
    manifest.setFhirVersions(null);
    manifest.setKeywords(null);

    // Act
    PackageManifestFieldGenerator.createFields(document, manifest);

    // Assert
    String contentTypeFieldName = CONTENT_TYPE + SEPARATOR + SUFFIX_STRING;

    // Es sollten keine Felder außer CONTENT_TYPE hinzugefügt worden sein
    assertNull(document.getField(PACKAGE_NAME + SEPARATOR + SUFFIX_STRING));
    assertNull(document.getField(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING));
    assertNull(document.getField(CANONICAL + SEPARATOR + SUFFIX_STRING));
    assertNull(document.getField(PACKAGE_CANONICAL + SEPARATOR + SUFFIX_STRING));
    assertNull(document.getField(PACKAGE_FHIRVERSION + SEPARATOR + SUFFIX_STRING));
    assertNull(document.getField(PACKAGE_KEYWORDS + SEPARATOR + SUFFIX_STRING));
    assertNull(document.getField(PACKAGE_ISPRERELEASE + SEPARATOR + SUFFIX_STRING));

    // Das CONTENT_TYPE-Feld sollte vorhanden sein
    IndexableField contentTypeField = document.getField(contentTypeFieldName);
    assertNotNull(contentTypeField);
    assertEquals(CONTENT_TYPE_PACKAGE, contentTypeField.stringValue());
  }
}
