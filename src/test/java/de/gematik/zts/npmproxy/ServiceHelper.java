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

package de.gematik.zts.npmproxy;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.with;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class ServiceHelper {

  private static boolean checkServiceReady(String url, int currentTry) {
    System.out.println("Warten auf Service, Versuch: " + (currentTry + 1));
    WebClient webClient = WebClient.create();

    try {
      // Anfrage senden und auf HTTP 200 prüfen
      webClient
          .get()
          .uri(url)
          .retrieve()
          .toBodilessEntity()
          .block(); // Blockiert, bis die Antwort eingeht

      System.out.println("Service ist bereit, Versuch: " + (currentTry + 1));
      return true;
    } catch (WebClientResponseException e) {
      System.out.println("Warten auf Service, Versuch: " + (currentTry + 1) + " fehlgeschlagen.");
      // Nur Fehlerstatus auswerten
      if (e.getStatusCode() != HttpStatusCode.valueOf(503)) {
        throw new IllegalStateException(
            "Unerwarteter Fehler beim Warten auf den Service: " + e.getMessage(), e);
      }
    } catch (Exception e) {
      // Sonstige Fehler, z. B. Verbindung nicht möglich
      System.out.println("Warten auf Service, Versuch: " + (currentTry + 1) + " fehlgeschlagen.");
    }

    return false;
  }

  public static void retryUntilReady(String url, int maxRetries, int delayInSeconds) {

    AtomicInteger count = new AtomicInteger();
    with()
        .pollInterval(delayInSeconds, SECONDS)
        .timeout((long) maxRetries * delayInSeconds, SECONDS)
        .await()
        .until(() -> checkServiceReady(url, count.get()) || count.getAndIncrement() >= maxRetries);
  }

  public static Path copyTestPackage(String packageFile, String copyToLocation) {

    Path packagePath = Paths.get(packageFile);

    // Zielpfad im Dateisystem
    Path targetPath = Paths.get(copyToLocation + "/" + packagePath.getFileName());

    // Datei aus dem Ressourcenverzeichnis laden
    try (InputStream resourceStream =
        ServiceHelper.class.getClassLoader().getResourceAsStream(packageFile)) {

      if (resourceStream == null) {
        throw new FileNotFoundException("Ressource nicht gefunden: " + packageFile);
      }

      // Datei zum Zielpfad kopieren
      Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
      return targetPath;
    } catch (IOException e) {
      throw new IllegalStateException("Fehler beim Kopieren der Testdatei: " + e.getMessage(), e);
    }
  }
}
