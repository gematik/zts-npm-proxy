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

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;

@Slf4j
public class StringParameterQueryGenerator {

  private StringParameterQueryGenerator() {}

  public static Query prepareExactQuery(String paramName, String param) {

    String inField = paramName + SEPARATOR + SUFFIX_STRING;
    return new TermQuery(new Term(inField, param));
  }

  public static Query preparePrefixQuery(String paramName, String param) {

    String inField = paramName + SEPARATOR + SUFFIX_STRING;
    return new PrefixQuery(new Term(inField, param));
  }

  public static Query prepareKeywordQuery(String paramName, String keyword) {
    String inField = paramName + SEPARATOR + SUFFIX_STRING;

    return new TermQuery(new Term(inField, keyword.toLowerCase()));
  }

  public static Query prepareKeywordQuery(List<String> fields, String keyword) {
    BooleanQuery.Builder multiFieldQuery = new BooleanQuery.Builder();
    for (String field : fields) {
      String inField = field + SEPARATOR + SUFFIX_STRING;
      TermQuery termQuery = new TermQuery(new Term(inField, keyword.toLowerCase()));
      multiFieldQuery.add(termQuery, BooleanClause.Occur.SHOULD);
    }
    return multiFieldQuery.build();
  }
}
