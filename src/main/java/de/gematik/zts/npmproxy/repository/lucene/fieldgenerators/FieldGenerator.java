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

import static de.gematik.zts.npmproxy.repository.lucene.fields.BaseFieldNames.CANONICAL;
import static de.gematik.zts.npmproxy.repository.lucene.fields.BaseFieldNames.CANONICAL_VERSION;

import de.gematik.zts.npmproxy.model.FhirPackageManifest;
import de.gematik.zts.npmproxy.model.NpmProxyLuceneDocument;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Field;
import org.hl7.fhir.r4.model.*;

@Slf4j
public class FieldGenerator {

  private static final String IDENTIFIER_ALT = "urn:ietf:rfc:3986";

  private FieldGenerator() {}

  /**
   * Creates fields for the given Resource. The field generation is primarily based on the search
   * parameters defined in the specification (canonical). Additionally, it creates fields for the
   * optional canonical URL found in identifier.
   *
   * @param document Lucene document
   * @param resource FHIR Resource
   */
  public static void createFieldsFromResource(NpmProxyLuceneDocument document, Resource resource) {
    if (!(resource instanceof MetadataResource metadataResource)) {
      throw new IllegalArgumentException(
          String.format(
              "Resource is not a MetadataResource, cannot create Resource fields: %s",
              resource.getResourceType()));
    }

    // Check for supported resource types
    if (resource.getResourceType() == ResourceType.CodeSystem
        || resource.getResourceType() == ResourceType.ValueSet
        || resource.getResourceType() == ResourceType.ConceptMap) {

      // create the canonical field for url and alternative canonicals
      if (metadataResource.hasUrl()) {
        createCanonicalFields(document, metadataResource.getUrl());
      }
      List<String> alternativeCanonicals = getAlternativeCanonicals(metadataResource);
      for (String canonical : alternativeCanonicals) {
        if (StringUtils.isNotBlank(canonical)) {
          createCanonicalFields(document, canonical);
        }
      }
      // create canonical version fields
      createCanonicalVersionFields(document, metadataResource);
    } else {
      throw new IllegalArgumentException(
          String.format(
              "resource type %s is not supported for field generation",
              resource.getResourceType()));
    }
  }

  private static List<String> getAlternativeCanonicals(MetadataResource metadataResource) {
    switch (metadataResource.getResourceType()) {
      case CodeSystem:
        CodeSystem codeSystem = ((CodeSystem) metadataResource);
        if (codeSystem.hasIdentifier()) {
          return codeSystem.getIdentifier().stream()
              .filter(id -> id.hasSystem() && id.hasValue())
              .filter(id -> IDENTIFIER_ALT.equals(id.getSystem()))
              .map(Identifier::getValue)
              .toList();
        }
        break;
      case ValueSet:
        ValueSet valueSet = ((ValueSet) metadataResource);
        if (valueSet.hasIdentifier()) {
          return valueSet.getIdentifier().stream()
              .filter(id -> id.hasSystem() && id.hasValue())
              .filter(id -> IDENTIFIER_ALT.equals(id.getSystem()))
              .map(Identifier::getValue)
              .toList();
        }
        break;
      case ConceptMap:
        ConceptMap conceptMap = ((ConceptMap) metadataResource);
        if (conceptMap.hasIdentifier()
            && conceptMap.getIdentifier().hasSystem()
            && conceptMap.getIdentifier().hasValue()) {
          return conceptMap.getIdentifier().getSystem().equals(IDENTIFIER_ALT)
              ? List.of(((ConceptMap) metadataResource).getIdentifier().getValue())
              : Collections.emptyList();
        }
        break;
      default:
        return List.of();
    }
    return Collections.emptyList();
  }

  /**
   * Creates canonical version fields for the given MetadataResource.
   *
   * @param document Lucene-Dokument
   * @param metadataResource Metadata-Ressource
   */
  private static void createCanonicalVersionFields(
      NpmProxyLuceneDocument document, MetadataResource metadataResource) {

    if (metadataResource.hasVersion()) {
      document.addStringFieldToDocument(
          CANONICAL_VERSION, metadataResource.getVersion(), Field.Store.YES);
    }
  }

  /**
   * Creates canonical fields for the given canonical URL.
   *
   * @param document Lucene document
   * @param canonical Canonical URL
   */
  public static void createCanonicalFields(NpmProxyLuceneDocument document, String canonical) {
    String canonicalLowerCase = canonical.toLowerCase();
    document.addStringFieldToDocument(CANONICAL, canonicalLowerCase, Field.Store.YES);

    if (canonicalLowerCase.startsWith("urn:oid:")) {
      handleOidCanonical(document, canonicalLowerCase);
    } else {
      handleUrlCanonical(document, canonicalLowerCase);
    }
  }

  /**
   * Helper method to handle OID canonical URLs.
   *
   * @param document Lucene document
   * @param canonicalLowerCase Canonical in lower case
   */
  private static void handleOidCanonical(
      NpmProxyLuceneDocument document, String canonicalLowerCase) {
    String oidPart = canonicalLowerCase.substring("urn:oid:".length());
    for (String sub : oidPart.split("\\.")) {
      if (!sub.isBlank()) {
        document.addStringFieldToDocument(CANONICAL, sub, Field.Store.YES);
      }
    }
  }

  /**
   * Helper method to handle URL canonical URLs.
   *
   * @param document Lucene document
   * @param canonicalLowerCase Canonical in lower case
   */
  private static void handleUrlCanonical(
      NpmProxyLuceneDocument document, String canonicalLowerCase) {
    for (String canonicalPart : canonicalLowerCase.split("/")) {
      if (canonicalPart.isBlank()) {
        continue;
      }
      document.addStringFieldToDocument(CANONICAL, canonicalPart, Field.Store.YES);
      addDotSplits(document, canonicalPart);
    }
  }

  /**
   * Adds dot-separated parts of the canonical URL to the document.
   *
   * @param document Lucene document
   * @param canonicalPart Canonical part to split by dots
   */
  private static void addDotSplits(NpmProxyLuceneDocument document, String canonicalPart) {
    if (!canonicalPart.contains(".")) {
      return;
    }
    for (String subPart : canonicalPart.split("\\.")) {
      if (!subPart.isBlank()) {
        document.addStringFieldToDocument(CANONICAL, subPart, Field.Store.YES);
      }
    }
  }

  /**
   * Creates fields for the given FhirPackageManifest.
   *
   * @param document Lucene document
   * @param packageManifest FhirPackageManifest
   */
  public static void createFieldsFromPackageManifest(
      NpmProxyLuceneDocument document, FhirPackageManifest packageManifest) {
    PackageManifestFieldGenerator.createFields(document, packageManifest);
  }
}
