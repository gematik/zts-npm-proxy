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

package de.gematik.zts.npmproxy.tools;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileHelper {

  private FileHelper() {
    // Utility-Klasse
  }

  private static final String DIGEST_ALGORITHM = "SHA-1";

  public static String calculateFileHash(Path path) throws NoSuchAlgorithmException, IOException {
    MessageDigest sha1Digest = MessageDigest.getInstance(DIGEST_ALGORITHM);

    try (FileChannel fileChannel = FileChannel.open(path)) {
      ByteBuffer buffer = ByteBuffer.allocateDirect(8192);

      while (fileChannel.read(buffer) != -1) {
        buffer.flip();
        sha1Digest.update(buffer);
        buffer.clear();
      }
    }

    byte[] sha1Bytes = sha1Digest.digest();
    StringBuilder sha1Hex = new StringBuilder();
    for (byte b : sha1Bytes) {
      sha1Hex.append(String.format("%02x", b));
    }

    return sha1Hex.toString();
  }
}
