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

package de.gematik.zts.npmproxy.repository.lucene.querygenerators;

import static de.gematik.zts.npmproxy.NpmProxyConstants.CATALOG_KEYWORD_OR_SEPARATOR_CHAR;
import static de.gematik.zts.npmproxy.repository.lucene.fields.BaseFieldNames.*;

import de.gematik.zts.npmproxy.model.SearchPackageParameters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

@Slf4j
public class CatalogParameterQueryGenerator {

  private CatalogParameterQueryGenerator() {}

  public static Query prepareExactNameAndVersionQuery(String packageName, String packageVersion) {
    // Prefilter
    Query filterQuery =
        StringParameterQueryGenerator.prepareExactQuery(CONTENT_TYPE, CONTENT_TYPE_PACKAGE);

    BooleanQuery.Builder mainQueryBuilder = new BooleanQuery.Builder();

    // Füge den Vorfilter zur Hauptabfrage hinzu
    mainQueryBuilder.add(filterQuery, BooleanClause.Occur.FILTER);

    // 'name' Parameter
    mainQueryBuilder.add(
        StringParameterQueryGenerator.prepareExactQuery(PACKAGE_NAME, packageName.toLowerCase()),
        BooleanClause.Occur.MUST);

    // 'version' Parameter
    mainQueryBuilder.add(
        StringParameterQueryGenerator.prepareExactQuery(
            PACKAGE_VERSION, packageVersion.toLowerCase()),
        BooleanClause.Occur.MUST);

    return mainQueryBuilder.build();
  }

  public static Query prepareQuery(SearchPackageParameters searchPackageParameters) {

    // Prefilter
    Query filterQuery =
        StringParameterQueryGenerator.prepareExactQuery(CONTENT_TYPE, CONTENT_TYPE_PACKAGE);

    BooleanQuery.Builder mainQueryBuilder = new BooleanQuery.Builder();

    // Füge den Vorfilter zur Hauptabfrage hinzu
    mainQueryBuilder.add(filterQuery, BooleanClause.Occur.FILTER);

    // Listen für MUST- und SHOULD-Abfragen
    List<Query> mustQueries = new ArrayList<>();
    List<Query> shouldQueries = new ArrayList<>();

    // 'name' Parameter
    searchPackageParameters
        .getName()
        .ifPresent(
            s ->
                mustQueries.add(
                    StringParameterQueryGenerator.preparePrefixQuery(
                        PACKAGE_NAME, s.toLowerCase())));

    // 'version' Parameter
    searchPackageParameters
        .getVersion()
        .ifPresent(
            s ->
                mustQueries.add(
                    StringParameterQueryGenerator.preparePrefixQuery(
                        PACKAGE_VERSION, s.toLowerCase())));

    // 'canonical' Parameter
    searchPackageParameters
        .getCanonical()
        .ifPresent(
            s ->
                mustQueries.add(
                    StringParameterQueryGenerator.preparePrefixQuery(CANONICAL, s.toLowerCase())));

    // 'canonicalVersion' Parameter
    searchPackageParameters
        .getCanonicalVersion()
        .ifPresent(
            s ->
                mustQueries.add(
                    StringParameterQueryGenerator.prepareExactQuery(
                        CANONICAL_VERSION, s.toLowerCase())));

    // 'pkgcanonical' Parameter
    searchPackageParameters
        .getPkgcanonical()
        .ifPresent(
            s ->
                mustQueries.add(
                    StringParameterQueryGenerator.prepareExactQuery(PACKAGE_CANONICAL, s)));

    // 'fhirVersion' Parameter
    searchPackageParameters
        .getFhirVersion()
        .ifPresent(
            s ->
                mustQueries.add(
                    StringParameterQueryGenerator.prepareExactQuery(PACKAGE_FHIRVERSION, s)));

    // 'prerelease' Parameter
    var prerelease = searchPackageParameters.getPrerelease();
    if (prerelease.isEmpty() || Boolean.TRUE.equals(!prerelease.get())) {
      mustQueries.add(
          StringParameterQueryGenerator.prepareExactQuery(PACKAGE_ISPRERELEASE, "false"));
    }

    // 'prerelease' Parameter
    var unlisted = searchPackageParameters.getIncludeUnlisted();
    if (unlisted.isEmpty() || Boolean.TRUE.equals(!unlisted.get())) {
      mustQueries.add(StringParameterQueryGenerator.prepareExactQuery(PACKAGE_UNLISTED, "false"));
    }

    // 'protected' Parameter
    searchPackageParameters
        .getProtectedPackage()
        .ifPresent(
            aBoolean ->
                mustQueries.add(
                    StringParameterQueryGenerator.prepareExactQuery(
                        PACKAGE_PROTECTED, aBoolean.toString())));

    // 'keyword' Parameter
    searchPackageParameters
        .getKeywordParams()
        .ifPresent(strings -> processKeywordParams(strings, mustQueries, shouldQueries));

    // Füge alle MUST-Abfragen zur Hauptabfrage hinzu
    mustQueries.forEach(query -> mainQueryBuilder.add(query, BooleanClause.Occur.MUST));

    // Füge alle SHOULD-Abfragen zur Hauptabfrage hinzu
    if (!shouldQueries.isEmpty()) {
      BooleanQuery.Builder shouldQueryBuilder = new BooleanQuery.Builder();
      shouldQueries.forEach(query -> shouldQueryBuilder.add(query, BooleanClause.Occur.SHOULD));
      // Sicherstellen, dass mindestens eine SHOULD-Abfrage übereinstimmt
      shouldQueryBuilder.setMinimumNumberShouldMatch(1);
      mainQueryBuilder.add(shouldQueryBuilder.build(), BooleanClause.Occur.MUST);
    }

    // Wenn keine MUST- oder SHOULD-Abfragen vorhanden sind, füge eine MatchAllDocsQuery hinzu
    if (mustQueries.isEmpty() && shouldQueries.isEmpty()) {
      mainQueryBuilder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
    }

    return mainQueryBuilder.build();
  }

  private static void processKeywordParams(
      List<String> keywords, List<Query> mustQueries, List<Query> shouldQueries) {

    List<Query> allAndQueries = new ArrayList<>();
    List<Query> allOrQueries = new ArrayList<>();

    for (String keywordParam : keywords) {
      // Trimmen und prüfen, ob das Keyword leer ist
      String trimmedParam = keywordParam.trim();
      if (trimmedParam.isEmpty()) {
        continue;
      }

      List<String> keywordFields = List.of(PACKAGE_KEYWORDS, PACKAGE_ADDITIONAL_KEYWORDS);
      if (trimmedParam.contains(CATALOG_KEYWORD_OR_SEPARATOR_CHAR)) {
        // OR-Keywords verarbeiten
        String[] orKeywords = trimmedParam.split(CATALOG_KEYWORD_OR_SEPARATOR_CHAR);
        List<Query> orQueries =
            Arrays.stream(orKeywords)
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .map(k -> StringParameterQueryGenerator.prepareKeywordQuery(keywordFields, k))
                .toList();

        if (!orQueries.isEmpty()) {
          Query orQuery = GenericQueryGenerator.prepareOrQuery(orQueries);
          allOrQueries.add(orQuery);
        }
      } else {
        // AND-Keyword verarbeiten
        Query andQuery =
            StringParameterQueryGenerator.prepareKeywordQuery(keywordFields, trimmedParam);
        allAndQueries.add(andQuery);
      }
    }

    // Wenn es AND-Queries gibt, diese als MUST hinzufügen
    if (!allAndQueries.isEmpty()) {
      Query combinedAndQuery = GenericQueryGenerator.prepareAndQuery(allAndQueries);
      mustQueries.add(combinedAndQuery);
    }

    // Wenn es OR-Queries gibt, diese als SHOULD hinzufügen
    if (!allOrQueries.isEmpty()) {
      Query combinedOrQuery = GenericQueryGenerator.prepareOrQuery(allOrQueries);
      shouldQueries.add(combinedOrQuery);
    }
  }
}
