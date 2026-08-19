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

package de.gematik.zts.npmproxy.model;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Resource;

@Getter
@Setter
@Slf4j
public class FhirPackage {

  public static final String PACKAGE_JSON_FILENAME = "package.json";
  public static final String INDEX_JSON_FILENAME = ".index.json";
  public static final String JSON_FILENAME_SUFFIX = ".json";

  // Erzeuge einen FhirContext für eine spezifische FHIR-Version (z.B. R4)
  static final FhirContext ctx = FhirContext.forR4Cached();

  // Erzeuge einen JSON-Parser
  static final IParser parser = ctx.newJsonParser();

  private FhirPackageManifest manifest;
  private List<Resource> resources;
  private Path packagePath;

  public FhirPackage() {
    this.manifest = null;
    this.resources = new ArrayList<>();
    this.packagePath = null;
  }

  public static Optional<FhirPackage> buildFromTarGz(Path tarGzPath) {
    log.info("loading: {}", tarGzPath);

    // Result-Objekt anlegen
    FhirPackage fhirPackage = new FhirPackage();
    fhirPackage.setPackagePath(tarGzPath);

    // Datei entpacken und Package-Informationen extrahieren
    try (InputStream fileInputStream = Files.newInputStream(tarGzPath);
        GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
        TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzipInputStream)) {

      TarArchiveEntry entry;
      while ((entry = tarInputStream.getNextEntry()) != null) {
        if (entry.getName().endsWith(JSON_FILENAME_SUFFIX)) {
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          byte[] buffer = new byte[4096];
          int length;
          while ((length = tarInputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
          }

          // Konvertiere den ByteArrayOutputStream in einen ByteArrayInputStream
          ByteArrayInputStream byteArrayInputStream =
              new ByteArrayInputStream(outputStream.toByteArray());

          if (entry.getName().endsWith(PACKAGE_JSON_FILENAME)) {

            // JSON-Inhalt in ein Java-Objekt parsen
            ObjectMapper objectMapper = new ObjectMapper();
            FhirPackageManifest packageManifest =
                objectMapper.readValue(byteArrayInputStream, FhirPackageManifest.class);

            fhirPackage.setManifest(packageManifest);
          } else if (entry.getName().endsWith(INDEX_JSON_FILENAME)) {
            // .index.json wird nicht verarbeitet
          } else {
            // JSON-Inhalt in ein FHIR-Resource-Objekt parsen
            Optional<IBaseResource> resource =
                fhirPackage.parseJsonResourceFromByteArray(byteArrayInputStream);

            resource.ifPresent(
                iBaseResource -> fhirPackage.getResources().add((Resource) iBaseResource));
          }
        }
      }

      // Nur wenn ein Manifest gefunden wurde, wird das FhirPackage zurückgegeben
      if (fhirPackage.getManifest() != null) return Optional.of(fhirPackage);

    } catch (IOException e) {
      log.error(
          "Error occurred while processing tar.gz file: {} - {}",
          tarGzPath.getFileName(),
          e.getMessage(),
          e);
    }

    return Optional.empty();
  }

  private Optional<IBaseResource> parseJsonResourceFromByteArray(
      ByteArrayInputStream byteArrayInputStream) {

    IBaseResource resource;

    try {
      // Parse die Ressource
      resource = parser.parseResource(new InputStreamReader(byteArrayInputStream));
      return Optional.of(resource);
    } catch (Exception e) {
      log.error("Error while parsing resource from byte array: {}", e.getMessage(), e);
      return Optional.empty();
    }
  }
}
