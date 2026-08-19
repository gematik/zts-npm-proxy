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

import de.gematik.zts.npmproxy.exceptions.PackageIndexException;
import de.gematik.zts.npmproxy.model.LuceneFhirPackageSearchResult;
import java.io.IOException;
import java.util.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.*;

@Slf4j
public class CatalogSearchHelper {

  private CatalogSearchHelper() {
    // hide constructor
  }

  public static Map<String, LuceneFhirPackageSearchResult> processQueryAndReturnPackageNameList(
      Query query, IndexSearcher searcher) throws PackageIndexException {

    try {
      LinkedHashMap<String, LuceneFhirPackageSearchResult> resultHashMap = new LinkedHashMap<>();
      int hitsPerPage = 100000;
      TopDocs topDocs = searcher.search(query, hitsPerPage);

      for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
        processScoreDoc(scoreDoc, searcher, resultHashMap);
      }

      return resultHashMap;

    } catch (IOException e) {
      throw new PackageIndexException(e);
    }
  }

  private static void processScoreDoc(
      ScoreDoc scoreDoc,
      IndexSearcher searcher,
      LinkedHashMap<String, LuceneFhirPackageSearchResult> resultSet)
      throws PackageIndexException {
    try {
      // Load the document for the current scoreDoc
      Document doc = searcher.storedFields().document(scoreDoc.doc);
      // Extract package name and version from the document
      String packageName = getFieldValue(doc, PACKAGE_NAME + SEPARATOR + SUFFIX_STRING);
      String packageVersion = getFieldValue(doc, PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING);

      // if we already have a result for this package name, we just update the result object
      if (resultSet.containsKey(packageName)) {
        updateExistingResult(resultSet.get(packageName), doc, packageVersion);
      } else {
        // Create a new result object for this packageName
        LuceneFhirPackageSearchResult newResult = createNewResult(doc, packageVersion);
        resultSet.put(packageName, newResult);
      }

      log.debug("score: {} package: {}-{}", scoreDoc.score, packageName, packageVersion);
    } catch (Exception e) {
      throw new PackageIndexException(e);
    }
  }

  private static String getFieldValue(Document doc, String fieldName) {
    IndexableField field = doc.getField(fieldName);
    return (field != null) ? field.stringValue() : null;
  }

  private static void updateExistingResult(
          @NonNull LuceneFhirPackageSearchResult existingResult, Document doc, String packageVersion) {

    // Add new package version
    existingResult.getPackageVersions().add(packageVersion);
    existingResult.getPackageVersions().sort(String.CASE_INSENSITIVE_ORDER);

    // Update keywords
    var currentKeywords = existingResult.getKeywords();
    addKeywordsFromFields(
        currentKeywords, doc.getFields(PACKAGE_KEYWORDS + SEPARATOR + SUFFIX_STRING));
    addKeywordsFromFields(
        currentKeywords, doc.getFields(PACKAGE_ADDITIONAL_KEYWORDS + SEPARATOR + SUFFIX_STRING));
    currentKeywords.sort(String.CASE_INSENSITIVE_ORDER);
  }

  private static LuceneFhirPackageSearchResult createNewResult(
      Document doc, String packageVersion) {
    LuceneFhirPackageSearchResult result = new LuceneFhirPackageSearchResult();

    // Package versions
    result.getPackageVersions().add(packageVersion);

    // Collect keywords
    var keywordList = new ArrayList<String>();
    addKeywordsFromFields(keywordList, doc.getFields(PACKAGE_KEYWORDS + SEPARATOR + SUFFIX_STRING));
    addKeywordsFromFields(
        keywordList, doc.getFields(PACKAGE_ADDITIONAL_KEYWORDS + SEPARATOR + SUFFIX_STRING));
    keywordList.sort(String.CASE_INSENSITIVE_ORDER);
    result.getKeywords().addAll(keywordList);

    return result;
  }

  private static void addKeywordsFromFields(List<String> keywords, IndexableField[] fields) {
    if (fields == null) {
      return;
    }
    for (IndexableField field : fields) {
      String keyword = field.stringValue();
      if (!keywords.contains(keyword)) {
        keywords.add(keyword);
      }
    }
  }
}
