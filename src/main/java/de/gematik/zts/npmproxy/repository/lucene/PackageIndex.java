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

import de.gematik.zts.npmproxy.exceptions.PackageIndexException;
import java.io.IOException;
import java.nio.file.Path;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

@Slf4j
public class PackageIndex implements AutoCloseable {

  @Getter private final Directory index;

  public PackageIndex(String indexDir) {
    Path indexDirectory = Path.of(indexDir);
    try {
      log.info("Try to open index at {}", indexDirectory);
      index = FSDirectory.open(indexDirectory);
    } catch (IOException e) {
      throw new PackageIndexException("Error while opening resource index.", e);
    }
  }

  public IndexSearcher createSearcher() throws IOException {
    DirectoryReader reader = DirectoryReader.open(index);
    return new IndexSearcher(reader);
  }

  public PackageIndexWriter createPackageModelIndexWriter(IndexWriterConfig.OpenMode openMode) {
    return new PackageIndexWriter(index, openMode);
  }

  @Override
  public void close() throws IOException {
    index.close();
  }
}
