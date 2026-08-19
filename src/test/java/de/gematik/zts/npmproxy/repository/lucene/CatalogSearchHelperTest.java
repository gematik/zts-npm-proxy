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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.zts.npmproxy.exceptions.PackageIndexException;
import de.gematik.zts.npmproxy.model.LuceneFhirPackageSearchResult;
import de.gematik.zts.npmproxy.repository.lucene.fields.BaseFieldNames;
import java.io.IOException;
import java.util.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CatalogSearchHelperTest {

  // Importieren Sie die Konstanten
  private static final String PACKAGE_NAME = BaseFieldNames.PACKAGE_NAME;
  private static final String PACKAGE_VERSION = BaseFieldNames.PACKAGE_VERSION;
  private static final String PACKAGE_KEYWORDS = BaseFieldNames.PACKAGE_KEYWORDS;
  private static final String SEPARATOR = BaseFieldNames.SEPARATOR;
  private static final String SUFFIX_STRING = BaseFieldNames.SUFFIX_STRING;
  private Query queryMock;
  private IndexSearcher searcherMock;

  @BeforeEach
  void setUp() {
    queryMock = Mockito.mock(Query.class);
    searcherMock = Mockito.mock(IndexSearcher.class);
  }

  @Test
  void testProcessQueryAndReturnPackageNameList_Success() throws Exception {
    // Mocking ScoreDocs
    ScoreDoc[] scoreDocs = {new ScoreDoc(1, 1.0f), new ScoreDoc(2, 0.8f)};

    // Mocking TopDocs
    TopDocs topDocs = new TopDocs(new TotalHits(2, TotalHits.Relation.EQUAL_TO), scoreDocs);

    // Mocking searcher.search(...)
    when(searcherMock.search(any(Query.class), anyInt())).thenReturn(topDocs);

    // Mocking Documents
    Document doc1 = new Document();
    doc1.add(
        new StringField(PACKAGE_NAME + SEPARATOR + SUFFIX_STRING, "package1", Field.Store.YES));
    doc1.add(
        new StringField(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING, "1.0.0", Field.Store.YES));
    doc1.add(
        new StringField(PACKAGE_KEYWORDS + SEPARATOR + SUFFIX_STRING, "keyword1", Field.Store.YES));
    doc1.add(
        new StringField(PACKAGE_KEYWORDS + SEPARATOR + SUFFIX_STRING, "keyword2", Field.Store.YES));

    Document doc2 = new Document();
    doc2.add(
        new StringField(PACKAGE_NAME + SEPARATOR + SUFFIX_STRING, "package2", Field.Store.YES));
    doc2.add(
        new StringField(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING, "2.0.0", Field.Store.YES));
    doc2.add(
        new StringField(PACKAGE_KEYWORDS + SEPARATOR + SUFFIX_STRING, "keyword3", Field.Store.YES));

    // Mocking storedFields().document(...)
    StoredFields storedFieldsMock = mock(StoredFields.class);
    when(searcherMock.storedFields()).thenReturn(storedFieldsMock);
    when(storedFieldsMock.document(1)).thenReturn(doc1);
    when(storedFieldsMock.document(2)).thenReturn(doc2);

    // Aufrufen der zu testenden Methode
    Map<String, LuceneFhirPackageSearchResult> result =
        CatalogSearchHelper.processQueryAndReturnPackageNameList(queryMock, searcherMock);

    // Überprüfen der Ergebnisse
    assertNotNull(result);
    assertEquals(2, result.size());
    assertTrue(result.containsKey("package1"));
    assertTrue(result.containsKey("package2"));

    LuceneFhirPackageSearchResult package1Result = result.get("package1");
    assertEquals(Arrays.asList("keyword1", "keyword2"), package1Result.getKeywords());

    LuceneFhirPackageSearchResult package2Result = result.get("package2");
    assertEquals(Collections.singletonList("keyword3"), package2Result.getKeywords());
  }

  @Test
  void testProcessQueryAndReturnPackageNameList_NoKeywords() throws Exception {
    // Dieser Test deckt den Fall ab, in dem keine Keywords vorhanden sind

    // Mocking ScoreDocs
    ScoreDoc[] scoreDocs = {new ScoreDoc(1, 1.0f)};

    // Mocking TopDocs
    TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

    // Mocking searcher.search(...)
    when(searcherMock.search(any(Query.class), anyInt())).thenReturn(topDocs);

    // Mocking Document ohne Keywords
    Document doc1 = new Document();
    doc1.add(
        new StringField(PACKAGE_NAME + SEPARATOR + SUFFIX_STRING, "package1", Field.Store.YES));
    doc1.add(
        new StringField(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING, "1.0.0", Field.Store.YES));
    // Keine Keywords hinzugefügt

    // Mocking storedFields().document(...)
    StoredFields storedFieldsMock = mock(StoredFields.class);
    when(searcherMock.storedFields()).thenReturn(storedFieldsMock);
    when(storedFieldsMock.document(1)).thenReturn(doc1);

    // Aufrufen der zu testenden Methode
    Map<String, LuceneFhirPackageSearchResult> result =
        CatalogSearchHelper.processQueryAndReturnPackageNameList(queryMock, searcherMock);

    // Überprüfen der Ergebnisse
    assertNotNull(result);
    assertEquals(1, result.size());
    assertTrue(result.containsKey("package1"));

    LuceneFhirPackageSearchResult package1Result = result.get("package1");
    assertNotNull(package1Result.getKeywords());
    assertTrue(package1Result.getKeywords().isEmpty());
  }

  @Test
  void testProcessQueryAndReturnPackageNameList_NullKeywords() throws Exception {
    // Dieser Test deckt den Fall ab, in dem keywordFields null ist

    // Mocking ScoreDocs
    ScoreDoc[] scoreDocs = {new ScoreDoc(1, 1.0f)};

    // Mocking TopDocs
    TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

    // Mocking searcher.search(...)
    when(searcherMock.search(any(Query.class), anyInt())).thenReturn(topDocs);

    // Mocking Document
    Document doc1 = mock(Document.class);
    when(doc1.getField(PACKAGE_NAME + SEPARATOR + SUFFIX_STRING))
        .thenReturn(
            new StringField(PACKAGE_NAME + SEPARATOR + SUFFIX_STRING, "package1", Field.Store.YES));
    when(doc1.getField(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING))
        .thenReturn(
            new StringField(PACKAGE_VERSION + SEPARATOR + SUFFIX_STRING, "1.0.0", Field.Store.YES));

    // Simulieren, dass getFields(...) null zurückgibt
    when(doc1.getFields(PACKAGE_KEYWORDS + SEPARATOR + SUFFIX_STRING)).thenReturn(null);

    // Mocking storedFields().document(...)
    StoredFields storedFieldsMock = mock(StoredFields.class);
    when(searcherMock.storedFields()).thenReturn(storedFieldsMock);
    when(storedFieldsMock.document(1)).thenReturn(doc1);

    // Aufrufen der zu testenden Methode
    Map<String, LuceneFhirPackageSearchResult> result =
        CatalogSearchHelper.processQueryAndReturnPackageNameList(queryMock, searcherMock);

    // Überprüfen der Ergebnisse
    assertNotNull(result);
    assertEquals(1, result.size());
    assertTrue(result.containsKey("package1"));

    LuceneFhirPackageSearchResult package1Result = result.get("package1");
    assertNotNull(package1Result.getKeywords());
    assertTrue(package1Result.getKeywords().isEmpty());
  }

  @Test
  void testProcessQueryAndReturnPackageNameList_ThrowsPackageIndexException() throws Exception {
    // Mocking searcher.search(...) to throw IOException
    when(searcherMock.search(any(Query.class), anyInt()))
        .thenThrow(new IOException("Mocked IOException"));

    // Überprüfen, ob eine PackageIndexException geworfen wird
    assertThrows(
        PackageIndexException.class,
        () -> {
          CatalogSearchHelper.processQueryAndReturnPackageNameList(queryMock, searcherMock);
        });
  }

  @Test
  void testProcessQueryAndReturnPackageNameList_ProcessScoreDocException() throws Exception {
    // Mocking ScoreDocs
    ScoreDoc[] scoreDocs = {new ScoreDoc(1, 1.0f)};

    // Mocking TopDocs
    TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

    // Mocking searcher.search(...) to return topDocs
    when(searcherMock.search(any(Query.class), anyInt())).thenReturn(topDocs);

    // Mocking storedFields().document(...) to return a Document that will cause an exception
    StoredFields storedFieldsMock = mock(StoredFields.class);
    when(searcherMock.storedFields()).thenReturn(storedFieldsMock);

    // Mocking the Document to throw an exception when getField(...) is called
    Document docMock = mock(Document.class);
    when(storedFieldsMock.document(1)).thenReturn(docMock);

    // Simulieren einer Exception beim Aufruf von getField()
    when(docMock.getField(anyString()))
        .thenThrow(new RuntimeException("Mocked Exception in getField"));

    // Überprüfen, ob eine PackageIndexException geworfen wird
    assertThrows(
        PackageIndexException.class,
        () -> {
          CatalogSearchHelper.processQueryAndReturnPackageNameList(queryMock, searcherMock);
        });
  }
}
