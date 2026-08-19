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

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

/**
 * Tool zur Generierung eines HMAC-Schlüssels aus einem Passwort. Das Tool kann eingesetzt werden,
 * wenn es mal wieder Zeit wird, einen neuen Dienstschlüssel zu generieren. Vermutlich wird das in
 * nächster Zeit aber nicht so richtig relevant werden, da der Schutzbedarf der Daten eher lachhaft
 * ist und unsere Token lediglich zum "Durchsetzen" der Downloadbedingungen eine Rolle spielen.
 */
@Slf4j
public class HmacKeyGeneration {

  public static final String PBKDF_ALGORITHM = "PBKDF2WithHmacSHA256";
  public static final String HMAC_ALGORITHM = "HmacSHA256";
  public static final int ITERATIONS = 65536;
  public static final int KEYLENGTH = 256;

  public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeySpecException {
    // get the password from arguments
    if (args.length != 1) {
      log.error("Please provide the password as argument.");
      throw new IllegalArgumentException("Please provide the password as argument.");
    }
    String password = args[0];

    // Salt
    byte[] salt = generateSalt();
    String encodedSalt = Base64.getEncoder().encodeToString(salt);
    log.info("Generated Salt: {}", encodedSalt);

    // Schlüsselgenerierung
    SecretKey key = generateKeyFromPassword(password, salt, ITERATIONS, KEYLENGTH);

    // Schlüssel als Base64-kodierter String anzeigen
    String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
    log.info("Generated Key: {}", encodedKey);
  }

  // Generiert einen Salt
  public static byte[] generateSalt() {
    SecureRandom random = new SecureRandom();
    byte[] salt = new byte[16];
    random.nextBytes(salt);
    return salt;
  }

  // Generiert einen Schlüssel basierend auf Passwort, Salt, Iterationen und Schlüssellänge
  public static SecretKey generateKeyFromPassword(
      String password, byte[] salt, int iterations, int keyLength)
      throws NoSuchAlgorithmException, InvalidKeySpecException {
    KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLength);
    SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF_ALGORITHM);
    byte[] keyBytes = factory.generateSecret(spec).getEncoded();
    return new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
  }
}
