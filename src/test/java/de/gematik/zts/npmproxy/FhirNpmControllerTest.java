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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.zts.npmproxy.exceptions.PackageAccessDeniedException;
import de.gematik.zts.npmproxy.exceptions.ServiceUnavailableException;
import de.gematik.zts.npmproxy.model.FhirPackageInfo;
import de.gematik.zts.npmproxy.repository.LuceneBackedPackageRepository;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
class FhirNpmControllerTest {

  @Mock private LuceneBackedPackageRepository packageRepository;
  @Mock private NpmProxyConfiguration properties;
  @Mock private ServerWebExchange exchange;
  @Mock private Authentication authentication;

  @InjectMocks private FhirNpmController fhirNpmController;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  // ================================================================================
  // Tests
  // ================================================================================

  @Test
  void testHandleGetPackageInfoRequest_ServiceUnavailable() {

    // Sicherstellen, dass wir in den Abbruch der Verarbeitung hineinlaufen
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(false);

    // Ausführen der Methode zum Abrufen der Paketinformationen
    Mono<ResponseEntity<Object>> response =
        fhirNpmController.handleGetPackageInfoRequest(
            exchange, authentication, "bfarm.terminologies.test");

    // Prüfen, dass wir eine Response erhalten haben; in unserem Fall muss es sich um eine Exception
    // handeln
    assertNotNull(response);
    assertThrows(ServiceUnavailableException.class, response::block);
  }

  @Test
  void testHandleGetPackageInfoRequest_NoPackageFound() {

    String packageName = "bfarm.terminologien.test";

    // Sicherstellen, dass das Repository als initialisiert gilt und wir kein Paket finden
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(true);
    when(packageRepository.findPackageByName(packageName)).thenReturn(Optional.empty());

    // Mock ServerHttpRequest and URI
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    when(request.getURI()).thenReturn(URI.create("/test/" + packageName));
    when(exchange.getRequest()).thenReturn(request);
    // Ausführen der Methode zum Abrufen der Paketinformationen
    Mono<ResponseEntity<Object>> response =
        fhirNpmController.handleGetPackageInfoRequest(exchange, authentication, packageName);

    // Prüfen, dass wir eine Response erhalten haben; in unserem Fall muss es sich um eine
    // NoResourceFoundException handeln
    assertNotNull(response);
    assertThrows(NoResourceFoundException.class, response::block);
  }

  @Test
  void testHandleGetPackageInfoRequest_Success() {

    // Paketinformationen vorbereiten
    String packageName = "bfarm.terminologien.test";
    FhirPackageInfo fhirPackageInfo = new FhirPackageInfo();
    fhirPackageInfo.setName(packageName);
    String user = "user";

    // Sicherstellen, dass das Repository als initialisiert gilt und wir ein passendes Paket finden
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(true);
    when(packageRepository.findPackageByName(packageName)).thenReturn(Optional.of(fhirPackageInfo));

    // Nutzernamen im Authentication-Objekt setzen
    when(authentication.getName()).thenReturn(user);

    // Ausführen der Methode zum Abrufen der Paketinformationen
    Mono<ResponseEntity<Object>> response =
        fhirNpmController.handleGetPackageInfoRequest(exchange, authentication, packageName);

    assertNotNull(response);
    ResponseEntity<Object> entity = response.block();
    assertNotNull(entity, "ResponseEntity must not be null");
    assertTrue(entity.hasBody(), "ResponseEntity must have a body");
    assertTrue(entity.getBody() instanceof FhirPackageInfo, "Body must be of type FhirPackageInfo");
    assertEquals(HttpStatus.OK, entity.getStatusCode());
    assertEquals(fhirPackageInfo, entity.getBody());
    assertEquals(
        CacheControl.maxAge(600, TimeUnit.SECONDS).cachePublic().getHeaderValue(),
        entity.getHeaders().getCacheControl());

    verify(exchange).getAttributes();
  }

  // ================================================================================

  @Test
  void testHandleGetPackageVersionRequest_ServiceUnavailable() {

    String packageName = "bfarm.terminologien.test";
    String packageVersion = "1.0.0";

    // Sicherstellen, dass wir in den Abbruch der Verarbeitung hineinlaufen
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(false);

    assertThrows(
        ServiceUnavailableException.class,
        () ->
            fhirNpmController.handleGetPackageVersionRequest(
                exchange, authentication, packageName, packageVersion));
  }

  @Test
  void testHandleGetPackageVersionRequest_ProtectedPackage_Unauthenticated() {

    String packageName = "bfarm.terminologien.test";
    String packageVersion = "1.0.0";

    // Sicherstellen, dass das Repository als initialisiert gilt
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(true);

    // Sicherstellen, dass der Nutzer nicht authentifiziert ist
    when(authentication.getName()).thenReturn("anonymous");
    when(authentication.isAuthenticated()).thenReturn(false);

    // Sicherstellen, dass das Paket geschützt ist
    when(packageRepository.isPackageVersionProtected(packageName, packageVersion)).thenReturn(true);

    assertThrows(
        AuthenticationCredentialsNotFoundException.class,
        () ->
            fhirNpmController.handleGetPackageVersionRequest(
                exchange, authentication, packageName, packageVersion));
  }

  @Test
  void testHandleGetPackageVersionRequest_ProtectedPackage_Authenticated_Unauthorized() {

    String packageName = "bfarm.terminologien.test";
    String packageVersion = "1.0.0";

    // Sicherstellen, dass das Repository als initialisiert gilt
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(true);

    // Sicherstellen, dass das Paket geschützt ist
    when(packageRepository.isPackageVersionProtected(packageName, packageVersion)).thenReturn(true);

    // Sicherstellen, dass der Nutzer authentifiziert ist, aber nicht die notwendigen Rechte hat
    when(authentication.getName()).thenReturn(UUID.randomUUID().toString());
    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getAuthorities()).thenReturn(Collections.emptySet());

    assertThrows(
        PackageAccessDeniedException.class,
        () ->
            fhirNpmController.handleGetPackageVersionRequest(
                exchange, authentication, packageName, packageVersion));
  }

  @Test
  void testHandleGetPackageVersionRequest_NoPackageVersionFound() {

    String packageName = "bfarm.terminologien.test";
    String packageVersion = "1.0.0";

    // Sicherstellen, dass das Repository als initialisiert gilt
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(true);

    // Sicherstellen, dass das Paket nicht geschützt ist
    when(packageRepository.isPackageVersionProtected(packageName, packageVersion))
        .thenReturn(false);

    // Sicherstellen, dass der Nutzer authentifiziert ist
    when(authentication.getName()).thenReturn(UUID.randomUUID().toString());

    // Mock ServerHttpRequest and URI
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    when(request.getURI()).thenReturn(URI.create("/test/" + packageName + "/" + packageVersion));
    when(exchange.getRequest()).thenReturn(request);

    // Sicherstellen, dass wir einen Dateipfad zurückbekommen
    when(packageRepository.getPackagePathIndex(packageName, packageVersion))
        .thenReturn(Optional.empty());

    assertThrows(
        NoResourceFoundException.class,
        () ->
            fhirNpmController.handleGetPackageVersionRequest(
                exchange, authentication, packageName, packageVersion));
  }

  @Test
  void testHandleGetPackageVersionRequest_UnprotectedPackage_Success() throws IOException {

    String packageName = "bfarm.terminologien.test";
    String packageVersion = "1.0.0";

    // Sicherstellen, dass das Repository als initialisiert gilt
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(true);

    // Sicherstellen, dass das Paket nicht geschützt ist
    when(packageRepository.isPackageVersionProtected(packageName, packageVersion))
        .thenReturn(false);

    // Sicherstellen, dass der Nutzer authentifiziert ist
    when(authentication.getName()).thenReturn(UUID.randomUUID().toString());

    // Testdatei erstellen und mit zufälligem Inhalt befüllen
    Path testFilePath = Files.createTempFile("testPackage-1.0.0", ".tar.gz");
    byte[] randomBytes = new byte[1024]; // 1 KB zufällige Bytes
    new Random().nextBytes(randomBytes);
    Files.write(testFilePath, randomBytes);

    // Sicherstellen, dass wir den Dateipfad der Testdatei zurückbekommen
    when(packageRepository.getPackagePathIndex(packageName, packageVersion))
        .thenReturn(Optional.of(testFilePath));

    ResponseEntity<Flux<DataBuffer>> response =
        fhirNpmController.handleGetPackageVersionRequest(
            exchange, authentication, packageName, packageVersion);

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
    assertEquals(
        "attachment; filename=\"" + packageName + "-" + packageVersion + ".tar.gz\"",
        response.getHeaders().getContentDisposition().toString());
    assertEquals(
        CacheControl.maxAge(3600, TimeUnit.SECONDS).cachePublic().getHeaderValue(),
        response.getHeaders().getCacheControl());

    byte[] responseBytes = new byte[1024]; // Größe entsprechend anpassen

    response
        .getBody()
        .doOnNext(
            buffer -> {
              buffer.read(responseBytes);
            })
        .doOnComplete(() -> assertArrayEquals(randomBytes, responseBytes))
        .subscribe();

    // Aufräumen
    Files.deleteIfExists(testFilePath);
  }

  @Test
  void testHandleGetPackageVersionRequest_ProtectedPackage_Success() throws IOException {

    String packageName = "bfarm.terminologien.test";
    String packageVersion = "1.0.0";

    // Sicherstellen, dass das Repository als initialisiert gilt
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(true);

    // Sicherstellen, dass das Paket geschützt ist
    when(packageRepository.isPackageVersionProtected(packageName, packageVersion)).thenReturn(true);

    // Sicherstellen, dass der Nutzer authentifiziert ist und auf das Paket zugreifen darf
    when(authentication.getName()).thenReturn(UUID.randomUUID().toString());
    when(authentication.isAuthenticated()).thenReturn(true);

    Collection<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority(packageName));
    when(authentication.getAuthorities())
        .thenAnswer((Answer<Collection<GrantedAuthority>>) invocation -> authorities);

    // Testdatei erstellen und mit zufälligem Inhalt befüllen
    Path testFilePath = Files.createTempFile("testPackage-1.0.0", ".tar.gz");
    byte[] randomBytes = new byte[1024]; // 1 KB zufällige Bytes
    new Random().nextBytes(randomBytes);
    Files.write(testFilePath, randomBytes);

    // Sicherstellen, dass wir den Dateipfad der Testdatei zurückbekommen
    when(packageRepository.getPackagePathIndex(packageName, packageVersion))
        .thenReturn(Optional.of(testFilePath));

    ResponseEntity<Flux<DataBuffer>> response =
        fhirNpmController.handleGetPackageVersionRequest(
            exchange, authentication, packageName, packageVersion);

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
    assertEquals(
        "attachment; filename=\"" + packageName + "-" + packageVersion + ".tar.gz\"",
        response.getHeaders().getContentDisposition().toString());
    assertEquals(
        CacheControl.maxAge(3600, TimeUnit.SECONDS).cachePrivate().getHeaderValue(),
        response.getHeaders().getCacheControl());

    byte[] responseBytes = new byte[1024]; // Größe entsprechend anpassen

    response
        .getBody()
        .doOnNext(
            buffer -> {
              buffer.read(responseBytes);
            })
        .doOnComplete(() -> assertArrayEquals(randomBytes, responseBytes))
        .subscribe();

    // Aufräumen
    Files.deleteIfExists(testFilePath);
  }
}
