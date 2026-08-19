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

package de.gematik.zts.npmproxy.repository.lucene;

import static de.gematik.zts.npmproxy.repository.lucene.fields.BaseFieldNames.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.zts.npmproxy.exceptions.PackageIndexException;
import de.gematik.zts.npmproxy.model.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PackageIndexWriter} */
class PackageIndexWriterTest {

  private Directory directory;

  @BeforeEach
  void setUp() {
    // Use ByteBuffersDirectory for in-memory testing
    directory = new ByteBuffersDirectory();
  }

  @AfterEach
  void tearDown() throws IOException {
    directory.close();
  }

  @Test
  void testConstructor_successfulInitialization() {
    // No exceptions should be thrown
    try (PackageIndexWriter writer =
        new PackageIndexWriter(directory, IndexWriterConfig.OpenMode.CREATE)) {
      assertThat(writer).isNotNull();
    }
  }

  @Test
  void testConstructor_throwsPackageIndexException() throws IOException {
    // We create a Directory mock that simulates an IOException
    Directory mockDirectory = mock(Directory.class);
    // Make listAll() throw an IOException to simulate a failure
    when(mockDirectory.listAll()).thenThrow(new IOException("Simulated IO error"));

    // Attempting to create the writer should throw our custom PackageIndexException
    assertThatThrownBy(
            () -> new PackageIndexWriter(mockDirectory, IndexWriterConfig.OpenMode.CREATE))
        .isInstanceOf(PackageIndexException.class)
        .hasMessageContaining("Error while initializing index writer.");
  }

  @Test
  void testUpdateDocument_noException() throws IOException {
    try (PackageIndexWriter writer =
        new PackageIndexWriter(directory, IndexWriterConfig.OpenMode.CREATE)) {

      // First, add a document to have something to update.
      NpmProxyLuceneDocument initialDoc = new NpmProxyLuceneDocument();
      initialDoc.addStringFieldToDocument(
          "documentId", "id-123", org.apache.lucene.document.Field.Store.YES);
      writer.updateDocument("id-123", initialDoc);

      // Now, update it with a second doc
      NpmProxyLuceneDocument updateDoc = new NpmProxyLuceneDocument();
      updateDoc.addStringFieldToDocument(
          "documentId", "id-123", org.apache.lucene.document.Field.Store.YES);
      updateDoc.addStringFieldToDocument(
          "anotherField", "newValue", org.apache.lucene.document.Field.Store.YES);

      writer.updateDocument("id-123", updateDoc);
    }

    // Verify that exactly 1 doc remains in the index, updated with the new fields
    try (DirectoryReader reader = DirectoryReader.open(directory)) {
      assertThat(reader.numDocs()).isEqualTo(1);
      // Check if the updated field exists and has the expected value
      Document updatedDoc = reader.storedFields().document(0);
      assertThat(updatedDoc.getFields("anotherField" + SEPARATOR + SUFFIX_STRING)).isNotEmpty();
      assertThat(updatedDoc.getField("anotherField" + SEPARATOR + SUFFIX_STRING).stringValue())
          .isEqualTo("newValue");
    }
  }

  @Test
  void testIndexPackage_addsDocumentToIndex() throws IOException {
    try (PackageIndexWriter writer =
        new PackageIndexWriter(directory, IndexWriterConfig.OpenMode.CREATE)) {

      // Create minimal FHIR Package
      FhirPackageManifest manifest = new FhirPackageManifest();
      manifest.setName("TestPackage");

      // no resources
      FhirPackage fhirPackage = new FhirPackage();
      fhirPackage.setManifest(manifest);
      fhirPackage.setResources(Collections.emptyList());

      FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();
      versionInfo.setProtectedPackage(true); // just to see that the field is set
      // versionInfo.getUnlisted() remains null => "false" in the index

      // Index the package
      writer.indexPackage(fhirPackage, versionInfo);
    }

    try (DirectoryReader reader = DirectoryReader.open(directory)) {
      // Exactly one document should have been stored
      assertThat(reader.numDocs()).isEqualTo(1);
    }
  }

  @Test
  void testIndexPackage_callsFieldGeneratorForResources() throws IOException {

    // Create a mock Resource to simulate indexing
    CodeSystem mockResource = mock(CodeSystem.class, RETURNS_DEEP_STUBS);
    when(mockResource.getResourceType()).thenReturn(org.hl7.fhir.r4.model.ResourceType.CodeSystem);

    FhirPackageManifest manifest = new FhirPackageManifest();
    manifest.setName("ResPackage");
    FhirPackage fhirPackage = new FhirPackage();
    fhirPackage.setManifest(manifest);
    fhirPackage.setResources(Collections.singletonList(mockResource));

    FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();

    try (PackageIndexWriter writer =
        new PackageIndexWriter(directory, IndexWriterConfig.OpenMode.CREATE)) {
      writer.indexPackage(fhirPackage, versionInfo);
    }

    // Check that a single doc was created
    try (DirectoryReader reader = DirectoryReader.open(directory)) {
      assertThat(reader.numDocs()).isEqualTo(1);
    }
  }

  @Test
  void testClose_noException() {
    PackageIndexWriter writer =
        new PackageIndexWriter(directory, IndexWriterConfig.OpenMode.CREATE);
    // Should not throw on close
    assertThatCode(writer::close).doesNotThrowAnyException();
  }

  @Test
  void testIndexPackage_withAnnotations_addsKeywordsToIndex() throws IOException {
    try (PackageIndexWriter writer =
        new PackageIndexWriter(directory, IndexWriterConfig.OpenMode.CREATE)) {

      // 1. Create a FhirPackage with minimal information
      FhirPackageManifest manifest = new FhirPackageManifest();
      manifest.setName("KeywordTestPackage");
      FhirPackage fhirPackage = new FhirPackage();
      fhirPackage.setManifest(manifest);

      // 2. Create a FhirPackageVersionInfo with annotations and additional keywords
      FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();
      FhirPackageArtifactRegistryAnnotations annotations =
          new FhirPackageArtifactRegistryAnnotations();
      annotations.setAdditionalKeywords(List.of("keywordOne", "KEYWORDtwo", "KEYWordThree"));
      versionInfo.setAnnotations(annotations);

      // 3. Index the package
      writer.indexPackage(fhirPackage, versionInfo);
    }

    // 4. Open the index and verify that the keywords are stored
    try (DirectoryReader reader = DirectoryReader.open(directory)) {
      assertThat(reader.numDocs()).isEqualTo(1);

      Document doc = reader.storedFields().document(0);
      String expectedFieldName = PACKAGE_ADDITIONAL_KEYWORDS + SEPARATOR + SUFFIX_STRING;

      // 5. Extract the stored fields corresponding to our keywords
      List<String> storedKeywords =
          doc.getFields().stream()
              .filter(f -> f.name().equals(expectedFieldName))
              .map(IndexableField::stringValue)
              .toList();

      // 6. Verify that all lowercase keywords are present
      assertThat(storedKeywords)
          .containsExactlyInAnyOrder("keywordone", "keywordtwo", "keywordthree");
    }
  }

  @Test
  void testIndexPackage_withoutAnnotations_doesNotAddKeywordsToIndex() throws IOException {
    try (PackageIndexWriter writer =
        new PackageIndexWriter(directory, IndexWriterConfig.OpenMode.CREATE)) {

      // 1. Create a FhirPackage with minimal information
      FhirPackageManifest manifest = new FhirPackageManifest();
      manifest.setName("NoAnnotationsPackage");
      FhirPackage fhirPackage = new FhirPackage();
      fhirPackage.setManifest(manifest);

      // 2. Create a FhirPackageVersionInfo with null annotations
      FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();
      versionInfo.setAnnotations(null);

      // 3. Index the package
      writer.indexPackage(fhirPackage, versionInfo);
    }

    // 4. Open the index and verify that no additional keywords were stored
    try (DirectoryReader reader = DirectoryReader.open(directory)) {
      assertThat(reader.numDocs()).isEqualTo(1);

      Document doc = reader.storedFields().document(0);
      String expectedFieldName = PACKAGE_ADDITIONAL_KEYWORDS + SEPARATOR + SUFFIX_STRING;

      // We expect no fields for additional keywords because annotations are null
      List<String> storedKeywords =
          doc.getFields().stream()
              .filter(f -> f.name().equals(expectedFieldName))
              .map(IndexableField::stringValue)
              .toList();

      // Should be empty
      assertThat(storedKeywords).isEmpty();
    }
  }
}
