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

import static de.gematik.zts.npmproxy.NpmProxyConstants.REGEXP_CANONICAL_VERSION_SEPARATOR;
import static de.gematik.zts.npmproxy.SearchTestHelper.createEmptySearchPackageParameters;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.ServiceHelper;
import de.gematik.zts.npmproxy.model.*;
import de.gematik.zts.npmproxy.repository.lucene.CatalogSearchHelper;
import de.gematik.zts.npmproxy.tools.FileHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

@Slf4j
class LuceneBackedPackageRepositoryTest {

  private static final String FILENAME_TEST_PACKAGE_1_0_0 =
      "testPackages/bfarm.terminologien.test-1.0.0.tgz";
  private static final String FILENAME_TEST_PACKAGE_2_0_0 =
      "testPackages/bfarm.terminologien.test-2.0.0.tgz";
  private static final String FILENAME_OTHERTEST_PACKAGE_1_0_0 =
      "testPackages/bfarm.terminologien.othertest-1.0.0.tgz";
  private static final String FILENAME_KEYWORD_TEST_PACKAGE_1_0_0 =
      "testPackages/some.random.package-1.0.0.tgz";
  private static final String FILENAME_PRERELEASE_TEST_PACKAGE_1_0_0 =
      "testPackages/some.prerelease.package-1.0.0-rc1.tgz";
  private static final String FILENAME_NO_MANIFEST_TEST_PACKAGE_1_0_0 =
      "testPackages/package.no.manifest-1.0.0-rc1.tgz";
  private LuceneBackedPackageRepository repository;
  private NpmProxyConfiguration properties;
  private Path cacheDir;

  @BeforeEach
  void setUp() throws IOException {
    // Testverzeichnis anlegen
    log.info("Creating temp directory");
    cacheDir = Files.createTempDirectory("packages");

    // Property Mock vorbereiten
    properties = mock(NpmProxyConfiguration.class);
    when(properties.getPackageCacheDir()).thenReturn(cacheDir.toString());
    when(properties.getHostName()).thenReturn("http://localhost");
    when(properties.getNpmPath()).thenReturn("/packages");
  }

  @AfterEach
  void tearDown() throws IOException {
    // Testverzeichnis löschen
    Files.walk(cacheDir)
        .map(Path::toFile)
        .forEach(
            file -> {
              log.info("Deleting file/folder: {}", file);
              file.delete();
            });
  }

  /**
   * Testet die Initialisierung des Repositories, für den Fall, dass das Cache-Verzeichnis leer ist.
   * Im Ergebnis wird erwartet, dass das Repository initialisiert ist, die richtigen Pakete
   * gemonitort werden aber keine Pakete gefunden wurden.
   */
  @Test
  void testInitializeNoCacheDirContent() {

    // Umgebung mocken
    when(properties.getMonitoredPackages())
        .thenReturn(Set.of("bfarm.terminologien.test", "bfarm.terminologien.othertest"));

    // Indirekter Methodenaufruf über Konstruktor
    repository = new LuceneBackedPackageRepository(properties);

    // Repository sollte initialisiert sein
    assertTrue(repository.isInitialized());

    // Das Repository sollte keine Pakete enthalten
    assertEquals(0, repository.getPackageCount());
    assertFalse(repository.findPackageByName("bfarm.terminologien.test").isPresent());
    assertFalse(repository.findPackageByName("bfarm.terminologien.othertest").isPresent());

    // Das Repository sollte keine Pakete enthalten
    assertEquals(0, repository.getPackageCount());
    List<FhirPackageBaseInfo> baseInfos =
        repository.searchPackages(
            new SearchPackageParameters(
                Optional.of("bfarm.terminologien.test"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));

    assertTrue(baseInfos.isEmpty());

    List<FhirPackageBaseInfo> baseInfosOther =
        repository.searchPackages(
            new SearchPackageParameters(
                Optional.of("bfarm.terminologien.othertest"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));

    assertTrue(baseInfosOther.isEmpty());
  }

  /**
   * Testet die Initialisierung des Repositories, für den Fall, dass das Cache-Verzeichnis ein
   * einzelnes Paket enthält. Im Ergebnis wird erwartet, dass das Repository initialisiert ist, das
   * Paket gefunden wurde und die richtigen Pakete gemonitort werden.
   */
  @Test
  void testInitializeSinglePackageCacheDirContent() {

    // Umgebung mocken
    when(properties.getMonitoredPackages())
        .thenReturn(Set.of("bfarm.terminologien.test", "bfarm.terminologien.othertest"));
    ServiceHelper.copyTestPackage(FILENAME_TEST_PACKAGE_1_0_0, cacheDir.toString());

    // Indirekter Methodenaufruf über Konstruktor
    repository = new LuceneBackedPackageRepository(properties);

    // Repository sollte initialisiert sein
    assertTrue(repository.isInitialized());

    // Das Repository sollte ein einzelnes Paket enthalten
    assertEquals(1, repository.getPackageCount());
    List<FhirPackageBaseInfo> baseInfos =
        repository.searchPackages(
            new SearchPackageParameters(
                Optional.of("bfarm.terminologien.test"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));

    assertFalse(baseInfos.isEmpty());

    List<FhirPackageBaseInfo> baseInfosOther =
        repository.searchPackages(
            new SearchPackageParameters(
                Optional.of("bfarm.terminologien.othertest"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));

    assertTrue(baseInfosOther.isEmpty());
  }

  /**
   * Testet die Initialisierung des Repositories, für den Fall, dass das Cache-Verzeichnis ein
   * einzelnes Paket enthält. Im Ergebnis wird erwartet, dass das Repository initialisiert ist, das
   * Paket gefunden wurde und die richtigen Pakete gemonitort werden.
   */
  @Test
  void testInitializeWithInvalidPackage() {

    ServiceHelper.copyTestPackage(FILENAME_NO_MANIFEST_TEST_PACKAGE_1_0_0, cacheDir.toString());

    // Indirekter Methodenaufruf über Konstruktor
    repository = new LuceneBackedPackageRepository(properties);

    // Repository sollte initialisiert sein
    assertTrue(repository.isInitialized());

    // Das Repository sollte ein einzelnes Paket enthalten
    assertEquals(0, repository.getPackageCount());
  }

  @Test
  void testSearchPackages_PackageInfoIsNull() {
    // Monitored Packages einrichten
    when(properties.getMonitoredPackages()).thenReturn(Set.of("existent.package"));

    // Repository initialisieren
    repository = new LuceneBackedPackageRepository(properties);

    // Mock des resultSetMap mit einem Paketnamen, der nicht im packageInfoIndex vorhanden ist
    Map<String, LuceneFhirPackageSearchResult> resultSetMap = new HashMap<>();
    resultSetMap.put("nonexistent.package", null);

    // Mock der statischen Methode CatalogSearchHelper.processQueryAndReturnPackageNameList
    try (MockedStatic<CatalogSearchHelper> mockedStatic = mockStatic(CatalogSearchHelper.class)) {
      mockedStatic
          .when(() -> CatalogSearchHelper.processQueryAndReturnPackageNameList(any(), any()))
          .thenReturn(resultSetMap);

      // Aufruf der Methode
      List<FhirPackageBaseInfo> result =
          repository.searchPackages(createEmptySearchPackageParameters());

      // Überprüfung, dass die Ergebnisliste leer ist, da packageInfo null ist
      assertTrue(result.isEmpty());
    }
  }

  @Test
  void testSearchPackages_SearchResultIsNull() {
    when(properties.getMonitoredPackages()).thenReturn(Set.of("test.package"));

    repository = new LuceneBackedPackageRepository(properties);

    // Erstellen eines gültigen packageInfo mit Versionen
    FhirPackageInfo packageInfo = new FhirPackageInfo();
    packageInfo.setName("test.package");
    ConcurrentHashMap<String, FhirPackageVersionInfo> versions = new ConcurrentHashMap<>();
    FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();
    versionInfo.setDescription("Test description");
    versionInfo.setFhirVersion("4.0.1");
    versions.put("1.0.0", versionInfo);
    packageInfo.setVersions(versions);

    // Spy auf Repository und Mock von findPackageByName
    LuceneBackedPackageRepository spyRepository = spy(repository);
    doReturn(Optional.of(packageInfo)).when(spyRepository).findPackageByName("test.package");

    // Mock des resultSetMap mit null searchResult
    Map<String, LuceneFhirPackageSearchResult> resultSetMap = new HashMap<>();
    resultSetMap.put("test.package", null);

    try (MockedStatic<CatalogSearchHelper> mockedStatic = mockStatic(CatalogSearchHelper.class)) {
      mockedStatic
          .when(() -> CatalogSearchHelper.processQueryAndReturnPackageNameList(any(), any()))
          .thenReturn(resultSetMap);

      // Aufruf der Methode
      List<FhirPackageBaseInfo> result =
          spyRepository.searchPackages(createEmptySearchPackageParameters());

      // Überprüfung
      assertEquals(1, result.size());
      FhirPackageBaseInfo baseInfo = result.get(0);
      assertEquals("Test description", baseInfo.getDescription());
      assertEquals("4.0.1", baseInfo.getFhirVersion());
      assertNull(
          baseInfo
              .getKeywords()); // Da searchResult null ist sollten auch keine keywords gesetzt sein
    }
  }

  @Test
  void testIndexPackageFileInEmptyRepoSuccess() throws IOException, NoSuchAlgorithmException {

    // Umgebung mocken
    when(properties.getMonitoredPackages())
        .thenReturn(Set.of("bfarm.terminologien.test", "bfarm.terminologien.othertest"));

    // Indizieren eines leeren Cache-Verzeichnisses: Anschließend sollte im Index das Paket nicht
    // gefunden werden.
    repository = new LuceneBackedPackageRepository(properties);
    assertTrue(repository.findPackageByName("bfarm.terminologien.test").isEmpty());

    // Ablegen des zu indizierenden Pakets im Cache-Verzeichnis
    Path testPackage1FilePath =
        ServiceHelper.copyTestPackage(FILENAME_TEST_PACKAGE_1_0_0, cacheDir.toString());

    // Methodenaufruf (Indizieren des kopierte Paket)
    boolean indexingResult = repository.indexPackageFile(testPackage1FilePath, null, null);

    // Der Methodenaufruf sollte true zurückgeben, da das Paket indiziert wurde
    assertTrue(indexingResult);

    // Das Paket sollte nun im Repository zu finden sein
    assertEquals(1, repository.getPackageCount());
    assertTrue(repository.findPackageByName("bfarm.terminologien.test").isPresent());
    assertTrue(repository.getPackagePathIndex("bfarm.terminologien.test", "1.0.0").isPresent());

    // Die Paketinformationen sollten korrekt sein
    FhirPackageInfo packageInfo = repository.findPackageByName("bfarm.terminologien.test").get();
    assertEquals("bfarm.terminologien.test", packageInfo.getName());
    assertEquals("bfarm.terminologien.test", packageInfo.getId());
    assertEquals("1.0.0", packageInfo.getLatestDistTags().get("latest"));

    // Die Paketversionen sollten korrekt sein
    assertNotNull(packageInfo.getVersions());
    assertEquals(1, packageInfo.getVersions().size());
    Map<String, FhirPackageVersionInfo> versions = packageInfo.getVersions();
    assertTrue(versions.containsKey("1.0.0"));
    FhirPackageVersionInfo versionInfo = versions.get("1.0.0");
    assertEquals("bfarm.terminologien.test", versionInfo.getName());
    assertEquals("1.0.0", versionInfo.getVersion());
    assertEquals("Das Package enthält Tests", versionInfo.getDescription());
    assertEquals("Test-Package", versionInfo.getTitle());
    assertEquals("Test", versionInfo.getAltTitle());
    assertNotNull(versionInfo.getDist());
    FhirPackageVersionDistInfo distInfo = versionInfo.getDist();
    assertEquals(
        properties.getHostName()
            + properties.getNpmPath()
            + "/"
            + versionInfo.getName()
            + "/"
            + versionInfo.getVersion(),
        distInfo.getTarball());
    assertEquals(FileHelper.calculateFileHash(testPackage1FilePath), distInfo.getShasum());
  }

  /**
   * Testet das Indizieren eines Pakets in einem nicht leeren Repo. Im Ergebnis sollte das Paket
   * indiziert werden und im Repository mit korrekten Angaben zu finden sein.
   *
   * @throws IOException
   * @throws NoSuchAlgorithmException
   */
  @Test
  void testIndexPackageFileInNonEmptyRepoExistingPackageSuccess()
      throws IOException, NoSuchAlgorithmException {

    // Umgebung mocken
    when(properties.getMonitoredPackages())
        .thenReturn(Set.of("bfarm.terminologien.test", "bfarm.terminologien.othertest"));
    ServiceHelper.copyTestPackage(FILENAME_TEST_PACKAGE_1_0_0, cacheDir.toString());

    // Indizieren des vorbefüllten Cache-Verzeichnisses: Anschließend sollte im Index das Paket
    // gefunden werden.
    repository = new LuceneBackedPackageRepository(properties);
    assertEquals(1, repository.getPackageCount());
    assertTrue(repository.findPackageByName("bfarm.terminologien.test").isPresent());

    // Ablegen des zusätzlich zu indizierenden Pakets im Cache-Verzeichnis
    Path testPackage2FilePath =
        ServiceHelper.copyTestPackage(FILENAME_TEST_PACKAGE_2_0_0, cacheDir.toString());

    // Methodenaufruf (Indizieren des zusätzlichen Pakets)
    boolean indexingResult = repository.indexPackageFile(testPackage2FilePath, null, null);

    // Der Methodenaufruf sollte true zurückgeben, da das Paket indiziert wurde
    assertTrue(indexingResult);

    // Es ist weiterhin nur ein Paket im Repository (aber zwei Paketversionen)
    assertEquals(1, repository.getPackageCount());
    assertTrue(repository.findPackageByName("bfarm.terminologien.test").isPresent());

    // Beide Paketversionen sollten im Repository zu finden sein
    assertTrue(repository.getPackagePathIndex("bfarm.terminologien.test", "1.0.0").isPresent());
    assertTrue(repository.getPackagePathIndex("bfarm.terminologien.test", "2.0.0").isPresent());

    // Die Paketinformationen sollten korrekt sein
    FhirPackageInfo packageInfo = repository.findPackageByName("bfarm.terminologien.test").get();
    assertEquals("bfarm.terminologien.test", packageInfo.getName());
    assertEquals("bfarm.terminologien.test", packageInfo.getId());
    // Die neueste Version sollte 2.0.0 sein
    assertEquals("2.0.0", packageInfo.getLatestDistTags().get("latest"));

    // Die neue Paketversion sollte korrekt sein
    assertNotNull(packageInfo.getVersions());
    assertEquals(2, packageInfo.getVersions().size());
    Map<String, FhirPackageVersionInfo> versions = packageInfo.getVersions();
    assertTrue(versions.containsKey("2.0.0"));
    FhirPackageVersionInfo versionInfo = versions.get("2.0.0");
    assertEquals("bfarm.terminologien.test", versionInfo.getName());
    assertEquals("2.0.0", versionInfo.getVersion());
    assertEquals("Das Package enthält Tests", versionInfo.getDescription());
    assertNotNull(versionInfo.getDist());
    FhirPackageVersionDistInfo distInfo = versionInfo.getDist();
    assertEquals(
        properties.getHostName()
            + properties.getNpmPath()
            + "/"
            + versionInfo.getName()
            + "/"
            + versionInfo.getVersion(),
        distInfo.getTarball());
    assertEquals(FileHelper.calculateFileHash(testPackage2FilePath), distInfo.getShasum());
  }

  @Test
  void testIndexPackageFileInNonEmptyRepoNonExistingPackageSuccess()
      throws IOException, NoSuchAlgorithmException {

    // Umgebung mocken
    when(properties.getMonitoredPackages())
        .thenReturn(Set.of("bfarm.terminologien.test", "bfarm.terminologien.othertest"));
    ServiceHelper.copyTestPackage(FILENAME_TEST_PACKAGE_1_0_0, cacheDir.toString());

    // Indizieren des vorbefüllten Cache-Verzeichnisses: Anschließend sollte im Index das Paket
    // gefunden werden.
    repository = new LuceneBackedPackageRepository(properties);
    assertEquals(1, repository.getPackageCount());
    assertTrue(repository.findPackageByName("bfarm.terminologien.test").isPresent());

    // Ablegen des zusätzlich zu indizierenden Pakets im Cache-Verzeichnis
    Path othertestPackage1FilePath =
        ServiceHelper.copyTestPackage(FILENAME_OTHERTEST_PACKAGE_1_0_0, cacheDir.toString());

    // Methodenaufruf (Indizieren des zusätzlichen Pakets)
    boolean indexingResult = repository.indexPackageFile(othertestPackage1FilePath, null, null);

    // Der Methodenaufruf sollte true zurückgeben, da das Paket indiziert wurde
    assertTrue(indexingResult);

    // Es gibt jetzt 2 Pakete im Repository
    assertEquals(2, repository.getPackageCount());
    assertTrue(repository.findPackageByName("bfarm.terminologien.test").isPresent());
    assertTrue(repository.findPackageByName("bfarm.terminologien.othertest").isPresent());

    // Beide Paketversionen sollten im Repository zu finden sein
    assertTrue(repository.getPackagePathIndex("bfarm.terminologien.test", "1.0.0").isPresent());
    assertTrue(
        repository.getPackagePathIndex("bfarm.terminologien.othertest", "1.0.0").isPresent());

    // Die Paketinformationen sollten korrekt sein
    FhirPackageInfo packageInfo =
        repository.findPackageByName("bfarm.terminologien.othertest").get();
    assertEquals("bfarm.terminologien.othertest", packageInfo.getName());
    assertEquals("bfarm.terminologien.othertest", packageInfo.getId());
    assertEquals("1.0.0", packageInfo.getLatestDistTags().get("latest"));

    // Die neue Paketversion sollte korrekt sein
    assertNotNull(packageInfo.getVersions());
    assertEquals(1, packageInfo.getVersions().size());
    Map<String, FhirPackageVersionInfo> versions = packageInfo.getVersions();
    assertTrue(versions.containsKey("1.0.0"));
    FhirPackageVersionInfo versionInfo = versions.get("1.0.0");
    assertEquals("bfarm.terminologien.othertest", versionInfo.getName());
    assertEquals("1.0.0", versionInfo.getVersion());
    assertEquals("Das Package enthält Other-Tests", versionInfo.getDescription());
    assertNotNull(versionInfo.getDist());
    FhirPackageVersionDistInfo distInfo = versionInfo.getDist();
    assertEquals(
        properties.getHostName()
            + properties.getNpmPath()
            + "/"
            + versionInfo.getName()
            + "/"
            + versionInfo.getVersion(),
        distInfo.getTarball());
    assertEquals(FileHelper.calculateFileHash(othertestPackage1FilePath), distInfo.getShasum());
  }

  /**
   * Testet das Indizieren eines Pakets, welches fehlerhaft ist. Im Ergebnis wird erwartet, dass das
   * Paket nicht indiziert wird.
   *
   * @throws IOException
   */
  @Test
  void testIndexPackageFileNullPackageVersionInfo() throws IOException {

    // Umgebung mocken
    when(properties.getMonitoredPackages())
        .thenReturn(Set.of("bfarm.terminologien.test", "bfarm.terminologien.othertest"));

    // Initialisieren des Repos aus einem leeren Cache-Verzeichnis: Anschließend sollte der Index
    // leer sein
    repository = new LuceneBackedPackageRepository(properties);
    assertEquals(0, repository.getPackageCount());

    // Vorbereiten eines fehlerhaften Pakets
    Path brokenPackageFilePath = Files.createTempFile("broken", ".tgz");

    // Methodenaufruf (Indizieren des fehlerhaften Pakets)
    boolean indexingResult = repository.indexPackageFile(brokenPackageFilePath, null, null);

    // Der Methodenaufruf sollte false zurückgeben, da das Paket nicht indiziert werden konnte
    assertFalse(indexingResult);

    // Das Repo sollte weiterhin leer sein
    assertEquals(0, repository.getPackageCount());

    // Aufräumen
    Files.deleteIfExists(brokenPackageFilePath);
  }

  /** Testet das Erstellen eines eindeutigen Identifiers für eine Paketversion. */
  @Test
  void testGetPackageVersionIdentifier() {
    String identifier =
        LuceneBackedPackageRepository.getPackageVersionIdentifier("package", "1.0.0");
    assertEquals("package#1.0.0", identifier);
  }

  /**
   * Testet das Parsen einer package.json-Datei aus einem Tar-Gz-Archiv. Im Ergebnis wird erwartet,
   * dass die Paketinformationen korrekt geparst werden.
   */
  @Test
  void testParsePackageJsonFileFromTarGzAllOkay() {

    // Vorbereiten eines Pakets
    Path testPackage1FilePath =
        ServiceHelper.copyTestPackage(FILENAME_TEST_PACKAGE_1_0_0, cacheDir.toString());

    Optional<FhirPackage> fhirPackage = FhirPackage.buildFromTarGz(testPackage1FilePath);

    assertTrue(fhirPackage.isPresent());
    // Parsen des fehlerhaften Pakets
    FhirPackageVersionInfo versionInfo =
        LuceneBackedPackageRepository.extractFhirPackageVersionInfoFromPackageManifest(
            fhirPackage.get().getManifest());

    // Das Ergebnis sollte nicht null sein
    assertNotNull(versionInfo);
    assertEquals("bfarm.terminologien.test", versionInfo.getName());
    assertEquals("1.0.0", versionInfo.getVersion());
    assertEquals("Das Package enthält Tests", versionInfo.getDescription());
    assertEquals("R4", versionInfo.getFhirVersion());
    // Noch nicht implementierte Datenübernahme
    assertNull(versionInfo.getUnlisted());
  }

  /**
   * Testet das Parsen eines fehlerhaften Pakets. Im Ergebnis wird erwartet, dass das Parsen
   * fehlschlägt und null zurückgegeben wird.
   *
   * @throws IOException
   */
  @Test
  void testParsePackageJsonFileFromTarGzBrokenFile() throws IOException {

    // Vorbereiten eines fehlerhaften Pakets
    Path brokenPackageFilePath = Files.createTempFile("broken", ".tgz");

    Optional<FhirPackage> fhirPackage = FhirPackage.buildFromTarGz(brokenPackageFilePath);
    assertTrue(fhirPackage.isEmpty());

    // Aufräumen
    Files.delete(brokenPackageFilePath);
  }

  /**
   * Testet das Selektieren eines indizierten Pakets anhand des Paketnamens. Im Ergebnis wird
   * erwartet, dass das Paket gefunden wird und Paketinformationen zurückgegeben werden.
   */
  @Test
  void testFindPackageByName() {

    // Umgebung mocken
    when(properties.getMonitoredPackages())
        .thenReturn(Set.of("bfarm.terminologien.test", "bfarm.terminologien.othertest"));
    ServiceHelper.copyTestPackage(FILENAME_TEST_PACKAGE_1_0_0, cacheDir.toString());

    // Initialisieren des Repos aus einem vorbefüllten Cache-Verzeichnis
    repository = new LuceneBackedPackageRepository(properties);

    Optional<FhirPackageInfo> packageInfo =
        repository.findPackageByName("bfarm.terminologien.test");
    assertTrue(packageInfo.isPresent());

    Optional<FhirPackageInfo> packageInfo2 =
        repository.findPackageByName("bfarm.terminologien.testnonexisting");
    assertTrue(packageInfo2.isEmpty());
  }

  /**
   * Testet das Heraussuchen eines Dateipfades anhand des Paketnamens und der Paketversion. Im
   * Ergebnis wird erwartet, dass für indizierte Pakete der Dateipfad zurückgegeben wird und für
   * nicht indizierte Pakete ein leerer Optional.
   */
  @Test
  void testGetPackagePathIndex() {

    // Umgebung mocken
    when(properties.getMonitoredPackages())
        .thenReturn(Set.of("bfarm.terminologien.test", "bfarm.terminologien.othertest"));
    ServiceHelper.copyTestPackage(FILENAME_TEST_PACKAGE_1_0_0, cacheDir.toString());

    // Initialisieren des Repos aus einem vorbefüllten Cache-Verzeichnis
    repository = new LuceneBackedPackageRepository(properties);

    Optional<Path> path = repository.getPackagePathIndex("bfarm.terminologien.test", "1.0.0");
    assertTrue(path.isPresent());

    Optional<Path> path2 = repository.getPackagePathIndex("bfarm.terminologien.test", "2.0.0");
    assertTrue(path2.isEmpty());
  }

  /**
   * Testet das korrekte Zählen der indizierten Pakete. Wichtig: Hier werden nur die Pakete gezählt,
   * nicht die Paketversionen.
   */
  @Test
  void testGetPackageCount() {

    // Umgebung mocken
    when(properties.getMonitoredPackages())
        .thenReturn(Set.of("bfarm.terminologien.test", "bfarm.terminologien.othertest"));
    ServiceHelper.copyTestPackage(FILENAME_TEST_PACKAGE_1_0_0, cacheDir.toString());
    ServiceHelper.copyTestPackage(FILENAME_TEST_PACKAGE_2_0_0, cacheDir.toString());
    ServiceHelper.copyTestPackage(FILENAME_OTHERTEST_PACKAGE_1_0_0, cacheDir.toString());

    // Initialisieren des Repos aus einem vorvefüllten Cache-Verzeichnis
    repository = new LuceneBackedPackageRepository(properties);

    // Wir erwarten 2 geladene Pakete (obwohl insgesamt 3 Paketversionen im Cache sind)
    assertEquals(2, repository.getPackageCount());
  }

  // search tests
  @Test
  void testSearchPackages_SearchResultKeywordsEmpty() {
    when(properties.getMonitoredPackages()).thenReturn(Set.of("test.package"));

    repository = new LuceneBackedPackageRepository(properties);

    // Erstellen eines gültigen packageInfo mit Versionen
    FhirPackageInfo packageInfo = new FhirPackageInfo();
    packageInfo.setName("test.package");
    ConcurrentHashMap<String, FhirPackageVersionInfo> versions = new ConcurrentHashMap<>();
    FhirPackageVersionInfo versionInfo = new FhirPackageVersionInfo();
    versionInfo.setDescription("Test description");
    versionInfo.setFhirVersion("4.0.1");
    versions.put("1.0.0", versionInfo);
    packageInfo.setVersions(versions);

    // Spy auf Repository und Mock von findPackageByName
    LuceneBackedPackageRepository spyRepository = spy(repository);
    doReturn(Optional.of(packageInfo)).when(spyRepository).findPackageByName("test.package");

    // Mock des resultSetMap mit leerer Keyword-Liste
    LuceneFhirPackageSearchResult searchResult = new LuceneFhirPackageSearchResult();
    searchResult.setKeywords(Collections.emptyList());
    Map<String, LuceneFhirPackageSearchResult> resultSetMap = new HashMap<>();
    resultSetMap.put("test.package", searchResult);

    try (MockedStatic<CatalogSearchHelper> mockedStatic = mockStatic(CatalogSearchHelper.class)) {
      mockedStatic
          .when(() -> CatalogSearchHelper.processQueryAndReturnPackageNameList(any(), any()))
          .thenReturn(resultSetMap);

      // Aufruf der Methode
      List<FhirPackageBaseInfo> result =
          spyRepository.searchPackages(createEmptySearchPackageParameters());

      // Überprüfung
      assertEquals(1, result.size());
      FhirPackageBaseInfo baseInfo = result.get(0);
      assertEquals("Test description", baseInfo.getDescription());
      assertEquals("4.0.1", baseInfo.getFhirVersion());
      // Keywords sollten nicht gesetzt sein
      assertTrue(baseInfo.getKeywords() == null || baseInfo.getKeywords().isEmpty());
    }
  }

  @ParameterizedTest(name = "[{index}] {arguments}")
  @CsvSource(
      value = {
        // all packages
        " % % % % % % % % 3",
        // only test package
        "bfarm.terminologien.test % % % % % % % % 1",
        // all packages with name bfarm.terminologien.*
        "bfarm.terminologien. % % % % % % % % 2",
        // all packages with version 1.0.0 -> 3 packages
        " % 1.0.0 % % % % % % % 3",
        // all packages with version 2.0.0 -> 1 package
        " % 2.0.0 % % % % % % % 1",
        // all packages with containing canonical-> 1 package
        " % % https://terminologien.bfarm.de/fhir/CodeSystem/test % % % % % % 1",
        // all packages with containing canonical (part)-> 2 packages
        " % % https://terminologien.bfarm.de/fhir/CodeSystem/ % % % % % % 2",
        // all packages with fhirVersion R4 -> 2 packages
        " % % % % R4 % % % % 3",
        // all packages with fhirVersion R5 -> 0 packages
        " % % % % R5 % % % % 0",
        // all packages with prerelease true -> 4 packages
        " % % % % % true % % % 4",
        // all packages with prerelease false -> 3 packages
        " % % % % % false % % % 3",
        // all packages with protectedPackage true -> 1 package
        " % % % % % % true % % 1",
        // all packages with protectedPackage false -> 2 packages
        " % % % % % % false % % 2",
        // all packages with keywords Test -> 2 packages
        " % % % % % % % Test % 2",
        // all packages with keywords Test (including prerelease) -> 3 packages
        " % % % % % true % % Test % 3",
        // all packages with keywords Other-Test -> 1 package
        " % % % % % % % Other-Test % 1",
        // all packages with keywords Test AND Other-Test -> 0 package
        " % % % % % % % Test#Other-Test % 0",
        // all packages with keywords Keyword1 AND Keyword2 -> 1 packages
        " % % % % % % % Keyword1#Keyword2 % 1",
        // all packages with keywords Keyword1 AND Keyword2 (including prerelease) -> 2 packages
        " % % % % % true % % Keyword1#Keyword2 % 2",
        // all packages with keywords Test OR Other-Test -> 3 packages
        " % % % % % % % Other-Test,Test % 3",
      },
      nullValues = "NIL",
      delimiter = '%')
  void testSearchVariations(
      String packageName,
      String packageVersion,
      String canonical,
      String pkgcanonical,
      String fhirVersion,
      String prerelease,
      String protectedPackage,
      String keywords,
      int expectedPackageCount) {

    // Umgebung mocken
    // monitored packages
    when(properties.getMonitoredPackages())
        .thenReturn(
            Set.of(
                "bfarm.terminologien.test",
                "bfarm.terminologien.othertest",
                "some.random.package",
                "some.prerelease.package"));
    // protected packages
    when(properties.getProtectedPackages()).thenReturn(Set.of("bfarm.terminologien.test"));

    // name = bfarm.terminologien.test
    // version = 1.0.0
    // canonical = https://terminologien.bfarm.de/fhir/CodeSystem/test (in CS)
    // pkgcanonical = null
    // fhirVersion = 4.0.1 (R4)
    // prerelease = false
    // protectedPackage = true
    // keywords = Test
    ServiceHelper.copyTestPackage(FILENAME_TEST_PACKAGE_1_0_0, cacheDir.toString());
    // name = bfarm.terminologien.test
    // version = 2.0.0
    // canonical = https://terminologien.bfarm.de/fhir/CodeSystem/test (in CS)
    // pkgcanonical = null
    // fhirVersion = 4.0.1 (R4)
    // prerelease = false
    // protectedPackage = true
    // keywords = Test
    ServiceHelper.copyTestPackage(FILENAME_TEST_PACKAGE_2_0_0, cacheDir.toString());
    // name = bfarm.terminologien.othertest
    // version = 1.0.0
    // canonical = https://terminologien.bfarm.de/fhir/CodeSystem/othertest (in CS)
    // pkgcanonical = null
    // fhirVersion = 4.0.1 (R4)
    // prerelease = false
    // protectedPackage = false
    // keywords = Other-Test
    ServiceHelper.copyTestPackage(FILENAME_OTHERTEST_PACKAGE_1_0_0, cacheDir.toString());

    // name = bfarm.terminologien.othertest
    // version = 1.0.0
    // canonical = https://terminologien.bfarm.de/fhir/CodeSystem/othertest (in CS)
    // pkgcanonical = null
    // fhirVersion = 4.0.1 (R4)
    // prerelease = false
    // protectedPackage = false
    // keywords = Other-Test
    ServiceHelper.copyTestPackage(FILENAME_OTHERTEST_PACKAGE_1_0_0, cacheDir.toString());

    // name = some.random.package
    // version = 1.0.0
    // canonical = https://some.random.package/fhir/CodeSystem/keywordtest (in CS)
    // pkgcanonical = null
    // fhirVersion = 4.0.1 (R4)
    // prerelease = false
    // protectedPackage = false
    // keywords = Keyword1,Keyword2,Test
    ServiceHelper.copyTestPackage(FILENAME_KEYWORD_TEST_PACKAGE_1_0_0, cacheDir.toString());

    // name = some.prerelease.package
    // version = 1.0.0-rc1
    // canonical = https://some.prerelease.package/fhir/CodeSystem/prerelease (in CS)
    // pkgcanonical = null
    // fhirVersion = 4.0.1 (R4)
    // prerelease = true
    // protectedPackage = false
    // keywords = Keyword1,Keyword2,Prerelease,Test
    ServiceHelper.copyTestPackage(FILENAME_PRERELEASE_TEST_PACKAGE_1_0_0, cacheDir.toString());

    // Indirekter Methodenaufruf über Konstruktor
    repository = new LuceneBackedPackageRepository(properties);

    // Repository sollte initialisiert sein
    assertTrue(repository.isInitialized());

    // Wir erwarten 4 geladene Pakete (obwohl insgesamt 5 Paketversionen im Cache sind)
    assertEquals(4, repository.getPackageCount());

    // prepare keywords
    List<String> keywordsList = null;
    if (keywords != null) {
      keywordsList = List.of(keywords.split("#"));
    }

    String canonicalVersion = null;
    if (canonical != null) {
      String[] canonicalParts = canonical.split(REGEXP_CANONICAL_VERSION_SEPARATOR);
      canonical = canonicalParts[0];
      canonicalVersion = canonicalParts.length == 2 ? canonicalParts[1] : null;
    }

    List<FhirPackageBaseInfo> baseInfos =
        repository.searchPackages(
            new SearchPackageParameters(
                packageName != null ? Optional.of(packageName) : Optional.empty(),
                packageVersion != null ? Optional.of(packageVersion) : Optional.empty(),
                canonical != null ? Optional.of(canonical) : Optional.empty(),
                canonicalVersion != null ? Optional.of(canonicalVersion) : Optional.empty(),
                pkgcanonical != null ? Optional.of(pkgcanonical) : Optional.empty(),
                fhirVersion != null ? Optional.of(fhirVersion) : Optional.empty(),
                prerelease != null ? Optional.of(Boolean.valueOf(prerelease)) : Optional.empty(),
                Optional.empty(),
                protectedPackage != null
                    ? Optional.of(Boolean.valueOf(protectedPackage))
                    : Optional.empty(),
                keywordsList != null ? Optional.of(keywordsList) : Optional.empty()));

    assertEquals(expectedPackageCount, baseInfos.size());
  }
}
