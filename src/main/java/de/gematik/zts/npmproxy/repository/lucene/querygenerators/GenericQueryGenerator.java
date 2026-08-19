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

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

@Slf4j
public class GenericQueryGenerator {

  private GenericQueryGenerator() {}

  public static Query prepareFilterQuery(List<Query> queryList) {

    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (Query query : queryList) {
      builder.add(query, BooleanClause.Occur.FILTER);
    }
    return builder.build();
  }

  public static Query prepareAndQuery(List<Query> queryList) {

    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (Query query : queryList) {
      builder.add(query, BooleanClause.Occur.MUST);
    }
    return builder.build();
  }

  public static Query prepareOrQuery(List<Query> queryList) {

    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (Query query : queryList) {
      builder.add(query, BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }

  public static Query prepareCombinedFilterMustQuery(Query filterQuery, Query mustQuery) {

    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(filterQuery, BooleanClause.Occur.FILTER);
    // Achtung: Wenn die mustQuery leer ist und wir sie trotzdem hinzufügen würden, werden keine
    // Ergebnisse zurückgegeben
    if (!mustQuery.toString().isEmpty()) builder.add(mustQuery, BooleanClause.Occur.MUST);
    return builder.build();
  }
}
