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

import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class NpmProxyConfiguration {

  // Hostname des Proxy Servers (z.B. 'https://terminologien.bfarm.de')
  // Dieser Name wird in den vermittelten Responses verwendet
  @Value("${proxy.hostname}")
  private String hostName;

  // Pfad zum NPM-Proxy (z.B. '/npm')
  // Dieser Pfad wird für die Dienstkonfiguration genutzt und gleichzeitig in den vermittelten
  // Responses verwendet
  @Value("${proxy.npm-path}")
  private String npmPath;

  // Pfad zum Token-Endpunkt (z.B. '/api/generate-token')
  // Dieser Pfad wird für die Dienstkonfiguration genutzt
  @Value("${proxy.token-path}")
  private String tokenPath;

  // Pfad zum Health-Endpunkt (z.B. '/api/health')
  // Dieser Pfad wird für die Dienstkonfiguration genutzt
  @Value("${proxy.health-path:/api/health}")
  private String healthPath;

  // Pfad zum Feed-Endpunkt (z.B. '/feeds/package-feed.xml')
  // Dieser Pfad wird für die Dienstkonfiguration genutzt
  @Value("${proxy.feed-path-hl7:/feeds/package-feed.xml}")
  private String feedPath;

  // Ziel-URL des Proxys (Adresse der NPM-Registry, z.B.
  // 'https://gitlab.fokus.fraunhofer.de/api/v4/projects/7448/packages/npm')
  @Value("${proxy.target-url}")
  private String targetUrl;

  @Value("${proxy.google.cloud.annotation.processing.enabled:false}")
  private boolean gCloudAnnotationProcessingEnabled;

  // Key für die Signatur und Signaturvalidierung des JWT-Tokens
  @Value("${proxy.key}")
  private String key;

  // Gültige NPM-Pakete, die über den Proxy vermittelt werden dürfen.
  // Nur die in der Liste enthaltenen Pakete dürfen im Token angefordert werden
  @Value("${proxy.protected-packages}")
  private Set<String> protectedPackages;

  // Gültigkeitsdauer des Tokens in Tagen
  @Value("${proxy.validity-duration-in-days}")
  private long validityDurationInDays;

  // backend-mode der NPM-Registry (basicauth, gitlab)
  @Value("${proxy.backend-mode}")
  private String backendMode;

  // Gitlab Token für die Authentifizierung des Proxys gegenüber der gitlab NPM-Registry
  @Value("${proxy.gitlab-token}")
  private String gitlabToken;

  // Username für die Authentifizierung des Proxys gegenüber einer basicauth NPM-Registry
  @Value("${proxy.username}")
  private String username;

  // Password für die Authentifizierung des Proxys gegenüber einer basicauth NPM-Registry
  @Value("${proxy.password}")
  private String password;

  // ##############################
  // Rate Limiting
  @Value("${proxy.rate-limiting.enabled:true}")
  private boolean rateLimitingEnabled;

  @Value("${proxy.rate-limiting.limit:50}")
  private int rateLimitingLimit;

  @Value("${proxy.rate-limiting.window-duration:300000}")
  private long rateLimitingWindowDuration;

  @Value("${proxy.rate-limiting.api-key-header:X-API-Key}")
  private String rateLimitingApiKeyHeader;

  @Value("${proxy.rate-limiting.api-key}")
  private String rateLimitingApiKey;

  // ##############################
  @Value("${proxy.monitored-packages}")
  private Set<String> monitoredPackages;

  @Value("${proxy.package-cache-dir}")
  private String packageCacheDir;

  public String getPackageCacheDir() {
    if (StringUtils.isEmpty(packageCacheDir)) {
      return System.getProperty("java.io.tmpdir");
    }
    return packageCacheDir;
  }
}
