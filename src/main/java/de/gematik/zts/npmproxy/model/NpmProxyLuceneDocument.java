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

import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;

@Getter
@Slf4j
public class NpmProxyLuceneDocument {
  private static final List<String> MANDATORY_FIELDS =
      List.of(
          DOCUMENT_ID,
          PACKAGE_NAME,
          PACKAGE_VERSION,
          PACKAGE_ISPRERELEASE,
          PACKAGE_FHIRVERSION,
          CANONICAL);

  private static final List<String> OPTIONAL_FIELDS =
      List.of(PACKAGE_KEYWORDS, PACKAGE_CANONICAL, CANONICAL_VERSION);

  private final Document document;

  public NpmProxyLuceneDocument() {
    this.document = new Document();
  }

  public NpmProxyLuceneDocument(
      @NonNull Document existingDocument, @NonNull FhirPackageVersionInfo fhirPackageVersionInfo) {

    this.document = new Document();

    // pre filter
    addStringFieldToDocument(CONTENT_TYPE, CONTENT_TYPE_PACKAGE, Field.Store.YES);

    // copy over mandatory and optional fields
    copyFieldsFromExistingDocument(existingDocument);

    // update the unlisted status based on the FhirPackageVersionInfo
    if (fhirPackageVersionInfo.getDeprecated() != null) {
      addStringFieldToDocument(PACKAGE_UNLISTED, String.valueOf(true), Field.Store.YES);
    } else {
      addStringFieldToDocument(PACKAGE_UNLISTED, String.valueOf(false), Field.Store.YES);
    }
  }

  public NpmProxyLuceneDocument(
      @NonNull Document existingDocument,
      @NonNull FhirPackageArtifactRegistryAnnotations annotations) {

    this.document = new Document();

    // pre filter
    addStringFieldToDocument(CONTENT_TYPE, CONTENT_TYPE_PACKAGE, Field.Store.YES);

    // copy over mandatory and optional fields
    copyFieldsFromExistingDocument(existingDocument);

    // additional keywords (may change from annotations)
    List<String> additionalKeywords =
        annotations.getAdditionalKeywords() != null
            ? annotations.getAdditionalKeywords()
            : Collections.emptyList();
    if (!additionalKeywords.isEmpty()) {
      additionalKeywords.forEach(
          keyword ->
              addStringFieldToDocument(
                  PACKAGE_ADDITIONAL_KEYWORDS, keyword.toLowerCase(), Field.Store.YES));
    }

    // protected (may change from annotations)
    var protectedDownload = annotations.getProtectedDownload();
    if (protectedDownload != null) {
      addStringFieldToDocument(
          PACKAGE_PROTECTED, String.valueOf(protectedDownload), Field.Store.YES);
    }

    // status of the package (may change from annotations)
    var packageStatus = annotations.getStatus();
    if (packageStatus == FhirPackageArtifactRegistryAnnotations.Status.DEPRECATED) {
      addStringFieldToDocument(PACKAGE_UNLISTED, String.valueOf(true), Field.Store.YES);
    } else {
      addStringFieldToDocument(PACKAGE_UNLISTED, String.valueOf(false), Field.Store.YES);
    }
  }

  private void copyFieldsFromExistingDocument(Document existingDocument) {
    // copy mandatory fixed fields
    for (String mandatoryField : MANDATORY_FIELDS) {
      var mandatoryFields = existingDocument.getFields(mandatoryField + SEPARATOR + SUFFIX_STRING);
      if (mandatoryFields == null || mandatoryFields.length == 0) {
        throw new IllegalArgumentException(mandatoryField + " must not be null or empty");
      }
      var documentFields = existingDocument.getFields(mandatoryField + SEPARATOR + SUFFIX_STRING);

      for (IndexableField field : documentFields) {
        var fieldValue = field.stringValue();
        addStringFieldToDocument(mandatoryField, fieldValue, Field.Store.YES);
      }
    }

    // copy optional fixed fields
    for (String optionalField : OPTIONAL_FIELDS) {
      var optionalFields = existingDocument.getFields(optionalField + SEPARATOR + SUFFIX_STRING);
      if (optionalFields != null) {
        for (IndexableField field : optionalFields) {
          var fieldValue = field.stringValue();
          addStringFieldToDocument(optionalField, fieldValue, Field.Store.YES);
        }
      }
    }
  }

  // Erzeugt Felder, die komplette Ressourcen enthalten
  public void addStoredFieldToDocument(String resourceName, byte[] resource) {
    document.add(new StoredField(resourceName + SEPARATOR + SUFFIX_CONTENT, resource));
  }

  public void addStringFieldToDocument(
      String paramName, @NonNull String paramValue, Field.Store fieldStore) {
    // Feld für exakte Suche
    document.add(new StringField(paramName + SEPARATOR + SUFFIX_STRING, paramValue, fieldStore));
  }

  public void addSortStringFieldToDocument(String paramName, String paramValue) {
    if (paramValue == null) {
      return;
    }
    // Feld für Sortierung
    document.add(
        new SortedDocValuesField(
            paramName + SEPARATOR + SUFFIX_STRING_SORT, new BytesRef(paramValue)));
  }

  public IndexableField[] getFields(String fieldName) {
    return document.getFields(fieldName);
  }

  public IndexableField getField(String fieldName) {
    return document.getField(fieldName);
  }
}
