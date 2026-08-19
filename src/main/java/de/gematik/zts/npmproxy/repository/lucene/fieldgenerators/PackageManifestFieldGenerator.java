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

import de.gematik.zts.npmproxy.exceptions.PackageIndexException;
import de.gematik.zts.npmproxy.model.FhirPackageManifest;
import de.gematik.zts.npmproxy.model.NpmProxyLuceneDocument;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Field;
import org.semver4j.Semver;

@Slf4j
public class PackageManifestFieldGenerator {

  private PackageManifestFieldGenerator() {}

  /**
   * Erzeugt die Felder für das PackageManifest. Die Erzeugung der Felder orientiert sich primär an
   * den in der Spezifikation definierten Suchparametern:
   * (https://app.swaggerhub.com/apis-docs/firely/Simplifier.net_FHIR_Package_API/1.0.1#/default/get_catalog)
   *
   * <p>- name (Search by (part of) a package name) - version (Search for packages with a version
   * containing this term) - canonical (Search for packages or resource contained in it with this
   * term in their canonical) - pkgcanonical (Search for packages with this exact canonical) -
   * fhirVersion (Limit search by FHIR version - Available values : R2, R3, R4, R4B, R5) -
   * prerelease (Whether to include or exclude prerelease package versions)
   *
   * @param document Lucene-Dokument
   * @param packageManifest FhirPackageManifest
   */
  public static void createFields(
      NpmProxyLuceneDocument document, FhirPackageManifest packageManifest) {

    // Für den Vorfilter setzen wir den Content-Type auf "package"
    document.addStringFieldToDocument(CONTENT_TYPE, CONTENT_TYPE_PACKAGE, Field.Store.YES);

    createNameFields(document, packageManifest.getName());
    createVersionFields(document, packageManifest.getVersion());
    // packages or resource contained in it with this term in their canonical
    createCanonicalFields(document, packageManifest.getCanonical());
    createPackageCanonicalField(document, packageManifest.getCanonical());
    createFhirVersionField(document, packageManifest.getFhirVersions());
    createKeywordsField(document, packageManifest.getKeywords());
  }

  /**
   * Erzeugt Felder, die die Suche nach dem Paketnamen oder dessen Bestandteile ermöglichen
   *
   * @param document Lucene-Dokument, zu dem die Felder hinzugefügt werden
   * @param packageName Paketname
   */
  private static void createNameFields(NpmProxyLuceneDocument document, String packageName) {
    if (StringUtils.isNoneEmpty(packageName)) {
      // Vollständigen Paketnamen für die exakte Suche indizieren und speichern (wir benötigen eine
      // gespeicherte Version des Feldes, um später zu ermitteln, welches Dokument zu welchem Paket
      // gehört)
      document.addStringFieldToDocument(PACKAGE_NAME, packageName.toLowerCase(), Field.Store.YES);

      // Vollständigen Paketnamen für die Sortierung indizieren
      document.addSortStringFieldToDocument(PACKAGE_NAME, packageName.toLowerCase());

      // Paketnamen in einzelne Teile zerlegen und für eine partielle Suche indizieren
      for (String namePart : packageName.toLowerCase().split("\\.")) {
        document.addStringFieldToDocument(PACKAGE_NAME, namePart, Field.Store.NO);
      }
    }
  }

  /**
   * Erzeugt Felder, die die Suche nach der Paketversion ermöglichen
   *
   * @param document Lucene-Dokument, zu dem die Felder hinzugefügt werden
   * @param packageVersion Paketversion
   */
  private static void createVersionFields(NpmProxyLuceneDocument document, String packageVersion) {
    if (StringUtils.isNoneEmpty(packageVersion)) {
      // vollständige Versionsnummer indizieren und Wert speichern
      document.addStringFieldToDocument(
          PACKAGE_VERSION, packageVersion.toLowerCase(), Field.Store.YES);

      Semver semver = new Semver(packageVersion.toLowerCase());

      // Entscheidung treffen, ob es sich um eine Prerelease-Version handelt oder nicht. Dies ist
      // essenziell für die Suche nach Prerelease-Versionen!
      if (!semver.getPreRelease().isEmpty()) {
        document.addStringFieldToDocument(PACKAGE_ISPRERELEASE, "true", Field.Store.YES);
      } else {
        document.addStringFieldToDocument(PACKAGE_ISPRERELEASE, "false", Field.Store.YES);
      }
    }
  }

  /**
   * Erzeugt Felder, die die Suche nach dem Canonical ermöglichen
   *
   * @param document Lucene-Dokument, zu dem die Felder hinzugefügt werden
   * @param canonical Canonical-Url des Pakets
   */
  private static void createCanonicalFields(NpmProxyLuceneDocument document, String canonical) {
    if (StringUtils.isNoneEmpty(canonical)) {
      // Canonical-Url des Pakets indizieren und Wert speichern
      document.addStringFieldToDocument(CANONICAL, canonical.toLowerCase(), Field.Store.YES);

      // Canonical-Url in Teile, nach den gesucht werden kann, zerlegen
      for (String canonicalPart : canonical.toLowerCase().split("/")) {
        if (StringUtils.isNoneEmpty(canonicalPart)) {
          document.addStringFieldToDocument(CANONICAL, canonicalPart, Field.Store.NO);
          if (canonicalPart.contains(".")) {
            for (String subPart : canonicalPart.split("\\.")) {
              document.addStringFieldToDocument(CANONICAL, subPart, Field.Store.NO);
            }
          }
        }
      }
    }
  }

  /**
   * Erzeugt ein Feld, das die exakte Suche nach dem Package Canonical ermöglicht
   *
   * @param document Lucene-Dokument, zu dem die Felder hinzugefügt werden
   * @param pkgCanonical Canonical-Url des Pakets
   */
  private static void createPackageCanonicalField(
      NpmProxyLuceneDocument document, String pkgCanonical) {
    if (StringUtils.isNoneEmpty(pkgCanonical)) {
      // Canonical-Url des Pakets indizieren und Wert speichern
      document.addStringFieldToDocument(PACKAGE_CANONICAL, pkgCanonical, Field.Store.YES);
    }
  }

  /**
   * Erzeugt Felder, die die Suche nach der FHIR-Version ermöglichen. Hinweis: Derzeit wird nur die
   * FHIR-Version 4.0.1 unterstützt. Sollten wir über ein Paket stolpern, das eine andere
   * FHIR-Version angibt, wird eine Exception geworfen.
   *
   * @param document Lucene-Dokument, zu dem die Felder hinzugefügt werden
   * @param fhirVersionList FHIR-Versionen
   */
  private static void createFhirVersionField(
      NpmProxyLuceneDocument document, List<String> fhirVersionList) {
    if (fhirVersionList != null && !fhirVersionList.isEmpty()) {
      for (String fhirVersion : fhirVersionList) {
        if (StringUtils.isNoneEmpty(fhirVersion)) {
          if (fhirVersion.equals("4.0.1")) {
            document.addStringFieldToDocument(PACKAGE_FHIRVERSION, "R4", Field.Store.YES);
          } else {
            log.warn("Unsupported FHIR-Version: {}", fhirVersion);
            throw new PackageIndexException("Unsupported FHIR-Version: " + fhirVersion);
          }
        }
      }
    }
  }

  /**
   * Erzeugt Felder, die die Suche nach Keywords ermöglichen
   *
   * @param document Lucene-Dokument, zu dem die Felder hinzugefügt werden
   * @param keywords Keywords aus dem PackageManifest
   */
  private static void createKeywordsField(NpmProxyLuceneDocument document, List<String> keywords) {

    if (keywords != null && !keywords.isEmpty()) {
      for (String keyword : keywords) {
        if (StringUtils.isNoneEmpty(keyword)) {
          document.addStringFieldToDocument(
              PACKAGE_KEYWORDS, keyword.toLowerCase(), Field.Store.YES);
        }
      }
    }
  }
}
