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

import static de.gematik.zts.npmproxy.repository.lucene.fields.BaseFieldNames.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexableField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link NpmProxyLuceneDocument} */
class NpmProxyLuceneDocumentTest {

  private NpmProxyLuceneDocument document;

  @BeforeEach
  void setUp() {
    document = new NpmProxyLuceneDocument();
  }

  /** Helper method to create a valid Document containing all mandatory fields. */
  private Document createValidDocument() {
    Document validDoc = new Document();
    validDoc.add(
        new StringField(
            DOCUMENT_ID + SEPARATOR + SUFFIX_STRING,
            "docIdValue",
            org.apache.lucene.document.Field.Store.YES));
    validDoc.add(
        new StringField(
            PACKAGE_NAME + SEPARATOR + SUFFIX_STRING,
            "packageNameValue",
            org.apache.lucene.document.Field.Store.YES));
    validDoc.add(
        new StringField(
            PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING,
            "1.0.0",
            org.apache.lucene.document.Field.Store.YES));
    validDoc.add(
        new StringField(
            PACKAGE_ISPRERELEASE + SEPARATOR + SUFFIX_STRING,
            "false",
            org.apache.lucene.document.Field.Store.YES));
    validDoc.add(
        new StringField(
            PACKAGE_FHIRVERSION + SEPARATOR + SUFFIX_STRING,
            "4.0.1",
            org.apache.lucene.document.Field.Store.YES));
    validDoc.add(
        new StringField(
            CANONICAL + SEPARATOR + SUFFIX_STRING,
            "canonicalValue",
            org.apache.lucene.document.Field.Store.YES));
    return validDoc;
  }

  @Test
  void testAddStoredFieldToDocument() {
    // Arrange
    String resourceName = "testResource";
    byte[] resourceContent = new byte[] {1, 2, 3, 4};

    // Act
    document.addStoredFieldToDocument(resourceName, resourceContent);

    // Assert
    String expectedFieldName = resourceName + SEPARATOR + SUFFIX_CONTENT;
    IndexableField field = document.getField(expectedFieldName);

    assertThat(field).as("Stored field should exist").isNotNull().isInstanceOf(StoredField.class);
    assertThat(field.binaryValue().bytes).containsExactly(resourceContent);
  }

  @Test
  void testAddStringFieldToDocument_Stored() {
    // Arrange
    String paramName = "testParam";
    String paramValue = "testValue";

    // Act
    document.addStringFieldToDocument(
        paramName, paramValue, org.apache.lucene.document.Field.Store.YES);

    // Assert
    String expectedFieldName = paramName + SEPARATOR + SUFFIX_STRING;
    IndexableField field = document.getField(expectedFieldName);

    assertThat(field).as("String field should exist").isNotNull().isInstanceOf(StringField.class);
    assertThat(field.stringValue()).isEqualTo(paramValue);
    assertThat(field.fieldType().stored()).isTrue();
  }

  @Test
  void testAddStringFieldToDocument_NotStored() {
    // Arrange
    String paramName = "testParam";
    String paramValue = "testValue";

    // Act
    document.addStringFieldToDocument(
        paramName, paramValue, org.apache.lucene.document.Field.Store.NO);

    // Assert
    String expectedFieldName = paramName + SEPARATOR + SUFFIX_STRING;
    IndexableField field = document.getField(expectedFieldName);

    assertThat(field).as("String field should exist").isNotNull().isInstanceOf(StringField.class);
    assertThat(field.stringValue()).isEqualTo(paramValue);
    assertThat(field.fieldType().stored()).isFalse();
  }

  @Test
  void testAddStringFieldToDocument_paramValueNull_throwsException() {
    // Arrange
    String paramName = "testParam";
    String paramValue = null;

    assertThatThrownBy(
            () ->
                document.addStringFieldToDocument(
                    paramName, paramValue, org.apache.lucene.document.Field.Store.YES))
        .as("Method should throw NullPointerException if paramValue is null")
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("paramValue is marked non-null but is null");
  }

  @Test
  void testAddSortStringFieldToDocument() {
    // Arrange
    String paramName = "testParam";
    String paramValue = "testValue";

    // Act
    document.addSortStringFieldToDocument(paramName, paramValue);

    // Assert
    String expectedFieldName = paramName + SEPARATOR + SUFFIX_STRING_SORT;
    IndexableField field = document.getField(expectedFieldName);

    assertThat(field)
        .as("SortedDocValuesField should exist")
        .isNotNull()
        .isInstanceOf(SortedDocValuesField.class);
    assertThat(field.binaryValue()).isNotNull();
    assertThat(field.binaryValue().utf8ToString()).isEqualTo(paramValue);
  }

  @Test
  void testAddSortStringFieldToDocument_paramValueNullDoesNotAddField() {
    // Arrange
    String paramName = "testParam";
    String paramValue = null;

    // Act
    document.addSortStringFieldToDocument(paramName, paramValue);

    // Assert
    String expectedFieldName = paramName + SEPARATOR + SUFFIX_STRING_SORT;
    assertThat(document.getField(expectedFieldName))
        .as("Field should not be added if value is null")
        .isNull();
  }

  @Test
  void testConstructorWithExistingDocumentAndFhirPackageVersionInfo_allMandatoryFieldsPresent() {
    // Arrange
    Document existingDocument = createValidDocument();
    FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();

    // Act
    NpmProxyLuceneDocument npmProxyDoc = new NpmProxyLuceneDocument(existingDocument, versionInfo);

    // Assert
    // Check that mandatory fields are copied
    assertThat(npmProxyDoc.getField(DOCUMENT_ID + SEPARATOR + SUFFIX_STRING))
        .isNotNull()
        .extracting(IndexableField::stringValue)
        .isEqualTo("docIdValue");
    assertThat(npmProxyDoc.getField(PACKAGE_NAME + SEPARATOR + SUFFIX_STRING))
        .isNotNull()
        .extracting(IndexableField::stringValue)
        .isEqualTo("packageNameValue");

    // By default, if versionInfo.getDeprecated() == null => unlisted = false
    IndexableField unlistedField =
        npmProxyDoc.getField(PACKAGE_UNLISTED + SEPARATOR + SUFFIX_STRING);
    assertThat(unlistedField).isNotNull();
    assertThat(unlistedField.stringValue()).isEqualTo("false");
  }

  @Test
  void testConstructorWithExistingDocumentAndFhirPackageVersionInfo_deprecatedNotNull() {
    // Arrange
    Document existingDocument = createValidDocument();
    FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();
    versionInfo.setDeprecated("SomeReason"); // triggers unlisted=true

    // Act
    NpmProxyLuceneDocument npmProxyDoc = new NpmProxyLuceneDocument(existingDocument, versionInfo);

    // Assert
    IndexableField unlistedField =
        npmProxyDoc.getField(PACKAGE_UNLISTED + SEPARATOR + SUFFIX_STRING);
    assertThat(unlistedField).isNotNull();
    assertThat(unlistedField.stringValue()).isEqualTo("true");
  }

  @Test
  void testConstructorWithExistingDocumentAndAnnotations_allMandatoryFieldsPresent() {
    // Arrange
    Document existingDocument = createValidDocument();
    FhirPackageArtifactRegistryAnnotations annotations =
        new FhirPackageArtifactRegistryAnnotations();

    // Act
    NpmProxyLuceneDocument npmProxyDoc = new NpmProxyLuceneDocument(existingDocument, annotations);

    // Assert
    // Confirm mandatory fields copied
    assertThat(npmProxyDoc.getField(DOCUMENT_ID + SEPARATOR + SUFFIX_STRING))
        .isNotNull()
        .extracting(IndexableField::stringValue)
        .isEqualTo("docIdValue");
    // If no changes via annotations, unlisted should default to false
    assertThat(npmProxyDoc.getField(PACKAGE_UNLISTED + SEPARATOR + SUFFIX_STRING))
        .isNotNull()
        .extracting(IndexableField::stringValue)
        .isEqualTo("false");
  }

  @Test
  void testConstructorWithExistingDocumentAndAnnotations_protectedDownload() {
    // Arrange
    Document existingDocument = createValidDocument();
    FhirPackageArtifactRegistryAnnotations annotations =
        new FhirPackageArtifactRegistryAnnotations();
    annotations.setProtectedDownload(true);

    // Act
    NpmProxyLuceneDocument npmProxyDoc = new NpmProxyLuceneDocument(existingDocument, annotations);

    // Assert
    IndexableField protectedField =
        npmProxyDoc.getField(PACKAGE_PROTECTED + SEPARATOR + SUFFIX_STRING);
    assertThat(protectedField).isNotNull();
    assertThat(protectedField.stringValue()).isEqualTo("true");
  }

  @Test
  void testConstructorWithExistingDocumentAndAnnotations_deprecated() {
    // Arrange
    Document existingDocument = createValidDocument();
    FhirPackageArtifactRegistryAnnotations annotations =
        new FhirPackageArtifactRegistryAnnotations();
    annotations.setStatus(FhirPackageArtifactRegistryAnnotations.Status.DEPRECATED);

    // Act
    NpmProxyLuceneDocument npmProxyDoc = new NpmProxyLuceneDocument(existingDocument, annotations);

    // Assert
    IndexableField unlistedField =
        npmProxyDoc.getField(PACKAGE_UNLISTED + SEPARATOR + SUFFIX_STRING);
    assertThat(unlistedField).isNotNull();
    assertThat(unlistedField.stringValue()).isEqualTo("true");
  }

  @Test
  void testConstructorWithExistingDocumentAndAnnotations_notDeprecated() {
    // Arrange
    Document existingDocument = createValidDocument();
    FhirPackageArtifactRegistryAnnotations annotations =
        new FhirPackageArtifactRegistryAnnotations();
    annotations.setStatus(FhirPackageArtifactRegistryAnnotations.Status.ACTIVE);

    // Act
    NpmProxyLuceneDocument npmProxyDoc = new NpmProxyLuceneDocument(existingDocument, annotations);

    // Assert
    IndexableField unlistedField =
        npmProxyDoc.getField(PACKAGE_UNLISTED + SEPARATOR + SUFFIX_STRING);
    assertThat(unlistedField).isNotNull();
    assertThat(unlistedField.stringValue()).isEqualTo("false");
  }

  @Test
  void testConstructorWithExistingDocumentAndAnnotations_additionalKeywords() {
    // Arrange
    Document existingDocument = createValidDocument();
    FhirPackageArtifactRegistryAnnotations annotations =
        new FhirPackageArtifactRegistryAnnotations();
    // Provide some keywords
    annotations.setAdditionalKeywords(List.of("KeywordOne", "KeyWordTwo"));

    // Act
    NpmProxyLuceneDocument npmProxyDoc = new NpmProxyLuceneDocument(existingDocument, annotations);

    // Assert
    // The additional keywords should be added in lowercase
    IndexableField[] keywordFields =
        npmProxyDoc.getFields(PACKAGE_ADDITIONAL_KEYWORDS + SEPARATOR + SUFFIX_STRING);
    assertThat(keywordFields).hasSize(2);
    assertThat(keywordFields[0].stringValue()).isEqualTo("keywordone");
    assertThat(keywordFields[1].stringValue()).isEqualTo("keywordtwo");
  }

  @Test
  void testConstructorWithExistingDocumentAndAnnotations_additionalKeywordsNull() {
    // Arrange
    Document existingDocument = createValidDocument();
    FhirPackageArtifactRegistryAnnotations annotations =
        new FhirPackageArtifactRegistryAnnotations();
    // additionalKeywords is null => should not add anything

    // Act
    NpmProxyLuceneDocument npmProxyDoc = new NpmProxyLuceneDocument(existingDocument, annotations);

    // Assert
    assertThat(npmProxyDoc.getFields(PACKAGE_ADDITIONAL_KEYWORDS + SEPARATOR + SUFFIX_STRING))
        .as("No additional keywords should be added if annotation's list is null or empty")
        .isEmpty();
  }

  @Test
  void testConstructorWithExistingDocument_optionalFieldsCopied() {
    // Arrange
    Document existingDocument = createValidDocument();
    // Insert optional fields
    existingDocument.add(
        new StringField(
            PACKAGE_KEYWORDS + SEPARATOR + SUFFIX_STRING,
            "keywordValue",
            org.apache.lucene.document.Field.Store.YES));
    existingDocument.add(
        new StringField(
            PACKAGE_CANONICAL + SEPARATOR + SUFFIX_STRING,
            "packageCanonicalValue",
            org.apache.lucene.document.Field.Store.YES));
    existingDocument.add(
        new StringField(
            CANONICAL_VERSION + SEPARATOR + SUFFIX_STRING,
            "canonicalVerValue",
            org.apache.lucene.document.Field.Store.YES));

    FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();

    // Act
    NpmProxyLuceneDocument npmProxyDoc = new NpmProxyLuceneDocument(existingDocument, versionInfo);

    // Assert
    // Check the optional fields were copied
    IndexableField keywordField =
        npmProxyDoc.getField(PACKAGE_KEYWORDS + SEPARATOR + SUFFIX_STRING);
    IndexableField packageCanonicalField =
        npmProxyDoc.getField(PACKAGE_CANONICAL + SEPARATOR + SUFFIX_STRING);
    IndexableField canonicalVerField =
        npmProxyDoc.getField(CANONICAL_VERSION + SEPARATOR + SUFFIX_STRING);

    assertThat(keywordField).isNotNull();
    assertThat(keywordField.stringValue()).isEqualTo("keywordValue");

    assertThat(packageCanonicalField).isNotNull();
    assertThat(packageCanonicalField.stringValue()).isEqualTo("packageCanonicalValue");

    assertThat(canonicalVerField).isNotNull();
    assertThat(canonicalVerField.stringValue()).isEqualTo("canonicalVerValue");
  }

  @Test
  void testConstructorWithExistingDocument_andMissingMandatoryField_throwsException() {
    // Arrange
    Document existingDocument = new Document();
    // Intentionally omit one mandatory field, e.g. DOCUMENT_ID, to provoke an exception
    existingDocument.add(
        new StringField(
            PACKAGE_NAME + SEPARATOR + SUFFIX_STRING,
            "packageNameValue",
            org.apache.lucene.document.Field.Store.YES));
    existingDocument.add(
        new StringField(
            PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING,
            "1.0.0",
            org.apache.lucene.document.Field.Store.YES));
    existingDocument.add(
        new StringField(
            PACKAGE_ISPRERELEASE + SEPARATOR + SUFFIX_STRING,
            "false",
            org.apache.lucene.document.Field.Store.YES));
    existingDocument.add(
        new StringField(
            PACKAGE_FHIRVERSION + SEPARATOR + SUFFIX_STRING,
            "4.0.1",
            org.apache.lucene.document.Field.Store.YES));
    existingDocument.add(
        new StringField(
            CANONICAL + SEPARATOR + SUFFIX_STRING,
            "canonicalValue",
            org.apache.lucene.document.Field.Store.YES));

    FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();

    // Act & Assert
    assertThatThrownBy(() -> new NpmProxyLuceneDocument(existingDocument, versionInfo))
        .as("Constructor should throw IllegalArgumentException if a mandatory field is missing")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(DOCUMENT_ID + " must not be null or empty");
  }

  @Test
  void testConstructorWithNullExistingDocument_throwsException() {
    var fhirPackageVersionInfo = new FhirPackageVersionInfo();
    // Act & Assert
    assertThatThrownBy(() -> new NpmProxyLuceneDocument(null, fhirPackageVersionInfo))
        .as("Constructor should throw NullPointerException if existingDocument is null")
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("existingDocument is marked non-null but is null");
  }

  @Test
  void testConstructorWithNullFhirPackageVersionInfo_throwsException() {
    FhirPackageVersionInfo fhirPackageVersionInfo = null;
    Document existingDocument = createValidDocument();
    // Act & Assert
    assertThatThrownBy(() -> new NpmProxyLuceneDocument(existingDocument, fhirPackageVersionInfo))
        .as("Constructor should throw NullPointerException if fhirPackageVersionInfo is null")
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fhirPackageVersionInfo is marked non-null but is null");
  }

  @Test
  void testConstructorWithNullFhirPackageArtifactRegistryAnnotations_throwsException() {
    FhirPackageArtifactRegistryAnnotations fhirPackageArtifactRegistryAnnotations = null;
    Document existingDocument = createValidDocument();
    // Act & Assert
    assertThatThrownBy(
            () ->
                new NpmProxyLuceneDocument(
                    existingDocument, fhirPackageArtifactRegistryAnnotations))
        .as(
            "Constructor should throw NullPointerException if fhirPackageArtifactRegistryAnnotations is null")
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("annotations is marked non-null but is null");
  }
}
