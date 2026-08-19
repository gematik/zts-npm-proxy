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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.zts.npmproxy.model.FhirPackageManifest;
import de.gematik.zts.npmproxy.model.NpmProxyLuceneDocument;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.index.IndexableField;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FieldGenerator} */
class FieldGeneratorTest {

  /** Helper method to get all string values from the document for a particular field name. */
  private static List<String> getStringFieldValues(
      NpmProxyLuceneDocument document, String fieldName) {
    return Arrays.stream(document.getFields(fieldName + SEPARATOR + SUFFIX_STRING))
        .map(IndexableField::stringValue)
        .toList();
  }

  @Test
  void testCreateFieldsFromResource_withCodeSystemUrl() {
    // Arrange
    NpmProxyLuceneDocument document = new NpmProxyLuceneDocument();
    CodeSystem codeSystem = new CodeSystem();
    codeSystem.setUrl("http://example.org/CodeSystem/test");
    codeSystem.setVersion("1.0.0");

    // Act
    FieldGenerator.createFieldsFromResource(document, codeSystem);

    // Assert
    List<String> canonicalValues = getStringFieldValues(document, CANONICAL);
    List<String> versionValues = getStringFieldValues(document, CANONICAL_VERSION);

    // The lower-cased canonical is stored.
    // Also, each segment of the URL is stored separately.
    assertThat(canonicalValues)
        .contains("http://example.org/codesystem/test")
        .contains("http:")
        .contains("example.org")
        .contains("codesystem")
        .contains("test");

    // The version field should match what's in the resource.
    assertThat(versionValues).contains("1.0.0");
  }

  @Test
  void testCreateFieldsFromResource_withCodeSystemOid() {
    // Arrange
    NpmProxyLuceneDocument document = new NpmProxyLuceneDocument();
    CodeSystem codeSystem = new CodeSystem();
    codeSystem.setUrl("urn:oid:1.2.3.4.5");

    // Act
    FieldGenerator.createFieldsFromResource(document, codeSystem);

    // Assert
    List<String> canonicalValues = getStringFieldValues(document, CANONICAL);

    // Entire OID in lower case:
    assertThat(canonicalValues).contains("urn:oid:1.2.3.4.5").contains("1", "2", "3", "4", "5");
  }

  @Test
  void testCreateFieldsFromResource_withAltCanonical() {
    // Arrange
    NpmProxyLuceneDocument document = new NpmProxyLuceneDocument();
    CodeSystem codeSystem = new CodeSystem();
    codeSystem.setUrl("http://example.org/CodeSystem/main");
    // This identifier system is recognized in getAlternativeCanonicals
    Identifier altIdentifier =
        new Identifier()
            .setSystem("urn:ietf:rfc:3986")
            .setValue("http://example.org/CodeSystem/alt");
    codeSystem.addIdentifier(altIdentifier);

    // Act
    FieldGenerator.createFieldsFromResource(document, codeSystem);

    // Assert
    List<String> canonicalValues = getStringFieldValues(document, CANONICAL);

    // "main" canonical
    assertThat(canonicalValues)
        .contains("http://example.org/codesystem/main")
        .contains("main")
        // "alt" canonical
        .contains("http://example.org/codesystem/alt")
        .contains("alt");
  }

  @Test
  void testCreateFieldsFromResource_withValueSet() {
    // Arrange
    NpmProxyLuceneDocument document = new NpmProxyLuceneDocument();
    ValueSet valueSet = new ValueSet();
    valueSet.setUrl("http://example.org/ValueSet/test");

    Identifier altIdentifier =
        new Identifier().setSystem("urn:ietf:rfc:3986").setValue("http://example.org/ValueSet/alt");
    valueSet.addIdentifier(altIdentifier);

    // Act
    FieldGenerator.createFieldsFromResource(document, valueSet);

    // Assert
    List<String> canonicalValues = getStringFieldValues(document, CANONICAL);
    assertThat(canonicalValues)
        // "main" canonical
        .contains("http://example.org/valueset/test")
        .contains("test")
        // "alt" canonical
        .contains("http://example.org/valueset/alt")
        .contains("alt");
  }

  @Test
  void testCreateFieldsFromResource_withConceptMap() {
    // Arrange
    NpmProxyLuceneDocument document = new NpmProxyLuceneDocument();
    ConceptMap conceptMap = new ConceptMap();
    conceptMap.setUrl("http://example.org/ConceptMap/test");
    // ConceptMap uses the single .getIdentifier() approach in getAlternativeCanonicals
    Identifier altIdentifier =
        new Identifier()
            .setSystem("urn:ietf:rfc:3986")
            .setValue("http://example.org/ConceptMap/alt");
    conceptMap.setIdentifier(altIdentifier);

    // Act
    FieldGenerator.createFieldsFromResource(document, conceptMap);

    // Assert
    List<String> canonicalValues = getStringFieldValues(document, CANONICAL);

    assertThat(canonicalValues)
        // "test" canonical
        .contains("http://example.org/conceptmap/test")
        // "alt" canonical
        .contains("http://example.org/conceptmap/alt");
  }

  @Test
  void testCreateFieldsFromResource_notMetadataResource() {
    // Arrange
    NpmProxyLuceneDocument document = new NpmProxyLuceneDocument();
    Resource nonMetadataResource =
        new Resource() {
          @Override
          public Resource copy() {
            return null;
          }

          @Override
          public ResourceType getResourceType() {
            // Some resource type that isn’t a MetadataResource, e.g. Patient
            return ResourceType.Patient;
          }
        };

    // Act
    assertThatThrownBy(
            () -> FieldGenerator.createFieldsFromResource(document, nonMetadataResource),
            "Should throw an exception for non-MetadataResource")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Resource is not a MetadataResource");

    // Assert
    // Shouldn't add anything for a non-MetadataResource
    assertThat(getStringFieldValues(document, CANONICAL)).isEmpty();
    assertThat(getStringFieldValues(document, CANONICAL_VERSION)).isEmpty();
  }

  @Test
  void testCreateFieldsFromResource_withNotSupportedMetadataResource() {
    // Arrange
    NpmProxyLuceneDocument document = new NpmProxyLuceneDocument();
    // create a MetadataResource that is not supported
    NamingSystem metadataResource = new NamingSystem();

    // Act
    assertThatThrownBy(
            () -> FieldGenerator.createFieldsFromResource(document, metadataResource),
            "Should throw an exception for MetadataResource, which is not supported")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            String.format(
                "resource type %s is not supported for field generation",
                metadataResource.getResourceType()));

    // Assert
    // Shouldn't add anything for a non-MetadataResource
    assertThat(getStringFieldValues(document, CANONICAL)).isEmpty();
    assertThat(getStringFieldValues(document, CANONICAL_VERSION)).isEmpty();
  }

  @Test
  void testCreateFieldsFromPackageManifest() {
    // Arrange
    NpmProxyLuceneDocument document = new NpmProxyLuceneDocument();
    FhirPackageManifest dummyManifest = new FhirPackageManifest();
    // Populate your manifest as needed

    // Act
    FieldGenerator.createFieldsFromPackageManifest(document, dummyManifest);

    // Assert
    // Just check if fields were created. There are more tests for specific fields in
    // PackageManifestFieldGeneratorTest
    assertThat(document.getDocument().getFields()).isNotEmpty();
  }
}
