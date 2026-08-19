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

import static de.gematik.zts.npmproxy.repository.lucene.fields.BaseFieldNames.*;
import static org.junit.jupiter.api.Assertions.*;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.jupiter.api.Test;

class StringParameterQueryGeneratorTest {

  @Test
  void testPrepareExactQuery() {
    // Arrange
    String paramName = "testParam";
    String paramValue = "testValue";

    // Act
    Query query = StringParameterQueryGenerator.prepareExactQuery(paramName, paramValue);

    // Assert
    String expectedField = paramName + SEPARATOR + SUFFIX_STRING;
    TermQuery termQuery = assertInstanceOf(TermQuery.class, query);
    Term term = termQuery.getTerm();
    assertEquals(expectedField, term.field());
    assertEquals(paramValue, term.text());
  }

  @Test
  void testPreparePrefixQuery() {
    // Arrange
    String paramName = "testParam";
    String paramValue = "prefixValue";

    // Act
    Query query = StringParameterQueryGenerator.preparePrefixQuery(paramName, paramValue);

    // Assert
    String expectedField = paramName + SEPARATOR + SUFFIX_STRING;
    PrefixQuery prefixQuery = assertInstanceOf(PrefixQuery.class, query);
    Term prefix = prefixQuery.getPrefix();
    assertEquals(expectedField, prefix.field());
    assertEquals(paramValue, prefix.text());
  }

  @Test
  void testPrepareKeywordQuery() {
    // Arrange
    String paramName = "keywordParam";
    String keyword = "KeywordValue";

    // Act
    Query query = StringParameterQueryGenerator.prepareKeywordQuery(paramName, keyword);

    // Assert
    String expectedField = paramName + SEPARATOR + SUFFIX_STRING;
    TermQuery termQuery = assertInstanceOf(TermQuery.class, query);
    Term term = termQuery.getTerm();
    assertEquals(expectedField, term.field());
    assertEquals(keyword.toLowerCase(), term.text());
  }

  @Test
  void testPrepareExactQuery_WithSpecialCharacters() {
    // Arrange
    String paramName = "specialParam";
    String paramValue = "value-with-special-characters_123";

    // Act
    Query query = StringParameterQueryGenerator.prepareExactQuery(paramName, paramValue);

    // Assert
    String expectedField = paramName + SEPARATOR + SUFFIX_STRING;
    TermQuery termQuery = assertInstanceOf(TermQuery.class, query);
    Term term = termQuery.getTerm();
    assertEquals(expectedField, term.field());
    assertEquals(paramValue, term.text());
  }

  @Test
  void testPreparePrefixQuery_WithEmptyValue() {
    // Arrange
    String paramName = "emptyParam";
    String paramValue = "";

    // Act
    Query query = StringParameterQueryGenerator.preparePrefixQuery(paramName, paramValue);

    // Assert
    String expectedField = paramName + SEPARATOR + SUFFIX_STRING;
    PrefixQuery prefixQuery = assertInstanceOf(PrefixQuery.class, query);
    Term prefix = prefixQuery.getPrefix();
    assertEquals(expectedField, prefix.field());
    assertEquals(paramValue, prefix.text());
  }

  @Test
  void testPrepareKeywordQuery_WithUpperCaseValue() {
    // Arrange
    String paramName = "uppercaseParam";
    String keyword = "UPPERCASEKEYWORD";

    // Act
    Query query = StringParameterQueryGenerator.prepareKeywordQuery(paramName, keyword);

    // Assert
    String expectedField = paramName + SEPARATOR + SUFFIX_STRING;
    TermQuery termQuery = assertInstanceOf(TermQuery.class, query);
    Term term = termQuery.getTerm();
    assertEquals(expectedField, term.field());
    assertEquals(keyword.toLowerCase(), term.text());
  }

  @Test
  void testPrepareExactQuery_NullValues() {
    // Arrange
    String paramName = null;
    String paramValue = null;

    // Act & Assert
    assertThrows(
        NullPointerException.class,
        () -> {
          StringParameterQueryGenerator.prepareExactQuery(paramName, paramValue);
        });
  }

  @Test
  void testPreparePrefixQuery_NullValues() {
    // Arrange
    String paramName = null;
    String paramValue = null;

    // Act & Assert
    assertThrows(
        NullPointerException.class,
        () -> {
          StringParameterQueryGenerator.preparePrefixQuery(paramName, paramValue);
        });
  }

  @Test
  void testPrepareKeywordQuery_NullValues() {
    // Arrange
    String paramName = null;
    String keyword = null;

    // Act & Assert
    assertThrows(
        NullPointerException.class,
        () -> {
          StringParameterQueryGenerator.prepareKeywordQuery(paramName, keyword);
        });
  }
}
