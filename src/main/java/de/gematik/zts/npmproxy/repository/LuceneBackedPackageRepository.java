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
import static de.gematik.zts.npmproxy.tools.FhirPackageHelper.checkFhirVersion;

import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.exceptions.FhirVersionException;
import de.gematik.zts.npmproxy.exceptions.PackageIndexException;
import de.gematik.zts.npmproxy.model.*;
import de.gematik.zts.npmproxy.repository.lucene.CatalogSearchHelper;
import de.gematik.zts.npmproxy.repository.lucene.PackageIndex;
import de.gematik.zts.npmproxy.repository.lucene.PackageIndexWriter;
import de.gematik.zts.npmproxy.repository.lucene.querygenerators.CatalogParameterQueryGenerator;
import de.gematik.zts.npmproxy.tools.FileHelper;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class LuceneBackedPackageRepository {

  private final NpmProxyConfiguration properties;

  @Getter private Map<String, FhirPackageInfo> packageInfoIndex;
  private Map<String, Path> packagePathIndex;

  @Getter private boolean isInitialized = false;
  @Getter @Setter private boolean initialUpdateSucceeded = false;

  private IndexSearcher indexSearcher;

  @Autowired
  public LuceneBackedPackageRepository(NpmProxyConfiguration properties) {
    this.properties = properties;
    initialize();
  }

  /**
   * Generiert einen eindeutigen Identifier für ein Paket anhand des Paketnamens und der Version.
   * Diese Version benötigen wir, um einen einheitlichen Schlüssel für die Suche im Index zu
   * erhalten.
   *
   * @param packageName Name des Pakets
   * @param packageVersion Version des Pakets
   * @return Eindeutiger Identifier
   */
  public static String getPackageVersionIdentifier(String packageName, String packageVersion) {
    return packageName + "#" + packageVersion;
  }

  /**
   * Extrahiert die FHIR-Paketinformationen aus einem FHIR-Paketmanifest
   *
   * @param packageManifest FHIR-Paketmanifest
   * @return FHIR-Paketinformationen
   */
  public static FhirPackageVersionInfo extractFhirPackageVersionInfoFromPackageManifest(
      FhirPackageManifest packageManifest) throws FhirVersionException {
    FhirPackageVersionInfo fhirPackageVersionInfo = new FhirPackageVersionInfo();
    fhirPackageVersionInfo.setName(packageManifest.getName());
    fhirPackageVersionInfo.setVersion(packageManifest.getVersion());
    fhirPackageVersionInfo.setDescription(packageManifest.getDescription());
    String fhirVersion = checkFhirVersion(packageManifest.getFhirVersions());
    fhirPackageVersionInfo.setFhirVersion(fhirVersion);
    fhirPackageVersionInfo.setStaticKeywords(
        packageManifest.getKeywords() != null
            ? packageManifest.getKeywords().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet())
            : new HashSet<>());
    fhirPackageVersionInfo.setAuthor(new FhirPackageAuthor(packageManifest.getAuthor()));
    fhirPackageVersionInfo.setTitle(packageManifest.getTitle());
    fhirPackageVersionInfo.setAltTitle(packageManifest.getAltTitle());
    return fhirPackageVersionInfo;
  }

  /**
   * Initialisiert das Repository. Lädt alle .tar.gz-Dateien aus dem Verzeichnis und verarbeitet
   * diese, um sich einen Index aufzubauen.
   */
  private void initialize() {

    log.info(
        "Initialize Lucene-based FHIR-NPM-Registry with data from: {}",
        properties.getPackageCacheDir());

    // Vorbereiten des Repository-Index
    packageInfoIndex = new ConcurrentHashMap<>();
    packagePathIndex = new ConcurrentHashMap<>();

    try (PackageIndex packageIndex = new PackageIndex(properties.getPackageCacheDir())) {
      try (PackageIndexWriter ignored =
          packageIndex.createPackageModelIndexWriter(IndexWriterConfig.OpenMode.CREATE)) {
        // IndexWriter erstellen, damit wir einen Searcher erhalten können
        log.info("Index for packages created in directory: {}", properties.getPackageCacheDir());
      }

      indexSearcher = packageIndex.createSearcher();
    } catch (IOException e) {
      log.error("Error initializing PackageIndex: {}", e.getMessage(), e);
    }
    // Verzeichnis, in dem die .tar.gz/tgz-Dateien liegen
    Path directory = Paths.get(properties.getPackageCacheDir());

    indexPackagesInDirectory(directory);

    isInitialized = true;
  }

  /**
   * Searches for a package document in the index by its name and version. If found, it returns the
   * document; otherwise, it returns null.
   *
   * @param packageName Name of the package to search for
   * @param packageVersion Version of the package to search for
   * @return Document if found, otherwise null
   */
  Document getDocument(String packageName, String packageVersion) {
    // search the existing package document
    Query query =
        CatalogParameterQueryGenerator.prepareExactNameAndVersionQuery(packageName, packageVersion);
    try {
      TopDocs topDocs = indexSearcher.search(query, 10);

      if (topDocs.scoreDocs.length > 1) {
        log.warn(
            "Found more than one document for package: {} version: {}",
            packageName,
            packageVersion);
      }

      if (topDocs.scoreDocs.length > 0) {
        // Grab the first scoreDoc
        ScoreDoc scoreDoc = topDocs.scoreDocs[0];

        // Retrieve the stored fields of the document
        Document doc = indexSearcher.storedFields().document(scoreDoc.doc);

        // Log each field
        doc.getFields()
            .forEach(
                field -> log.debug("Field: {} - Value: {}", field.name(), field.stringValue()));

        return doc; // Return the first document
      }
      log.error("no doc found for package: {} version: {}", packageName, packageVersion);
      return null; // No results found

    } catch (IOException e) {
      throw new PackageIndexException(
          String.format("Error searching for package: %s#%s", packageName, packageVersion), e);
    }
  }

  /**
   * Updated the index of a package version. If annotations are provided, they will be taken into
   * account. Otherwise, only information from the FhirPackageVersionInfo will be used.
   *
   * @param fhirPackageVersionInfo Contains information about the package version
   * @param annotations Annotations, if any, to be added to the package version.
   */
  public void reIndexPackage(
      @NonNull FhirPackageVersionInfo fhirPackageVersionInfo,
      FhirPackageArtifactRegistryAnnotations annotations) {

    // search the existing package document

    Document document =
        getDocument(fhirPackageVersionInfo.getName(), fhirPackageVersionInfo.getVersion());
    if (document == null) {
      log.error(
          "no document found for package: {} version: {}",
          fhirPackageVersionInfo.getName(),
          fhirPackageVersionInfo.getVersion());
      return;
    }

    // create a new document from an existing one, either using the annotations if provided or the
    // FhirPackageVersionInfo
    NpmProxyLuceneDocument npmProxyLuceneDocument =
        annotations != null
            ? new NpmProxyLuceneDocument(document, annotations)
            : new NpmProxyLuceneDocument(document, fhirPackageVersionInfo);

    // update the index
    try (PackageIndex packageIndex = new PackageIndex(properties.getPackageCacheDir())) {
      // Erzeugen eines IndexWriters für die Lucene-Indexierung (CREATE-APPEND-Modus, wenn Index
      // existiert, wird er geöffnet, ansonsten neu erstellt)
      try (PackageIndexWriter packageIndexWriter =
          packageIndex.createPackageModelIndexWriter(IndexWriterConfig.OpenMode.CREATE_OR_APPEND)) {
        packageIndexWriter.updateDocument(
            document.getField(DOCUMENT_ID + SEPARATOR + SUFFIX_STRING).stringValue(),
            npmProxyLuceneDocument);
        log.info(
            "Document updated for package version: {}-{}",
            fhirPackageVersionInfo.getName(),
            fhirPackageVersionInfo.getVersion());
      }

      indexSearcher = packageIndex.createSearcher();

    } catch (IOException e) {
      log.error("Error updating PackageIndex: {}", e.getMessage(), e);
    }
  }

  private void indexPackage(
      FhirPackage fhirPackage, FhirPackageVersionInfo fhirPackageVersionInfo) {
    try (PackageIndex packageIndex = new PackageIndex(properties.getPackageCacheDir())) {
      // Erzeugen eines IndexWriters für die Lucene-Indexierung (CREATE-APPEND-Modus, wenn Index
      // existiert, wird er geöffnet, ansonsten neu erstellt)
      try (PackageIndexWriter packageIndexWriter =
          packageIndex.createPackageModelIndexWriter(IndexWriterConfig.OpenMode.CREATE_OR_APPEND)) {
        packageIndexWriter.indexPackage(fhirPackage, fhirPackageVersionInfo);
      }

      indexSearcher = packageIndex.createSearcher();

    } catch (IOException e) {
      log.error("Error initializing PackageIndex: {}", e.getMessage(), e);
    }
  }

  private void indexPackagesInDirectory(Path directory) {
    // Laden und Verarbeiten aller .tar.gz/tgz-Dateien im Verzeichnis
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.{tar.gz,tgz}")) {
      for (Path entry : stream) {
        // Gefundene Dateien werden verarbeitet (Paket-Informationen extrahieren + Index aufbauen)
        indexPackageFile(entry, null, null);
      }
    } catch (IOException e) {
      log.error(
          "Error occurred while iterating through cache directory and indexing resources: {}",
          e.getMessage(),
          e);
    }
  }

  /**
   * Verarbeitet eine einzelne .tar.gz/.tgz-Datei und extrahiert die Informationen aus der
   * package.json-Datei. Mit den entsprechenden Angaben wird der Index der Registry aufgebaut.
   *
   * @param packageFilePath Pfad zur .tar.gz/.tgz-Datei
   */
  public boolean indexPackageFile(
      Path packageFilePath,
      FhirPackageVersionInfo fhirPackageVersionInfo,
      FhirPackageArtifactRegistryAnnotations annotations) {

    // Extrahieren der Paketinformationen aus der package.json-Datei
    Optional<FhirPackage> fhirPackage = FhirPackage.buildFromTarGz(packageFilePath);

    // Sollten wir kein Paketmanifest finden, loggen wir einen Fehler und brechen die weitere
    // Verarbeitung ab
    if (fhirPackage.isEmpty()) {
      log.error("Error occurred while loading package from: {}", packageFilePath);
      return false;
    }

    // Umwandeln des FHIR-Paketmanifests in FHIR-Paketinformationen
    FhirPackageVersionInfo packageVersionInfo =
        extractFhirPackageVersionInfoFromPackageManifest(fhirPackage.get().getManifest());

    // store the annotations in the PackageVersionInfo
    packageVersionInfo.setAnnotations(annotations);

    // check if the package is protected or not
    boolean isProtectedPackage =
        properties.isGCloudAnnotationProcessingEnabled() && annotations != null
            ? annotations.getProtectedDownload()
            : properties.getProtectedPackages().contains(packageVersionInfo.getName());

    // set the protected state in the package version info
    packageVersionInfo.setProtectedPackage(isProtectedPackage);

    // set deprecation message if available and no annotations present
    if (annotations == null
        && fhirPackageVersionInfo != null
        && fhirPackageVersionInfo.getDeprecated() != null) {
      packageVersionInfo.setUnlisted(fhirPackageVersionInfo.getDeprecated());
    }

    try {
      // Berechnen des Hash-Wertes der Datei, da dieser für die Dist-Informationen benötigt wird
      FhirPackageVersionDistInfo dist = new FhirPackageVersionDistInfo();
      dist.setShasum(FileHelper.calculateFileHash(packageFilePath));

      // Generieren der logischen Tarball-URL. Diese Struktur findet sich auch im
      // FhirNpmController wider
      dist.setTarball(
          properties.getHostName()
              + properties.getNpmPath()
              + "/"
              + packageVersionInfo.getName()
              + "/"
              + packageVersionInfo.getVersion());

      packageVersionInfo.setDist(dist);

    } catch (Exception e) {
      log.error("Error occurred while preparing dist-Infos: {}", e.getMessage(), e);

      // hier müssen wir den Indexing-Prozess abbrechen, da es anscheinend größere Probleme mit
      // der Datei gibt
      return false;
    }

    // Indizieren des Pakets für die spätere Suche
    indexPackage(fhirPackage.get(), packageVersionInfo);

    // Befüllen der Lookup-Tabelle, um vom Paketnamen und der Version auf den Pfad der Datei zu
    // kommen. Diese
    // Information benötigen wir später für den Datenabruf
    packagePathIndex.put(
        getPackageVersionIdentifier(packageVersionInfo.getName(), packageVersionInfo.getVersion()),
        packageFilePath);

    String packageName = packageVersionInfo.getName();

    FhirPackageInfo packageInfo =
        packageInfoIndex.computeIfAbsent(
            packageName,
            k -> {
              var fhirPackageInfo = new FhirPackageInfo();
              fhirPackageInfo.setId(packageName);
              fhirPackageInfo.setName(packageName);
              return fhirPackageInfo;
            });

    ConcurrentHashMap<String, FhirPackageVersionInfo> versions = packageInfo.getVersions();
    if (versions == null) {
      // Falls die Versions-Map noch nicht existiert, wird sie erstellt und der PackageInfo
      // zugeordnet
      versions = new ConcurrentHashMap<>();
      packageInfo.setVersions(versions);
    }
    // Hinzufügen der Version zum Versions-Index
    versions.put(packageVersionInfo.getVersion(), packageVersionInfo);

    // Aktualisieren der Dist-Tags (hier: latest)
    packageInfo.setDistTags(packageInfo.getLatestDistTags());
    // Aktualisieren der Beschreibung (hier: latest)
    packageInfo.setDescription(packageInfo.getLatestDescription());

    return true;
  }

  /**
   * Liefert anhand eines Paketnamens die zugehörigen Paketinformationen
   *
   * @param packageName Name des Pakets
   * @return Paketinformationen
   */
  public Optional<FhirPackageInfo> findPackageByName(String packageName) {
    return packageInfoIndex.get(packageName) != null
        ? Optional.of(packageInfoIndex.get(packageName))
        : Optional.empty();
  }

  /**
   * Liefert anhand eines Paketnamens die zugehörigen Paketinformationen
   *
   * @param packageName Name of the package
   * @param packageVersion Version of the package
   * @return an Optional of FhirPackageVersionInfo if version is found, otherwise Optional.empty()
   */
  public Optional<FhirPackageVersionInfo> findPackageVersion(
      String packageName, String packageVersion) {
    var packageInfo = packageInfoIndex.get(packageName);
    if (packageInfo == null) {
      return Optional.empty();
    } else {
      return Optional.ofNullable(packageInfo.getVersions().get(packageVersion));
    }
  }

  /**
   * Updates the version information of a package version. If the unlisted status is different from
   * the remote version info, it will be updated.
   *
   * @param remoteVersionInfo The remote version info to update
   * @return true if the version info was updated, false otherwise
   */
  public boolean updateVersionInfo(FhirPackageVersionInfo remoteVersionInfo) {

    var localFhirPackageVersionInfo =
        findPackageVersion(remoteVersionInfo.getName(), remoteVersionInfo.getVersion());

    if (localFhirPackageVersionInfo.isPresent()
        && !Objects.equals(
            localFhirPackageVersionInfo.get().getUnlisted(), remoteVersionInfo.getDeprecated())) {
      // update deprecation / unlisted status message
      localFhirPackageVersionInfo.get().setUnlisted(remoteVersionInfo.getDeprecated());
      return true;
    }
    return false;
  }

  /**
   * Updates the information of a package version. If the annotations are not present, they will be
   * added. If the annotations are present, they will be updated, if they are different from the
   * existing ones.
   *
   * @param packageName Name of the package
   * @param packageVersion Version of the package
   * @param annotations Annotations to be added or updated
   * @return true if the annotations were added or updated, false if the annotations are already
   *     present and not change
   */
  public boolean updateVersionInfo(
      String packageName,
      String packageVersion,
      @NonNull FhirPackageArtifactRegistryAnnotations annotations) {

    var localFhirPackageVersionInfo = findPackageVersion(packageName, packageVersion);

    if (localFhirPackageVersionInfo.isPresent()) {

      // annotations are present, but no change in annotations
      if (localFhirPackageVersionInfo.get().getAnnotations() != null
          && localFhirPackageVersionInfo.get().getAnnotations().equals(annotations)) {
        return false;
      }

      // current annotations are different or not present -> update the annotations, protected
      // state, download conditions and keywords
      localFhirPackageVersionInfo.get().setAnnotations(annotations);
      return true;
    }
    return false;
  }

  /**
   * Checks if a package version is protected. If gcloud is disabled, it checks if the package name
   * is in the list of protected packages from properties. If gcloud is enabled, it checks if the
   * package version is protected. If the package version is omitted, it checks if any package
   * version is protected.
   *
   * @param packageName - Name of the package
   * @param packageVersion - Version of the package
   * @return true if the package version is protected, false otherwise
   */
  public boolean isPackageVersionProtected(String packageName, String packageVersion) {
    // if gcloud is disabled, just check if the package name is in the list of protected packages
    if (!properties.isGCloudAnnotationProcessingEnabled()) {
      return properties.getProtectedPackages().contains(packageName);
    }
    // gcloud is enabled
    // get the package
    FhirPackageInfo packageInfo = packageInfoIndex.get(packageName);
    if (packageInfo != null) {

      // if packageVersion is omitted, check if any package version is protected
      if (packageVersion == null) {
        for (FhirPackageVersionInfo versionInfo : packageInfo.getVersions().values()) {
          if (versionInfo.getProtectedPackage()) {
            return true;
          }
        }
        return false;
      }

      // get the version info
      FhirPackageVersionInfo versionInfo = packageInfo.getVersions().get(packageVersion);
      if (versionInfo != null) {
        // return the information if package is protected
        return versionInfo.getProtectedPackage();
      }
    }
    return false;
  }

  /**
   * Liefert den Pfad zur .tar.gz/.tgz-Datei für eine konkrete Paketversion
   *
   * @param packageName Name des Pakets
   * @param packageVersion Version des Pakets
   * @return Pfad zur .tar.gz/.tgz-Datei
   */
  public Optional<Path> getPackagePathIndex(String packageName, String packageVersion) {
    String packageVersionIdentifier = getPackageVersionIdentifier(packageName, packageVersion);
    return packagePathIndex.get(packageVersionIdentifier) != null
        ? Optional.of(packagePathIndex.get(packageVersionIdentifier))
        : Optional.empty();
  }

  /**
   * Liefert die Anzahl der im Repository enthaltenen Pakete
   *
   * @return Anzahl der Pakete
   */
  public int getPackageCount() {
    return packageInfoIndex.size();
  }

  // --------------------------------------------------------------------------------
  // Lucene Query-Methoden

  public List<FhirPackageBaseInfo> searchPackages(SearchPackageParameters searchPackageParameters) {

    // Generieren der Lucene-Query
    Query query = CatalogParameterQueryGenerator.prepareQuery(searchPackageParameters);

    // returns a map with the package names and their keywords
    // different keywords
    var resultSetMap =
        CatalogSearchHelper.processQueryAndReturnPackageNameList(query, indexSearcher);

    ArrayList<FhirPackageBaseInfo> resultList = new ArrayList<>();
    // iterate over package names
    for (var entries : resultSetMap.entrySet()) {
      // get the corresponding package by name from the packageInfoIndex
      Optional<FhirPackageInfo> packageInfo = findPackageByName(entries.getKey());
      FhirPackageBaseInfo fhirPackageBaseInfo = new FhirPackageBaseInfo();
      if (packageInfo.isPresent()) {
        fhirPackageBaseInfo.setName(packageInfo.get().getName());

        // get the information for the latest version
        String latestVersion = packageInfo.get().getLatestDistTags().get("latest");
        FhirPackageVersionInfo latestPackageVersionInfo =
            packageInfo.get().getVersions().get(latestVersion);
        fhirPackageBaseInfo.setDescription(latestPackageVersionInfo.getDescription());
        fhirPackageBaseInfo.setFhirVersion(latestPackageVersionInfo.getFhirVersion());

        // put the keywords here from search result
        LuceneFhirPackageSearchResult searchResult = entries.getValue();
        if (searchResult != null && !searchResult.getKeywords().isEmpty()) {
          fhirPackageBaseInfo.setKeywords(searchResult.getKeywords());
        }

        // set the package versions that match the search criteria
        if (searchResult != null && !searchResult.getPackageVersions().isEmpty()) {
          fhirPackageBaseInfo.setPackageVersions(searchResult.getPackageVersions());
        }

        resultList.add(fhirPackageBaseInfo);
      }
    }

    return resultList;
  }

  /**
   * Returns a list of package version infos based on the provided filters for dynamic feed
   * creation.
   *
   * @param publisher The publisher to filter by (optional)
   * @param packageName The package name to filter by (optional)
   * @param keyword A keyword to filter by (optional)
   * @param publishToHl7 Filter for packages that are published / not published to HL7 FHIR package
   *     registry
   * @return A list of FhirPackageVersionInfo matching the filters
   */
  public List<FhirPackageVersionInfo> getPackageVersionInfos(
      String publisher, String packageName, String keyword, Boolean publishToHl7) {

    return packageInfoIndex.values().stream()
        .flatMap(packageInfo -> packageInfo.getVersions().values().stream())
        // Filter out non-visible versions and log protected ones that should not be published
        .filter(
            versionInfo -> {
              // Skip versions that aren't visible
              if (versionInfo.getAnnotations() == null
                  || !versionInfo.getAnnotations().getVisibility()) {
                log.warn("Skipping non-visible package version: {}", versionInfo.getName());
                return false;
              }
              // Skip protected packages that are set to be published to HL7
              if (publishToHl7 != null
                  && publishToHl7.equals(true)
                  && versionInfo.getPublishToHl7()
                  && versionInfo.getProtectedPackage()) {
                log.warn(
                    "Skipping protected package version {} that is set to be published to HL7",
                    versionInfo.getName());
                return false;
              }
              return true;
            })
        // Check each filter condition
        .filter(
            versionInfo ->
                publisher == null || versionInfo.getAuthor().getName().equalsIgnoreCase(publisher))
        .filter(
            versionInfo ->
                packageName == null || versionInfo.getName().equalsIgnoreCase(packageName))
        .filter(
            versionInfo ->
                keyword == null || versionInfo.getKeywords().contains(keyword.toLowerCase()))
        // Match publishToHl7 value
        .filter(
            versionInfo -> publishToHl7 == null || versionInfo.getPublishToHl7() == publishToHl7)
        .toList();
  }
}
