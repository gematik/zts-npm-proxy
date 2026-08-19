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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.jupiter.api.Test;

class GenericQueryGeneratorTest {

  @Test
  void testPrepareFilterQuery_withMultipleQueries() {
    // Arrange
    Query query1 = new TermQuery(new Term("field1", "value1"));
    Query query2 = new TermQuery(new Term("field2", "value2"));
    List<Query> queryList = Arrays.asList(query1, query2);

    // Act
    Query result = GenericQueryGenerator.prepareFilterQuery(queryList);

    // Assert
    assertNotNull(result);
    assertInstanceOf(BooleanQuery.class, result);

    BooleanQuery booleanQuery = (BooleanQuery) result;
    List<BooleanClause> clauses = booleanQuery.clauses();
    assertEquals(2, clauses.size());

    for (BooleanClause clause : clauses) {
      assertEquals(BooleanClause.Occur.FILTER, clause.occur());
      assertTrue(queryList.contains(clause.query()));
    }
  }

  @Test
  void testPrepareFilterQuery_withEmptyList() {
    // Arrange
    List<Query> queryList = Collections.emptyList();

    // Act
    Query result = GenericQueryGenerator.prepareFilterQuery(queryList);

    // Assert
    assertNotNull(result);
    assertInstanceOf(BooleanQuery.class, result);

    BooleanQuery booleanQuery = (BooleanQuery) result;
    assertTrue(booleanQuery.clauses().isEmpty());
  }

  @Test
  void testPrepareAndQuery_withMultipleQueries() {
    // Arrange
    Query query1 = new TermQuery(new Term("field1", "value1"));
    Query query2 = new MatchAllDocsQuery();
    List<Query> queryList = Arrays.asList(query1, query2);

    // Act
    Query result = GenericQueryGenerator.prepareAndQuery(queryList);

    // Assert
    assertNotNull(result);
    assertInstanceOf(BooleanQuery.class, result);

    BooleanQuery booleanQuery = (BooleanQuery) result;
    List<BooleanClause> clauses = booleanQuery.clauses();
    assertEquals(2, clauses.size());

    for (BooleanClause clause : clauses) {
      assertEquals(BooleanClause.Occur.MUST, clause.occur());
      assertTrue(queryList.contains(clause.query()));
    }
  }

  @Test
  void testPrepareAndQuery_withEmptyList() {
    // Arrange
    List<Query> queryList = Collections.emptyList();

    // Act
    Query result = GenericQueryGenerator.prepareAndQuery(queryList);

    // Assert
    assertNotNull(result);
    assertInstanceOf(BooleanQuery.class, result);

    BooleanQuery booleanQuery = (BooleanQuery) result;
    assertTrue(booleanQuery.clauses().isEmpty());
  }

  @Test
  void testPrepareOrQuery_withMultipleQueries() {
    // Arrange
    Query query1 = new TermQuery(new Term("field1", "value1"));
    Query query2 = new MatchAllDocsQuery();
    List<Query> queryList = Arrays.asList(query1, query2);

    // Act
    Query result = GenericQueryGenerator.prepareOrQuery(queryList);

    // Assert
    assertNotNull(result);
    assertInstanceOf(BooleanQuery.class, result);

    BooleanQuery booleanQuery = (BooleanQuery) result;
    List<BooleanClause> clauses = booleanQuery.clauses();
    assertEquals(2, clauses.size());

    for (BooleanClause clause : clauses) {
      assertEquals(BooleanClause.Occur.SHOULD, clause.occur());
      assertTrue(queryList.contains(clause.query()));
    }
  }

  @Test
  void testPrepareOrQuery_withEmptyList() {
    // Arrange
    List<Query> queryList = Collections.emptyList();

    // Act
    Query result = GenericQueryGenerator.prepareOrQuery(queryList);

    // Assert
    assertNotNull(result);
    assertInstanceOf(BooleanQuery.class, result);

    BooleanQuery booleanQuery = (BooleanQuery) result;
    assertTrue(booleanQuery.clauses().isEmpty());
  }

  @Test
  void testPrepareCombinedFilterMustQuery_withValidQueries() {
    // Arrange
    Query filterQuery = new TermQuery(new Term("filterField", "filterValue"));
    Query mustQuery = new TermQuery(new Term("mustField", "mustValue"));

    // Act
    Query result = GenericQueryGenerator.prepareCombinedFilterMustQuery(filterQuery, mustQuery);

    // Assert
    assertNotNull(result);
    assertInstanceOf(BooleanQuery.class, result);

    BooleanQuery booleanQuery = (BooleanQuery) result;
    List<BooleanClause> clauses = booleanQuery.clauses();
    assertEquals(2, clauses.size());

    // Überprüfen der FILTER-Klausel
    BooleanClause filterClause =
        clauses.stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.FILTER)
            .findFirst()
            .orElse(null);
    assertNotNull(filterClause);
    assertEquals(filterQuery, filterClause.query());

    // Überprüfen der MUST-Klausel
    BooleanClause mustClause =
        clauses.stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.MUST)
            .findFirst()
            .orElse(null);
    assertNotNull(mustClause);
    assertEquals(mustQuery, mustClause.query());
  }

  @Test
  void testPrepareCombinedFilterMustQuery_withEmptyMustQuery() {
    // Arrange
    Query filterQuery = new TermQuery(new Term("filterField", "filterValue"));
    Query mustQuery = new BooleanQuery.Builder().build(); // Leere Query

    // Act
    Query result = GenericQueryGenerator.prepareCombinedFilterMustQuery(filterQuery, mustQuery);

    // Assert
    // Da mustQuery leer ist und toString() leer, sollte sie nicht hinzugefügt werden
    assertNotNull(result);
    assertInstanceOf(BooleanQuery.class, result);

    BooleanQuery booleanQuery = (BooleanQuery) result;
    List<BooleanClause> clauses = booleanQuery.clauses();
    assertEquals(1, clauses.size());

    BooleanClause filterClause = clauses.get(0);
    assertEquals(BooleanClause.Occur.FILTER, filterClause.occur());
    assertEquals(filterQuery, filterClause.query());
  }

  @Test
  void testPrepareCombinedFilterMustQuery_withEmptyFilterQuery() {
    // Arrange
    Query filterQuery = new BooleanQuery.Builder().build(); // Leere Query
    Query mustQuery = new TermQuery(new Term("mustField", "mustValue"));

    // Act
    Query result = GenericQueryGenerator.prepareCombinedFilterMustQuery(filterQuery, mustQuery);

    // Assert
    // Auch wenn filterQuery leer ist, wird sie hinzugefügt
    assertNotNull(result);
    assertInstanceOf(BooleanQuery.class, result);

    BooleanQuery booleanQuery = (BooleanQuery) result;
    List<BooleanClause> clauses = booleanQuery.clauses();
    assertEquals(2, clauses.size());

    // Überprüfen der FILTER-Klausel (leere Query)
    BooleanClause filterClause =
        clauses.stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.FILTER)
            .findFirst()
            .orElse(null);
    assertNotNull(filterClause);
    assertEquals(filterQuery, filterClause.query());

    // Überprüfen der MUST-Klausel
    BooleanClause mustClause =
        clauses.stream()
            .filter(clause -> clause.occur() == BooleanClause.Occur.MUST)
            .findFirst()
            .orElse(null);
    assertNotNull(mustClause);
    assertEquals(mustQuery, mustClause.query());
  }

  @Test
  void testPrepareCombinedFilterMustQuery_withBothQueriesEmpty() {
    // Arrange
    Query filterQuery = new BooleanQuery.Builder().build(); // Leere Query
    Query mustQuery = new BooleanQuery.Builder().build(); // Leere Query

    // Act
    Query result = GenericQueryGenerator.prepareCombinedFilterMustQuery(filterQuery, mustQuery);

    // Assert
    // Da mustQuery leer ist und toString() leer, sollte sie nicht hinzugefügt werden
    // filterQuery wird hinzugefügt
    assertNotNull(result);
    assertInstanceOf(BooleanQuery.class, result);

    BooleanQuery booleanQuery = (BooleanQuery) result;
    List<BooleanClause> clauses = booleanQuery.clauses();
    assertEquals(1, clauses.size());

    BooleanClause filterClause = clauses.get(0);
    assertEquals(BooleanClause.Occur.FILTER, filterClause.occur());
    assertEquals(filterQuery, filterClause.query());
  }

  @Test
  void testPrepareFilterQuery_withNullList() {
    // Arrange
    List<Query> queryList = null;

    // Act & Assert
    assertThrows(
        NullPointerException.class,
        () -> {
          GenericQueryGenerator.prepareFilterQuery(queryList);
        });
  }

  @Test
  void testPrepareAndQuery_withNullList() {
    // Arrange
    List<Query> queryList = null;

    // Act & Assert
    assertThrows(
        NullPointerException.class,
        () -> {
          GenericQueryGenerator.prepareAndQuery(queryList);
        });
  }

  @Test
  void testPrepareOrQuery_withNullList() {
    // Arrange
    List<Query> queryList = null;

    // Act & Assert
    assertThrows(
        NullPointerException.class,
        () -> {
          GenericQueryGenerator.prepareOrQuery(queryList);
        });
  }

  @Test
  void testPrepareCombinedFilterMustQuery_withNullQueries() {
    // Arrange
    Query filterQuery = null;
    Query mustQuery = null;

    // Act & Assert
    assertThrows(
        NullPointerException.class,
        () -> {
          GenericQueryGenerator.prepareCombinedFilterMustQuery(filterQuery, mustQuery);
        });
  }
}
