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

import static de.gematik.zts.npmproxy.SearchTestHelper.createEmptySearchPackageParameters;
import static de.gematik.zts.npmproxy.repository.lucene.fields.BaseFieldNames.*;
import static org.junit.jupiter.api.Assertions.*;

import de.gematik.zts.npmproxy.model.SearchPackageParameters;
import java.util.List;
import java.util.Optional;
import org.apache.lucene.search.*;
import org.junit.jupiter.api.Test;

class CatalogParameterQueryGeneratorTest {

  @Test
  void testPrepareQuery_withNoParameters() {
    // Arrange
    SearchPackageParameters params = createEmptySearchPackageParameters();

    // Act
    Query query = CatalogParameterQueryGenerator.prepareQuery(params);

    // Assert
    assertNotNull(query);
    assertInstanceOf(BooleanQuery.class, query);

    BooleanQuery booleanQuery = (BooleanQuery) query;
    List<BooleanClause> clauses = booleanQuery.clauses();

    // Es sollte eine FILTER-Klausel, eine MUST-Klausel für prerelease und unlisted geben
    assertEquals(3, clauses.size());

    // Überprüfen der FILTER-Klausel
    BooleanClause filterClause =
        clauses.stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.FILTER)
            .findFirst()
            .orElse(null);
    assertNotNull(filterClause);
    Query filterQuery = filterClause.query();
    TermQuery termFilterQuery = assertInstanceOf(TermQuery.class, filterQuery);
    assertEquals(CONTENT_TYPE + SEPARATOR + SUFFIX_STRING, termFilterQuery.getTerm().field());
    assertEquals(CONTENT_TYPE_PACKAGE, termFilterQuery.getTerm().text());

    // Überprüfen der MUST-Klausel für prerelease
    BooleanClause prereleaseClause =
        clauses.stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.MUST)
            .filter(clause -> clause.query() instanceof TermQuery)
            .filter(
                clause ->
                    ((TermQuery) clause.query())
                        .getTerm()
                        .field()
                        .equals(PACKAGE_ISPRERELEASE + SEPARATOR + SUFFIX_STRING))
            .findFirst()
            .orElse(null);
    assertNotNull(prereleaseClause);
    TermQuery prereleaseQuery = (TermQuery) prereleaseClause.query();
    assertEquals("false", prereleaseQuery.getTerm().text());
  }

  @Test
  void testPrepareQuery_withNameParameter() {
    // Arrange
    SearchPackageParameters params = createEmptySearchPackageParameters();
    params.setName(Optional.of("test-package"));

    // Act
    Query query = CatalogParameterQueryGenerator.prepareQuery(params);

    // Assert
    assertNotNull(query);
    assertInstanceOf(BooleanQuery.class, query);

    BooleanQuery booleanQuery = (BooleanQuery) query;
    List<BooleanClause> clauses = booleanQuery.clauses();

    // Es sollte eine FILTER-Klausel und drei MUST-Klauseln geben (Name, prerelease, unlisted)
    assertEquals(4, clauses.size());

    // Überprüfen der MUST-Klausel für den Namen
    BooleanClause nameClause =
        clauses.stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.MUST)
            .filter(clause -> clause.query() instanceof PrefixQuery)
            .findFirst()
            .orElse(null);
    assertNotNull(nameClause);
    PrefixQuery nameQuery = (PrefixQuery) nameClause.query();
    assertEquals(PACKAGE_NAME + SEPARATOR + SUFFIX_STRING, nameQuery.getPrefix().field());
    assertEquals("test-package", nameQuery.getPrefix().text());
  }

  @Test
  void testPrepareQuery_withPrereleaseAndUnlistedTrue() {
    // Arrange
    SearchPackageParameters params = createEmptySearchPackageParameters();
    params.setPrerelease(Optional.of(true));
    params.setIncludeUnlisted(Optional.of(true));

    // Act
    Query query = CatalogParameterQueryGenerator.prepareQuery(params);

    // Assert
    BooleanQuery booleanQuery = assertInstanceOf(BooleanQuery.class, query);
    List<BooleanClause> clauses = booleanQuery.clauses();

    // Es sollte nur die FILTER-Klausel und eine MUST-Klausel mit MatchALlDocs,
    // aber keine
    // prerelease-Klausel
    assertEquals(2, clauses.size());

    // Überprüfen, dass keine MUST-Klausel für prerelease vorhanden ist
    BooleanClause prereleaseClause =
        clauses.stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.MUST)
            .filter(clause -> clause.query() instanceof TermQuery)
            .filter(
                clause ->
                    ((TermQuery) clause.query())
                        .getTerm()
                        .field()
                        .equals(PACKAGE_ISPRERELEASE + SEPARATOR + SUFFIX_STRING))
            .findFirst()
            .orElse(null);
    assertNull(prereleaseClause);

    // Überprüfen der MUST-Klausel mit MatchAllDocsQuery
    BooleanClause mustClause =
        clauses.stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.MUST)
            .filter(clause -> clause.query() instanceof MatchAllDocsQuery)
            .findFirst()
            .orElse(null);
    assertNotNull(mustClause);
  }

  @Test
  void testPrepareQuery_withVersionParameter() {
    // Arrange
    SearchPackageParameters params = createEmptySearchPackageParameters();
    params.setVersion(Optional.of("1.0"));

    // Act
    Query query = CatalogParameterQueryGenerator.prepareQuery(params);

    // Assert
    assertNotNull(query);
    assertInstanceOf(BooleanQuery.class, query);

    BooleanQuery booleanQuery = (BooleanQuery) query;
    List<BooleanClause> clauses = booleanQuery.clauses();

    // Es sollte eine FILTER-Klausel und drei MUST-Klauseln geben (Name, prerelease, unlisted)
    assertEquals(4, clauses.size());

    // Überprüfen der MUST-Klausel für die Version
    BooleanClause versionClause =
        clauses.stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.MUST)
            .filter(clause -> clause.query() instanceof PrefixQuery)
            .findFirst()
            .orElse(null);
    assertNotNull(versionClause);
    PrefixQuery versionQuery = (PrefixQuery) versionClause.query();
    assertEquals(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING, versionQuery.getPrefix().field());
    assertEquals("1.0", versionQuery.getPrefix().text());
  }

  // Die übrigen Tests passen wir analog an, um die prerelease-Klausel zu berücksichtigen.

  @Test
  void testPrepareQuery_withMultipleParameters() {
    // Arrange
    SearchPackageParameters params = createEmptySearchPackageParameters();
    params.setName(Optional.of("test-package"));
    params.setVersion(Optional.of("1.0"));
    params.setFhirVersion(Optional.of("R4"));

    // Act
    Query query = CatalogParameterQueryGenerator.prepareQuery(params);

    // Assert
    assertNotNull(query);
    assertInstanceOf(BooleanQuery.class, query);

    BooleanQuery booleanQuery = (BooleanQuery) query;
    List<BooleanClause> clauses = booleanQuery.clauses();

    // Es sollte eine FILTER-Klausel und fünf MUST-Klauseln geben (Name, Version, FHIR-Version,
    // prerelease, unlisted)
    assertEquals(6, clauses.size());

    // Überprüfen der Anzahl der MUST-Klauseln
    long mustCount =
        clauses.stream().filter(clause -> clause.occur() == BooleanClause.Occur.MUST).count();
    assertEquals(5, mustCount);
  }

  @Test
  void testPrepareQuery_withPrereleaseFalse() {
    // Arrange
    SearchPackageParameters params = createEmptySearchPackageParameters();
    params.setPrerelease(Optional.of(false));

    // Act
    Query query = CatalogParameterQueryGenerator.prepareQuery(params);

    // Assert
    BooleanQuery booleanQuery = assertInstanceOf(BooleanQuery.class, query);
    List<BooleanClause> clauses = booleanQuery.clauses();

    // Es sollte eine FILTER-Klausel und zwei MUST-Klausel (prerelease, unlisted) geben
    assertEquals(3, clauses.size());

    // Überprüfen der MUST-Klausel für prerelease
    BooleanClause prereleaseClause =
        clauses.stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.MUST)
            .filter(clause -> clause.query() instanceof TermQuery)
            .filter(
                clause ->
                    ((TermQuery) clause.query())
                        .getTerm()
                        .field()
                        .equals(PACKAGE_ISPRERELEASE + SEPARATOR + SUFFIX_STRING))
            .findFirst()
            .orElse(null);
    assertNotNull(prereleaseClause);
    TermQuery prereleaseQuery = (TermQuery) prereleaseClause.query();
    assertEquals("false", prereleaseQuery.getTerm().text());
  }

  @Test
  void testPrepareQuery_withProtectedPackageTrue() {
    // Arrange
    SearchPackageParameters params = createEmptySearchPackageParameters();
    params.setProtectedPackage(Optional.of(true));

    // Act
    Query query = CatalogParameterQueryGenerator.prepareQuery(params);

    // Assert
    BooleanQuery booleanQuery = assertInstanceOf(BooleanQuery.class, query);
    List<BooleanClause> clauses = booleanQuery.clauses();

    // Es sollte eine FILTER-Klausel und drei MUST-Klauseln geben (protectedPackage, prerelease,
    // unlisted)
    assertEquals(4, clauses.size());

    // Überprüfen der MUST-Klausel für protectedPackage
    BooleanClause protectedClause =
        clauses.stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.MUST)
            .filter(clause -> clause.query() instanceof TermQuery)
            .filter(
                clause ->
                    ((TermQuery) clause.query())
                        .getTerm()
                        .field()
                        .equals(PACKAGE_PROTECTED + SEPARATOR + SUFFIX_STRING))
            .findFirst()
            .orElse(null);
    assertNotNull(protectedClause);
    TermQuery protectedQuery = (TermQuery) protectedClause.query();
    assertEquals("true", protectedQuery.getTerm().text());
  }

  @Test
  void testPrepareQuery_withKeywordParams() {
    // Arrange
    SearchPackageParameters params = createEmptySearchPackageParameters();
    params.setKeywordParams(Optional.of(List.of("keyword1", "keyword2")));

    // Act
    Query query = CatalogParameterQueryGenerator.prepareQuery(params);

    // Assert
    BooleanQuery booleanQuery = assertInstanceOf(BooleanQuery.class, query);
    List<BooleanClause> clauses = booleanQuery.clauses();

    // Es sollte eine FILTER-Klausel, zwei MUST-Klausel für prerelease, unlisted und eine
    // MUST-Klausel für die
    // Keywords geben
    assertEquals(4, clauses.size());

    // Überprüfen der MUST-Klausel mit AND-Keywords
    BooleanClause keywordClause =
        clauses.stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.MUST)
            .filter(clause -> clause.query() instanceof BooleanQuery)
            .findFirst()
            .orElse(null);
    assertNotNull(keywordClause);

    BooleanQuery keywordQuery = (BooleanQuery) keywordClause.query();
    assertEquals(2, keywordQuery.clauses().size());

    // Weitere Überprüfungen wie zuvor
  }

  @Test
  void testPrepareQuery_withKeywordParamsContainingOr() {
    // Arrange
    SearchPackageParameters params = createEmptySearchPackageParameters();
    params.setKeywordParams(Optional.of(List.of("keyword1,keyword2", "keyword3")));

    // Act
    Query query = CatalogParameterQueryGenerator.prepareQuery(params);

    // Assert
    BooleanQuery booleanQuery = assertInstanceOf(BooleanQuery.class, query);

    // Es sollte eine FILTER-Klausel und mindestens drei MUST-Klauseln geben (prerelease, unlisted
    // und
    // Keywords)
    List<BooleanClause> mustClauses =
        booleanQuery.clauses().stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.MUST)
            .toList();

    // Insgesamt sollten es 4 Klauseln sein (FILTER, prerelease, AND-Keywords, OR-Keywords)
    assertEquals(5, booleanQuery.clauses().size());
    assertEquals(4, mustClauses.size());

    // Weitere Überprüfungen können analog angepasst werden
  }
}
