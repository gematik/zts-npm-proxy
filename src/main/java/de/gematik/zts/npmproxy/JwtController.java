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

import static de.gematik.zts.npmproxy.NpmProxyConstants.*;

import de.gematik.zts.npmproxy.exceptions.JwtCreationException;
import de.gematik.zts.npmproxy.exceptions.ServiceUnavailableException;
import de.gematik.zts.npmproxy.model.TokenRequest;
import de.gematik.zts.npmproxy.model.TokenResponse;
import de.gematik.zts.npmproxy.repository.LuceneBackedPackageRepository;
import de.gematik.zts.npmproxy.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Implementiert die von Außen aufrufbare Operationen für die Erstellung eines Zugriffstokens */
@RestController
@Order(2)
@Slf4j
@Tag(name = "Token-API", description = "API for generating access tokens")
public class JwtController {

  private final JwtService jwtService;
  private final LuceneBackedPackageRepository packageRepository;

  @Autowired
  public JwtController(JwtService jwtService, LuceneBackedPackageRepository packageRepository) {
    this.jwtService = jwtService;
    this.packageRepository = packageRepository;
  }

  @PostMapping(
      value = "${proxy.token-path}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Generate a token for protected packages",
      description =
          "Endpoint to retrieve a token for accessing protected packages. The token must be included in the Authorization header to retrieve protected packages.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successful response",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad request", content = @Content()),
      })
  public Mono<TokenResponse> generateToken(
      @RequestBody TokenRequest request,
      Authentication authentication,
      ServerWebExchange exchange) {

    // Abbruch der Verarbeitung, falls noch kein initiales Update der Paketinformationen
    // durchgeführt wurde
    if (!packageRepository.isInitialUpdateSucceeded()) {
      return Mono.error(
          new ServiceUnavailableException(
              "Der Dienst wurde nicht korrekt initialisiert. Bitte versuchen Sie es später erneut."));
    }

    return Mono.fromSupplier(
        () -> {

          // Übernahme des Nutzernamens aus dem Token in die Exchange-Attribute
          exchange.getAttributes().put(ATTRIBUTE_USER, authentication.getName());

          // Laden der Liste von Packages aus der Anfrage
          List<String> packageList = request.getPackages();

          // Prüfen der übergebenen Paketliste
          if (packageList == null || packageList.isEmpty()) {
            // Die Liste der Packages darf nicht leer sein
            throw new JwtCreationException(MESSAGE_TOKEN_GENERATION_NO_PACKAGE);
          } else {
            // Alle angegebenen Packages müssen in der Liste der konfigurierten gültigen und
            // geschützten Packages enthalten sein
            for (String p : packageList) {
              // Ermitteln, ob das Paket auf der Liste der geschützten Pakete steht
              boolean isUnProtectedPackage = !packageRepository.isPackageVersionProtected(p, null);
              if (isUnProtectedPackage) {
                throw new JwtCreationException(MESSAGE_TOKEN_GENERATION_INVALID_PACKAGE + p);
              }
            }
          }

          // Vorbereiten der Tokeninformationen:
          // Für alle im Request angegebenen Packages wird ein entsprechender Listeneintrag im
          // packages-Claim erstellt
          List<GrantedAuthority> authorityList = new ArrayList<>();
          for (String pkg : packageList) {
            authorityList.add(new SimpleGrantedAuthority(pkg));
          }

          // Erstellen des UserDetails-Objekts:
          // Hinweis: Wir erstellen lediglich eine Random-UUID als Username (sub-Claim), da wir hier
          // noch kein vollständiges User-Management haben (wollen)
          UserDetails userDetails = new User(UUID.randomUUID().toString(), "", authorityList);

          // Übernahme der generierten UUID in die Exchange-Attribute
          exchange.getAttributes().put(ATTRIBUTE_LOG_MESSAGE, userDetails.getUsername());

          // Generieren des Tokens
          return new TokenResponse(
              jwtService.generateToken(userDetails, CLAIM_NOTE_DOWNLOADBEDINGUNGEN));
        });
  }
}
