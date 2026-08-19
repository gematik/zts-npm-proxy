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

package de.gematik.zts.npmproxy.repository;

import static de.gematik.zts.npmproxy.repository.lucene.fields.BaseFieldNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.exceptions.PackageIndexException;
import de.gematik.zts.npmproxy.model.*;
import de.gematik.zts.npmproxy.repository.lucene.PackageIndex;
import de.gematik.zts.npmproxy.repository.lucene.PackageIndexWriter;
import de.gematik.zts.npmproxy.tools.FileHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Extended tests that focus on increasing coverage of LuceneBackedPackageRepository, particularly
 * methods like reIndexPackage, updateVersionInfo, isPackageVersionProtected, etc.
 */
@Slf4j
class LuceneBackedPackageRepositoryExtendedTest {

  private LuceneBackedPackageRepository repository;
  private NpmProxyConfiguration properties;
  private Path cacheDir;

  // ---------------------------------------------------------------------------------------------
  // Helper methods
  // ---------------------------------------------------------------------------------------------
  /**
   * Helper method to set the private "indexSearcher" field on the repository instance via
   * reflection.
   */
  private void setIndexSearcherField(IndexSearcher searcher) {
    // Find the declared field

    java.lang.reflect.Field field =
        ReflectionUtils.findField(LuceneBackedPackageRepository.class, "indexSearcher");
    // Make it accessible
    ReflectionUtils.makeAccessible(field);
    // Set the field value
    ReflectionUtils.setField(field, repository, searcher);
  }

  /** Helper to generate a mock FhirPackage with a manifest returning the given name/version. */
  private FhirPackage mockFhirPackage(String name, String version) {
    FhirPackageManifest manifest = new FhirPackageManifest();
    manifest.setName(name);
    manifest.setVersion(version);
    manifest.setDescription("Mocked package");
    manifest.setFhirVersions(List.of("4.0.1"));
    FhirPackage fhirPackage = mock(FhirPackage.class);
    when(fhirPackage.getManifest()).thenReturn(manifest);
    return fhirPackage;
  }

  // ---------------------------------------------------------------------------------------------
  // Setup
  // ---------------------------------------------------------------------------------------------
  @BeforeEach
  void setUp() throws IOException {

    log.info("Creating temp directory");
    cacheDir = Files.createTempDirectory("packages");
    // Mock configuration
    properties = mock(NpmProxyConfiguration.class);
    when(properties.getPackageCacheDir()).thenReturn(cacheDir.toString());
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(false);

    // Create a real instance (spy) of the repository, but we will mock certain internals:
    repository = spy(new LuceneBackedPackageRepository(properties));

    // By default, pretend that searching for documents returns null so no real Lucene calls happen.
    doReturn(null).when(repository).getDocument(anyString(), anyString());
  }

  @AfterEach
  void tearDown() throws IOException {
    // Testverzeichnis löschen
    FileSystemUtils.deleteRecursively(cacheDir);
  }

  // ---------------------------------------------------------------------------------------------
  // reIndexPackage(...)
  // ---------------------------------------------------------------------------------------------

  @Test
  void testReIndexPackage_noDocumentFound() {
    // getDocument returns null, simulating no existing document
    doReturn(null).when(repository).getDocument("test-package", "1.0.0");

    FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();
    versionInfo.setName("test-package");
    versionInfo.setVersion("1.0.0");

    // should not create a PackageIndex if no document is found
    try (MockedConstruction<PackageIndex> packageIndexConstruction =
        mockConstruction(PackageIndex.class)) {

      // Aufruf der Methode
      repository.reIndexPackage(versionInfo, null);

      // verify that no PackageIndex was constructed
      assertThat(packageIndexConstruction.constructed()).isEmpty();
    }
  }

  @Test
  void testReIndexPackage_foundDocument_withoutAnnotations() {
    // Mock a found document
    Document doc = new Document();
    // Add the fields that NpmProxyLuceneDocument requires
    doc.add(
        new StringField(DOCUMENT_ID + SEPARATOR + SUFFIX_STRING, "docIdValue", Field.Store.YES));
    doc.add(
        new StringField(PACKAGE_NAME + SEPARATOR + SUFFIX_STRING, "test-package", Field.Store.YES));
    doc.add(new StringField(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING, "1.0.0", Field.Store.YES));
    doc.add(
        new StringField(
            PACKAGE_ISPRERELEASE + SEPARATOR + SUFFIX_STRING, "false", Field.Store.YES));
    doc.add(
        new StringField(PACKAGE_FHIRVERSION + SEPARATOR + SUFFIX_STRING, "R4", Field.Store.YES));
    doc.add(
        new StringField(
            CANONICAL + SEPARATOR + SUFFIX_STRING, "http://some/canonical", Field.Store.YES));
    doReturn(doc).when(repository).getDocument("test-package", "1.0.0");

    FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();
    versionInfo.setName("test-package");
    versionInfo.setVersion("1.0.0");
    versionInfo.setDescription("Test package description");

    // Create one mock for the writer outside the try
    PackageIndexWriter writerMock = mock(PackageIndexWriter.class);

    // Mock the construction of PackageIndex so we do not call real Lucene
    try (MockedConstruction<PackageIndex> packageIndexConstruction =
        mockConstruction(
            PackageIndex.class,
            (packageIndexMock, context) -> {
              // Whenever LuceneBackedPackageRepository does new PackageIndex(...),
              // we return 'packageIndexMock' with this stubbed writer.
              when(packageIndexMock.createPackageModelIndexWriter(any())).thenReturn(writerMock);

              // By default, createSearcher() could just return null or another mock
              when(packageIndexMock.createSearcher()).thenReturn(null);
            })) {

      // Trigger the code that calls 'new PackageIndex(...)'
      repository.reIndexPackage(versionInfo, null);

      // Now assert we used the writer
      verify(writerMock).updateDocument(eq("docIdValue"), any(NpmProxyLuceneDocument.class));

      // confirm exactly one PackageIndex object was constructed
      assertThat(packageIndexConstruction.constructed()).hasSize(1);
    }
  }

  @Test
  void testReIndexPackage_foundDocument_withAnnotations() {
    // Mock a found document
    Document doc = new Document();
    // Add mandatory fields the code expects:
    doc.add(
        new StringField(DOCUMENT_ID + SEPARATOR + SUFFIX_STRING, "docIdValue", Field.Store.YES));
    doc.add(
        new StringField(PACKAGE_NAME + SEPARATOR + SUFFIX_STRING, "test-package", Field.Store.YES));
    doc.add(new StringField(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING, "2.0.0", Field.Store.YES));
    doc.add(
        new StringField(
            PACKAGE_ISPRERELEASE + SEPARATOR + SUFFIX_STRING, "false", Field.Store.YES));
    doc.add(
        new StringField(PACKAGE_FHIRVERSION + SEPARATOR + SUFFIX_STRING, "R4", Field.Store.YES));
    doc.add(
        new StringField(
            CANONICAL + SEPARATOR + SUFFIX_STRING, "http://some/canonical", Field.Store.YES));
    doReturn(doc).when(repository).getDocument("test-package", "2.0.0");

    FhirPackageArtifactRegistryAnnotations annotations =
        new FhirPackageArtifactRegistryAnnotations();
    annotations.setVisibility(true);
    annotations.setProtectedDownload(false);

    FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();
    versionInfo.setName("test-package");
    versionInfo.setVersion("2.0.0");

    try (MockedConstruction<PackageIndex> ignored =
        mockConstruction(
            PackageIndex.class,
            (mock, context) -> {
              PackageIndexWriter writerMock = mock(PackageIndexWriter.class);
              when(mock.createPackageModelIndexWriter(any())).thenReturn(writerMock);
              when(mock.createSearcher()).thenReturn(null);

              repository.reIndexPackage(versionInfo, annotations);

              // Assert that updateDocument(...) was called with the correct ID
              verify(writerMock)
                  .updateDocument(eq("docIdValue"), any(NpmProxyLuceneDocument.class));
            })) {
      // No additional code needed here; the verification is inside the constructor lambda
    }
  }

  @Test
  void testGetDocument_noResults() throws Exception {
    // Let the real method be called
    doCallRealMethod().when(repository).getDocument(anyString(), anyString());

    // Mock an IndexSearcher and inject it into the repository
    IndexSearcher mockSearcher = mock(IndexSearcher.class);
    setIndexSearcherField(mockSearcher);

    // Suppose the search returns no hits
    TotalHits totalHits = new TotalHits(0, TotalHits.Relation.EQUAL_TO);
    TopDocs topDocs = new TopDocs(totalHits, new ScoreDoc[0]);
    when(mockSearcher.search(any(Query.class), eq(10))).thenReturn(topDocs);

    Document result = repository.getDocument("missing-package", "1.0.0");
    assertThat(result).isNull();
    // verify that storedFields() is not called
    verify(mockSearcher, never()).storedFields();
  }

  @Test
  void testGetDocument_oneResult() throws Exception {
    doCallRealMethod().when(repository).getDocument(anyString(), anyString());

    IndexSearcher mockSearcher = mock(IndexSearcher.class);
    setIndexSearcherField(mockSearcher);

    // Single ScoreDoc
    ScoreDoc[] scoreDocs = {new ScoreDoc(42, 2.3f)};
    TotalHits totalHits = new TotalHits(scoreDocs.length, TotalHits.Relation.EQUAL_TO);
    TopDocs topDocs = new TopDocs(totalHits, scoreDocs);
    when(mockSearcher.search(any(Query.class), eq(10))).thenReturn(topDocs);

    Document doc = new Document();
    StoredFields storedFields = mock(StoredFields.class);
    when(mockSearcher.storedFields()).thenReturn(storedFields);
    when(storedFields.document(42)).thenReturn(doc);

    Document result = repository.getDocument("some-package", "1.0.0");
    assertThat(result).isEqualTo(doc);
    verify(storedFields).document(42);
  }

  @Test
  void testGetDocument_multipleResults() throws Exception {
    doCallRealMethod().when(repository).getDocument(anyString(), anyString());

    IndexSearcher mockSearcher = mock(IndexSearcher.class);
    setIndexSearcherField(mockSearcher);

    // multiple hits
    ScoreDoc[] scoreDocs = {new ScoreDoc(10, 1.0f), new ScoreDoc(11, 1.0f)};
    TotalHits totalHits = new TotalHits(scoreDocs.length, TotalHits.Relation.EQUAL_TO);
    TopDocs topDocs = new TopDocs(totalHits, scoreDocs);
    when(mockSearcher.search(any(Query.class), eq(10))).thenReturn(topDocs);

    Document doc = new Document();
    StoredFields storedFields = mock(StoredFields.class);
    when(mockSearcher.storedFields()).thenReturn(storedFields);
    when(storedFields.document(10)).thenReturn(doc);

    Document result = repository.getDocument("some-package", "2.0.0");
    assertThat(result).isSameAs(doc);

    // only the first match is used
    verify(storedFields).document(10);
  }

  @Test
  void testGetDocument_ioException() throws Exception {
    doCallRealMethod().when(repository).getDocument(anyString(), anyString());

    IndexSearcher mockSearcher = mock(IndexSearcher.class);
    setIndexSearcherField(mockSearcher);

    // simulate an IOException
    when(mockSearcher.search(any(Query.class), eq(10))).thenThrow(new IOException("Simulated"));

    // Should throw a PackageIndexException
    assertThatThrownBy(() -> repository.getDocument("io-problem-package", "1.0.0"))
        .isInstanceOf(PackageIndexException.class);
  }

  // ---------------------------------------------------------------------------------------------
  // updateVersionInfo(FhirPackageVersionInfo remoteVersionInfo)
  // ---------------------------------------------------------------------------------------------

  @Test
  void testUpdateVersionInfo_existingVersion_changesUnlisted() {
    // Insert a local version
    var localPackageInfo = new FhirPackageInfo();
    localPackageInfo.setName("some.package");
    var versionMap = new ConcurrentHashMap<String, FhirPackageVersionInfo>();
    FhirPackageVersionInfo localVersion = new FhirPackageVersionInfo();
    localVersion.setUnlisted(null); // not deprecated
    localVersion.setName("some.package");
    localVersion.setVersion("1.0.0");
    versionMap.put("1.0.0", localVersion);
    localPackageInfo.setVersions(versionMap);

    // Put it into the repository's index maps
    repository.getPackageInfoIndex().put("some.package", localPackageInfo);

    FhirPackageVersionInfo remoteVersionInfo = new FhirPackageVersionInfo();
    remoteVersionInfo.setName("some.package");
    remoteVersionInfo.setVersion("1.0.0");
    remoteVersionInfo.setDeprecated("This version is deprecated"); // new value

    boolean updated = repository.updateVersionInfo(remoteVersionInfo);
    assertThat(updated).isTrue();
    assertThat(localVersion.getUnlisted()).isEqualTo("This version is deprecated");
  }

  @Test
  void testUpdateVersionInfo_nonExistingVersion_noUpdate() {
    FhirPackageVersionInfo remoteVersionInfo = new FhirPackageVersionInfo();
    remoteVersionInfo.setName("some.package");
    remoteVersionInfo.setVersion("1.0.0");
    remoteVersionInfo.setDeprecated("deprecated message");

    boolean updated = repository.updateVersionInfo(remoteVersionInfo);
    assertThat(updated).isFalse();
  }

  // ---------------------------------------------------------------------------------------------
  // updateVersionInfo(String packageName, String packageVersion,
  // FhirPackageArtifactRegistryAnnotations)
  // ---------------------------------------------------------------------------------------------

  @Test
  void testUpdateVersionInfo_withAnnotations_alreadySame_noChange() {
    // local version
    var localPackageInfo = new FhirPackageInfo();
    localPackageInfo.setName("some.package");
    var versionMap = new ConcurrentHashMap<String, FhirPackageVersionInfo>();
    FhirPackageVersionInfo localVersion = new FhirPackageVersionInfo();
    localVersion.setName("some.package");
    localVersion.setVersion("1.0.0");

    FhirPackageArtifactRegistryAnnotations existingAnn =
        new FhirPackageArtifactRegistryAnnotations();
    existingAnn.setVisibility(true);
    existingAnn.setProtectedDownload(false);
    localVersion.setAnnotations(existingAnn);

    versionMap.put("1.0.0", localVersion);
    localPackageInfo.setVersions(versionMap);
    repository.getPackageInfoIndex().put("some.package", localPackageInfo);

    // Attempt an update with the same annotations
    boolean updated = repository.updateVersionInfo("some.package", "1.0.0", existingAnn);
    assertThat(updated).isFalse();
  }

  @Test
  void testUpdateVersionInfo_withAnnotations_differentValues() {
    // local version
    var localPackageInfo = new FhirPackageInfo();
    localPackageInfo.setName("some.package");
    var versionMap = new ConcurrentHashMap<String, FhirPackageVersionInfo>();
    FhirPackageVersionInfo localVersion = new FhirPackageVersionInfo();
    localVersion.setName("some.package");
    localVersion.setVersion("1.0.0");

    FhirPackageArtifactRegistryAnnotations existingAnn =
        new FhirPackageArtifactRegistryAnnotations();
    existingAnn.setVisibility(true);
    existingAnn.setProtectedDownload(false);
    localVersion.setAnnotations(existingAnn);

    versionMap.put("1.0.0", localVersion);
    localPackageInfo.setVersions(versionMap);
    repository.getPackageInfoIndex().put("some.package", localPackageInfo);

    // Attempt an update with different annotation values
    FhirPackageArtifactRegistryAnnotations newAnn = new FhirPackageArtifactRegistryAnnotations();
    newAnn.setVisibility(false); // changed
    newAnn.setProtectedDownload(true); // changed

    boolean updated = repository.updateVersionInfo("some.package", "1.0.0", newAnn);
    assertThat(updated).isTrue();

    assertThat(localVersion.getAnnotations().getVisibility()).isFalse();
    assertThat(localVersion.getAnnotations().getProtectedDownload()).isTrue();
  }

  @Test
  void testUpdateVersionInfo_withAnnotations_versionNotFound() {
    boolean updated =
        repository.updateVersionInfo(
            "some.package", "1.0.0", new FhirPackageArtifactRegistryAnnotations());
    assertThat(updated).isFalse();
  }

  // ---------------------------------------------------------------------------------------------
  // isPackageVersionProtected(...)
  // ---------------------------------------------------------------------------------------------

  @Test
  void testIsPackageVersionProtected_gcloudDisabled_foundInProtectedPackages() {
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(false);
    when(properties.getProtectedPackages()).thenReturn(Set.of("some.package"));

    // no local version needed
    boolean result = repository.isPackageVersionProtected("some.package", "1.0.0");
    assertThat(result).isTrue();
  }

  @Test
  void testIsPackageVersionProtected_gcloudDisabled_notProtected() {
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(false);
    when(properties.getProtectedPackages()).thenReturn(Set.of("another.package"));

    boolean result = repository.isPackageVersionProtected("some.package", "1.0.0");
    assertThat(result).isFalse();
  }

  @Test
  void testIsPackageVersionProtected_gcloudEnabled_versionProtected() {
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(true);

    var localPackageInfo = new FhirPackageInfo();
    localPackageInfo.setName("some.package");

    var versionMap = new ConcurrentHashMap<String, FhirPackageVersionInfo>();
    FhirPackageVersionInfo localVersion = new FhirPackageVersionInfo();
    localVersion.setProtectedPackage(true); // Mark it as protected
    localVersion.setName("some.package");
    localVersion.setVersion("1.0.0");
    versionMap.put("1.0.0", localVersion);

    localPackageInfo.setVersions(versionMap);
    repository.getPackageInfoIndex().put("some.package", localPackageInfo);

    boolean result = repository.isPackageVersionProtected("some.package", "1.0.0");
    assertThat(result).isTrue();
  }

  @Test
  void testIsPackageVersionProtected_gcloudEnabled_anyVersionProtected() {
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(true);

    var localPackageInfo = new FhirPackageInfo();
    localPackageInfo.setName("some.package");
    var versionMap = new ConcurrentHashMap<String, FhirPackageVersionInfo>();

    // v1 is not protected
    FhirPackageVersionInfo localVersion1 = new FhirPackageVersionInfo();
    localVersion1.setProtectedPackage(false);
    localVersion1.setName("some.package");
    localVersion1.setVersion("1.0.0");
    versionMap.put("1.0.0", localVersion1);

    // v2 is protected
    FhirPackageVersionInfo localVersion2 = new FhirPackageVersionInfo();
    localVersion2.setProtectedPackage(true);
    localVersion2.setName("some.package");
    localVersion2.setVersion("2.0.0");
    versionMap.put("2.0.0", localVersion2);

    localPackageInfo.setVersions(versionMap);
    repository.getPackageInfoIndex().put("some.package", localPackageInfo);

    // pass null for packageVersion => check any version
    boolean result = repository.isPackageVersionProtected("some.package", null);
    assertThat(result).isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // getPackageVersionInfos(...)
  // ---------------------------------------------------------------------------------------------

  @Test
  void testGetPackageVersionInfos_basicFiltering() {
    // Create a package with two versions
    var localPackageInfo = new FhirPackageInfo();
    localPackageInfo.setName("some.package");
    var versionMap = new ConcurrentHashMap<String, FhirPackageVersionInfo>();

    // version 1
    FhirPackageVersionInfo localVersion1 = new FhirPackageVersionInfo();
    localVersion1.setName("some.package");
    localVersion1.setVersion("1.0.0");
    localVersion1.setAnnotations(new FhirPackageArtifactRegistryAnnotations());
    localVersion1.getAnnotations().setVisibility(true); // visible
    localVersion1.setProtectedPackage(false);
    localVersion1.setPublishToHl7(false);
    localVersion1.setAuthor(new FhirPackageAuthor("Foo"));
    localVersion1.setStaticKeywords(Set.of("hello", "world"));
    versionMap.put("1.0.0", localVersion1);

    // version 2
    FhirPackageVersionInfo localVersion2 = new FhirPackageVersionInfo();
    localVersion2.setName("some.package");
    localVersion2.setVersion("2.0.0");

    localVersion2.setAnnotations(new FhirPackageArtifactRegistryAnnotations());
    localVersion2.getAnnotations().setVisibility(true); // visible
    localVersion2.setProtectedPackage(true); // protected
    localVersion2.setPublishToHl7(true);
    localVersion2.setAuthor(new FhirPackageAuthor("FooBar"));
    localVersion2.setStaticKeywords(Set.of("world"));
    versionMap.put("2.0.0", localVersion2);

    localPackageInfo.setVersions(versionMap);
    repository.getPackageInfoIndex().put("some.package", localPackageInfo);

    // Another package
    var anotherPackageInfo = new FhirPackageInfo();
    anotherPackageInfo.setName("another.package");
    var versionMap2 = new ConcurrentHashMap<String, FhirPackageVersionInfo>();
    FhirPackageVersionInfo localVersion3 = new FhirPackageVersionInfo();
    localVersion3.setName("another.package");
    localVersion3.setVersion("1.0.1");
    localVersion3.setAnnotations(new FhirPackageArtifactRegistryAnnotations());
    localVersion3.getAnnotations().setVisibility(true);
    localVersion3.setProtectedPackage(false);
    localVersion3.setAuthor(new FhirPackageAuthor("Others"));
    localVersion3.setStaticKeywords(Set.of("alpha", "beta"));
    versionMap2.put("1.0.1", localVersion3);
    anotherPackageInfo.setVersions(versionMap2);
    repository.getPackageInfoIndex().put("another.package", anotherPackageInfo);

    // Invisible package --> should be skipped in results
    var invisiblePackageVersionInfo = new FhirPackageInfo();
    anotherPackageInfo.setName("invisible.package");
    var versionMap3 = new ConcurrentHashMap<String, FhirPackageVersionInfo>();
    FhirPackageVersionInfo localVersion4 = new FhirPackageVersionInfo();
    localVersion4.setName("invisible.package");
    localVersion4.setVersion("1.0.0");
    localVersion4.setAnnotations(new FhirPackageArtifactRegistryAnnotations());
    localVersion4.getAnnotations().setVisibility(false);
    localVersion4.setProtectedPackage(false);
    localVersion4.setAuthor(new FhirPackageAuthor("Invisible"));
    localVersion4.setStaticKeywords(Set.of("invisible", "package"));
    versionMap3.put("1.0.0", localVersion4);
    invisiblePackageVersionInfo.setVersions(versionMap3);
    repository.getPackageInfoIndex().put("invisible.package", invisiblePackageVersionInfo);

    // 1) Filter for publisher = "Foo"
    var result = repository.getPackageVersionInfos("Foo", null, null, null);
    // Should match localVersion1 only

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getVersion()).isEqualTo("1.0.0");

    // 2) Filter for packageName = "some.package"
    result = repository.getPackageVersionInfos(null, "some.package", null, null);
    assertThat(result).hasSize(2);

    // 3) Filter for keyword = "hello" => only version1 has this
    result = repository.getPackageVersionInfos(null, null, "hello", null);
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getVersion()).isEqualTo("1.0.0");

    // 4) Filter for publishToHl7 = true
    //   localVersion2 is publishToHl7 = true, but it is also protected.
    //   In the code, that means we skip it if "publishToHl7" is true => expect 0
    result = repository.getPackageVersionInfos(null, "some.package", null, true);
    assertThat(result).isEmpty();
  }

  @Test
  void testIndexPackageFile_returnsFalseWhenFhirPackageIsEmpty() {
    // We want FhirPackage.buildFromTarGz(...) to return Optional.empty().
    Path dummy = cacheDir.resolve("non-existent-package.tgz");

    // mock to return an empty Optional
    try (MockedStatic<FhirPackage> staticMock = mockStatic(FhirPackage.class)) {
      // Any call to FhirPackage.buildFromTarGz(...) returns Optional.empty()
      staticMock
          .when(() -> FhirPackage.buildFromTarGz(any(Path.class)))
          .thenReturn(Optional.empty());

      boolean result = repository.indexPackageFile(dummy, null, null);
      assertThat(result).isFalse();
    }

    // The repository should still have 0 packages.
    assertThat(repository.getPackageCount()).isZero();
  }

  @Test
  void testIndexPackageFile_gcloudEnabledAndAnnotationsProtectDownload() throws IOException {
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(true);
    // Create a dummy file in the cache directory
    Path dummy = cacheDir.resolve("dummy.tgz");
    Files.createFile(dummy);

    // Prepare a FhirPackage that returns a simple manifest
    FhirPackage validPackage = mockFhirPackage("some.pkg", "1.0.0");

    try (MockedStatic<FhirPackage> staticMock = mockStatic(FhirPackage.class)) {
      // Return "validPackage" whenever buildFromTarGz is called
      staticMock
          .when(() -> FhirPackage.buildFromTarGz(any(Path.class)))
          .thenReturn(Optional.of(validPackage));

      // create annotations with protectedDownload = true
      FhirPackageArtifactRegistryAnnotations annotations =
          new FhirPackageArtifactRegistryAnnotations();
      annotations.setProtectedDownload(true);

      boolean result =
          repository.indexPackageFile(cacheDir.resolve("dummy.tgz"), null, annotations);
      assertThat(result).isTrue();

      // Verify that the package was indexed correctly and retrieval from the repository works
      assertThat(repository.findPackageByName("some.pkg"))
          .isPresent()
          .get()
          .extracting(p -> p.getVersions().get("1.0.0").getProtectedPackage())
          .isEqualTo(true);
    }
  }

  @Test
  void testIndexPackageFile_setsUnlistedFromDeprecatedIfAnnotationsNull() throws IOException {
    FhirPackage validPackage = mockFhirPackage("some.pkg", "1.0.1");

    // create a dummy file in the cache directory
    Path dummy = cacheDir.resolve("dummy.tgz");
    Files.createFile(dummy);

    try (MockedStatic<FhirPackage> staticMock = mockStatic(FhirPackage.class)) {
      staticMock
          .when(() -> FhirPackage.buildFromTarGz(any(Path.class)))
          .thenReturn(Optional.of(validPackage));

      // create a remote version info with deprecated message
      FhirPackageVersionInfo remoteVersionInfo = new FhirPackageVersionInfo();
      remoteVersionInfo.setName("some.pkg");
      remoteVersionInfo.setVersion("1.0.1");
      remoteVersionInfo.setDeprecated("This version is deprecated");

      boolean result =
          repository.indexPackageFile(cacheDir.resolve("dummy.tgz"), remoteVersionInfo, null);
      assertThat(result).isTrue();

      assertThat(repository.findPackageVersion("some.pkg", "1.0.1"))
          .isPresent()
          .get()
          .extracting(FhirPackageVersionInfo::getUnlisted)
          .isEqualTo("This version is deprecated");
    }
  }

  @Test
  void testIndexPackageFile_returnsFalseWhenFileHashCalculationFails() {
    FhirPackage validPackage = mockFhirPackage("broken.pkg", "1.0.0");

    try (MockedStatic<FhirPackage> staticMock = mockStatic(FhirPackage.class);
        MockedStatic<FileHelper> fileHelperMock = mockStatic(FileHelper.class)) {

      // Make buildFromTarGz(...) return a valid package
      staticMock
          .when(() -> FhirPackage.buildFromTarGz(any(Path.class)))
          .thenReturn(Optional.of(validPackage));

      // Force FileHelper.calculateFileHash(...) to throw
      fileHelperMock
          .when(() -> FileHelper.calculateFileHash(any(Path.class)))
          .thenThrow(new IOException("Simulated broken hash calculation"));

      boolean result = repository.indexPackageFile(cacheDir.resolve("dummy.tgz"), null, null);
      assertThat(result).isFalse();

      // "broken.pkg" should not be stored
      assertThat(repository.findPackageByName("broken.pkg")).isEmpty();
    }
  }

  @Test
  void testIsPackageVersionProtected_gcloudEnabled_packageNotFound() {
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(true);

    // No package with this name in packageInfoIndex
    boolean result = repository.isPackageVersionProtected("unknown.package", "1.0.0");
    assertThat(result).isFalse(); // covers the "packageInfo == null" branch
  }

  @Test
  void testIsPackageVersionProtected_gcloudEnabled_noVersionSpecified_noneProtected() {
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(true);

    // Create a package with two unprotected versions
    FhirPackageInfo packageInfo = new FhirPackageInfo();
    packageInfo.setName("test.package");
    ConcurrentHashMap<String, FhirPackageVersionInfo> versions = new ConcurrentHashMap<>();

    FhirPackageVersionInfo v1 = new FhirPackageVersionInfo();
    v1.setName("test.package");
    v1.setVersion("1.0.0");
    v1.setProtectedPackage(false);

    FhirPackageVersionInfo v2 = new FhirPackageVersionInfo();
    v2.setName("test.package");
    v2.setVersion("2.0.0");
    v2.setProtectedPackage(false);

    versions.put("1.0.0", v1);
    versions.put("2.0.0", v2);
    packageInfo.setVersions(versions);

    repository.getPackageInfoIndex().put("test.package", packageInfo);

    // Pass null for packageVersion -> check any version
    boolean result = repository.isPackageVersionProtected("test.package", null);
    assertThat(result).isFalse();
  }

  @Test
  void testIsPackageVersionProtected_gcloudEnabled_versionSpecified_notFound() {
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(true);

    // Create a package with a single version "1.0.0"
    FhirPackageInfo packageInfo = new FhirPackageInfo();
    packageInfo.setName("test.package");
    ConcurrentHashMap<String, FhirPackageVersionInfo> versions = new ConcurrentHashMap<>();

    FhirPackageVersionInfo v1 = new FhirPackageVersionInfo();
    v1.setName("test.package");
    v1.setVersion("1.0.0");
    v1.setProtectedPackage(true);

    versions.put("1.0.0", v1);
    packageInfo.setVersions(versions);

    repository.getPackageInfoIndex().put("test.package", packageInfo);

    // We ask about version "2.0.0" which doesn't exist in versions
    boolean result = repository.isPackageVersionProtected("test.package", "2.0.0");
    assertThat(result).isFalse();
  }

  @Test
  void testIsPackageVersionProtected_gcloudEnabled_versionSpecified_existsButNotProtected() {
    when(properties.isGCloudAnnotationProcessingEnabled()).thenReturn(true);

    // Create a package with a version that is explicitly not protected
    FhirPackageInfo packageInfo = new FhirPackageInfo();
    packageInfo.setName("test.package");
    ConcurrentHashMap<String, FhirPackageVersionInfo> versions = new ConcurrentHashMap<>();

    FhirPackageVersionInfo v1 = new FhirPackageVersionInfo();
    v1.setName("test.package");
    v1.setVersion("1.0.0");
    v1.setProtectedPackage(false); // a real version, but not protected
    versions.put("1.0.0", v1);

    packageInfo.setVersions(versions);
    repository.getPackageInfoIndex().put("test.package", packageInfo);

    // Ask about that known version
    boolean result = repository.isPackageVersionProtected("test.package", "1.0.0");
    assertThat(result).isFalse();
  }

  @Test
  void testGetDocument_exactVersionMatch_withPreReleaseVersion() throws Exception {
    // We need to index packages through the repository's normal flow
    // so that documents are created with the correct structure

    // Create two mock packages
    FhirPackage package1 = mockFhirPackage("test-package", "1.0.0");
    FhirPackage package2 = mockFhirPackage("test-package", "1.0.0-rc.20251027172931");

    // Create dummy files
    Path file1 = cacheDir.resolve("test-package-1.0.0.tgz");
    Path file2 = cacheDir.resolve("test-package-1.0.0-rc.tgz");
    Files.createFile(file1);
    Files.createFile(file2);

    try (MockedStatic<FhirPackage> staticMock = mockStatic(FhirPackage.class);
        MockedStatic<FileHelper> fileHelperMock = mockStatic(FileHelper.class)) {

      // Mock the file hash calculation
      fileHelperMock
          .when(() -> FileHelper.calculateFileHash(any(Path.class)))
          .thenReturn("dummyhash123");

      // Mock buildFromTarGz to return the appropriate package based on the file path
      staticMock
          .when(() -> FhirPackage.buildFromTarGz(eq(file1)))
          .thenReturn(Optional.of(package1));
      staticMock
          .when(() -> FhirPackage.buildFromTarGz(eq(file2)))
          .thenReturn(Optional.of(package2));

      // Index both packages through the repository
      boolean indexed1 = repository.indexPackageFile(file1, null, null);
      boolean indexed2 = repository.indexPackageFile(file2, null, null);

      assertThat(indexed1).isTrue();
      assertThat(indexed2).isTrue();

      // Call the real getDocument method (not mocked)
      doCallRealMethod().when(repository).getDocument(anyString(), anyString());

      // Test 1: Request exact version "1.0.0"
      Document result1 = repository.getDocument("test-package", "1.0.0");
      assertThat(result1).isNotNull();
      assertThat(result1.get(PACKAGE_NAME + SEPARATOR + SUFFIX_STRING)).isEqualTo("test-package");
      assertThat(result1.get(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING)).isEqualTo("1.0.0");

      // Test 2: Request exact version "1.0.0-rc.20251027172931"
      Document result2 = repository.getDocument("test-package", "1.0.0-rc.20251027172931");
      assertThat(result2).isNotNull();
      assertThat(result2.get(PACKAGE_NAME + SEPARATOR + SUFFIX_STRING)).isEqualTo("test-package");
      assertThat(result2.get(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING))
          .isEqualTo("1.0.0-rc.20251027172931");

      // Verify that the versions are different
      assertThat(result1.get(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING))
          .isNotEqualTo(result2.get(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING));

      // Test 3: Request a non-existing version "1.0.0-rc.20241227172931", there should be no match
      // as the Query is for exact version
      Document result3 = repository.getDocument("test-package", "1.0.0-rc.20241227172931");
      assertThat(result3).isNull();
    }
  }
}
