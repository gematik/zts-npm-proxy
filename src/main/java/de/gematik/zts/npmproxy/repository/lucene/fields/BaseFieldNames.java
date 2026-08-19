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

package de.gematik.zts.npmproxy.repository.lucene.fields;

public class BaseFieldNames {

  public static final String CONTENT_TYPE = "CONTENT_TYPE";
  public static final String CONTENT_TYPE_PACKAGE = "CONTENT_TYPE_PACKAGE";
  public static final String DOCUMENT_ID = "documentId";
  public static final String PACKAGE = "package";
  public static final String PACKAGE_NAME = "packageName";
  public static final String PACKAGE_VERSION = "packageVersion";
  public static final String PACKAGE_ISPRERELEASE = "packageIsPreRelease";
  public static final String PACKAGE_CANONICAL = "packageCanonical";
  public static final String PACKAGE_FHIRVERSION = "packageFhirVersion";
  public static final String PACKAGE_PROTECTED = "packageProtected";
  public static final String PACKAGE_KEYWORDS = "packageKeywords";
  public static final String PACKAGE_ADDITIONAL_KEYWORDS = "packageAdditionalKeywords";
  public static final String PACKAGE_UNLISTED = "packageUnlisted";
  public static final String PACKAGE_AUTHOR = "author";
  public static final String CANONICAL = "canonical";
  public static final String CANONICAL_VERSION = "canonicalVersion";
  public static final String SEPARATOR = "_";
  public static final String SUFFIX_CONTENT = "content";
  public static final String SUFFIX_STRING = "string";
  public static final String SUFFIX_STRING_SORT = "stringsort";

  private BaseFieldNames() {
    // hide constructor
  }
}
