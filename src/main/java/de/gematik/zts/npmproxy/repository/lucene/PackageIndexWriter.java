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
import static de.gematik.zts.npmproxy.repository.lucene.fields.BaseFieldNames.SUFFIX_STRING;

import de.gematik.zts.npmproxy.exceptions.PackageIndexException;
import de.gematik.zts.npmproxy.model.FhirPackage;
import de.gematik.zts.npmproxy.model.FhirPackageVersionInfo;
import de.gematik.zts.npmproxy.model.NpmProxyLuceneDocument;
import de.gematik.zts.npmproxy.repository.lucene.fieldgenerators.FieldGenerator;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.hl7.fhir.r4.model.Resource;

@Slf4j
public class PackageIndexWriter implements AutoCloseable {

  private final IndexWriter writer;

  /**
   * Konstruktor für den IndexWriter
   *
   * @param index Directory (Lucene-Index)
   * @param openMode OpenMode für den IndexWriter
   */
  public PackageIndexWriter(Directory index, IndexWriterConfig.OpenMode openMode) {

    IndexWriterConfig indexWriterConfig = new IndexWriterConfig();
    indexWriterConfig.setMergeScheduler(new ConcurrentMergeScheduler());

    indexWriterConfig.setOpenMode(openMode);
    try {
      writer = new IndexWriter(index, indexWriterConfig);
    } catch (IOException e) {
      throw new PackageIndexException("Error while initializing index writer.", e);
    }
  }

  // ==============================================================================================

  public void updateDocument(String documentId, NpmProxyLuceneDocument npmProxyLuceneDocument) {
    try {
      writer.updateDocument(
          new Term(DOCUMENT_ID + SEPARATOR + SUFFIX_STRING, documentId),
          npmProxyLuceneDocument.getDocument());
    } catch (IOException e) {
      log.error("Error while updating document in IndexWriter: {}", e.getMessage(), e);
    }
  }

  /**
   * Adds a FHIR-Package to the Lucene index.
   *
   * @param fhirPackage FHIR-Package
   * @param fhirPackageVersionInfo Version information for the FHIR-Package
   */
  public void indexPackage(FhirPackage fhirPackage, FhirPackageVersionInfo fhirPackageVersionInfo) {

    log.info("Indexing package: {}", fhirPackage.getManifest().getName());
    // Anlegen des Lucene-Dokuments
    NpmProxyLuceneDocument npmProxyLuceneDocument = new NpmProxyLuceneDocument();

    // Erzeugen der Felder für geschützte Pakete
    npmProxyLuceneDocument.addStringFieldToDocument(
        PACKAGE_PROTECTED,
        String.valueOf(fhirPackageVersionInfo.getProtectedPackage()),
        Field.Store.YES);

    // Set package unlisted / deprecated status
    if (fhirPackageVersionInfo.getUnlisted() != null) {
      npmProxyLuceneDocument.addStringFieldToDocument(PACKAGE_UNLISTED, "true", Field.Store.YES);
    } else {
      npmProxyLuceneDocument.addStringFieldToDocument(PACKAGE_UNLISTED, "false", Field.Store.YES);
    }

    // if we have annotations, we might have additional keywords
    if (fhirPackageVersionInfo.getAnnotations() != null) {
      // additional keywords (may change from annotations)
      List<String> additionalKeywords =
          fhirPackageVersionInfo.getAnnotations().getAdditionalKeywords() != null
              ? fhirPackageVersionInfo.getAnnotations().getAdditionalKeywords()
              : Collections.emptyList();
      if (!additionalKeywords.isEmpty()) {
        additionalKeywords.forEach(
            keyword ->
                npmProxyLuceneDocument.addStringFieldToDocument(
                    PACKAGE_ADDITIONAL_KEYWORDS, keyword.toLowerCase(), Field.Store.YES));
      }
    }

    // create a random UUID for the document ID
    npmProxyLuceneDocument.addStringFieldToDocument(
        DOCUMENT_ID, UUID.randomUUID().toString(), Field.Store.YES);

    // Erzeugen der Felder auf Grundlage des übergebenen FHIR-Paketmanifests
    FieldGenerator.createFieldsFromPackageManifest(
        npmProxyLuceneDocument, fhirPackage.getManifest());

    for (Resource resource : fhirPackage.getResources()) {
      FieldGenerator.createFieldsFromResource(npmProxyLuceneDocument, resource);
    }

    // Dokument zum Index hinzufügen
    try {
      writer.addDocument(npmProxyLuceneDocument.getDocument());
    } catch (IOException e) {
      log.error("Error while adding document to IndexWriter: {}", e.getMessage(), e);
    }
  }

  // ==============================================================================================

  @Override
  public void close() {
    try {
      writer.close();
    } catch (IOException e) {
      log.error("Error while closing IndexReader: {}", e.getMessage(), e);
    }
  }
}
