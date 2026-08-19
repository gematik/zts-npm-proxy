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

package de.gematik.zts.npmproxy.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class SecureUrlBuilder {

  @Value("${proxy.hostname}")
  private String baseUrl;

  @Value("${proxy.feeds-path:/feeds}")
  private String feedsPath;

  @PostConstruct
  public void validateConfiguration() {
    if (baseUrl.contains("://")) {
      String protocol = baseUrl.substring(0, baseUrl.indexOf("://")).toLowerCase();
      if (!protocol.equals("http") && !protocol.equals("https")) {
        throw new IllegalArgumentException(
            "Invalid proxy.hostname: Only http:// and https:// are supported, got: " + baseUrl);
      }
    }
    log.info("SecureUrlBuilder initialized with baseUrl: {}", baseUrl);
  }

  /**
   * Builds a secure URL for feeds using configured base URL
   *
   * @param queryParams Query parameters to append
   * @return Secure URL string
   */
  public String buildFeedUrl(String queryParams) {
    // Ensure baseUrl has a protocol
    String fullBaseUrl = baseUrl;
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
      fullBaseUrl = "https://" + baseUrl;
    }

    // Use fromUriString instead of fromHttpUrl (non-deprecated)
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(fullBaseUrl).path(feedsPath);

    if (queryParams != null && !queryParams.isEmpty()) {
      // Use query() method to add the entire query string at once
      builder.query(queryParams);
    }

    return builder.build().toUriString();
  }
}
