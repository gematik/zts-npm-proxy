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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class SecureUrlBuilderTest {

  private SecureUrlBuilder secureUrlBuilder;

  @BeforeEach
  void setUp() {
    secureUrlBuilder = new SecureUrlBuilder();
    ReflectionTestUtils.setField(secureUrlBuilder, "baseUrl", "example.com");
    ReflectionTestUtils.setField(secureUrlBuilder, "feedsPath", "/feeds");
  }

  @Test
  void testBuildFeedUrl_WithQueryParams_ReturnsCorrectUrl() {
    // Given
    String queryParams = "publisher=BfArM&name=example";

    // When
    String result = secureUrlBuilder.buildFeedUrl(queryParams);

    // Then
    assertThat(result)
        .isEqualTo("https://example.com/feeds?publisher=BfArM&name=example")
        .startsWith("https://")
        .contains("/feeds")
        .contains("publisher=BfArM")
        .contains("name=example");
  }

  @Test
  void testBuildFeedUrl_WithNullQueryParams_ReturnsBaseUrl() {
    // When
    String result = secureUrlBuilder.buildFeedUrl(null);

    // Then
    assertThat(result).isEqualTo("https://example.com/feeds").doesNotContain("?");
  }

  @Test
  void testBuildFeedUrl_WithEmptyQueryParams_ReturnsBaseUrl() {
    // When
    String result = secureUrlBuilder.buildFeedUrl("");

    // Then
    assertThat(result).isEqualTo("https://example.com/feeds").doesNotContain("?");
  }

  @Test
  void testBuildFeedUrl_HostnameWithHttps_DoesNotDuplicateProtocol() {
    // Given
    ReflectionTestUtils.setField(secureUrlBuilder, "baseUrl", "https://secure.example.com");

    // When
    String result = secureUrlBuilder.buildFeedUrl("publisher=BfArM");

    // Then
    assertThat(result)
        .isEqualTo("https://secure.example.com/feeds?publisher=BfArM")
        .doesNotContain("https://https://");
  }

  @Test
  void testBuildFeedUrl_HostnameWithHttp_PreservesHttpProtocol() {
    // Given
    ReflectionTestUtils.setField(secureUrlBuilder, "baseUrl", "http://insecure.example.com");

    // When
    String result = secureUrlBuilder.buildFeedUrl("publisher=BfArM");

    // Then
    assertThat(result)
        .isEqualTo("http://insecure.example.com/feeds?publisher=BfArM")
        .startsWith("http://");
  }

  @Test
  void testBuildFeedUrl_WithPort_PreservesPort() {
    // Given
    ReflectionTestUtils.setField(secureUrlBuilder, "baseUrl", "example.com:8080");

    // When
    String result = secureUrlBuilder.buildFeedUrl("publisher=BfArM");

    // Then
    assertThat(result)
        .isEqualTo("https://example.com:8080/feeds?publisher=BfArM")
        .contains(":8080");
  }

  @Test
  void testBuildFeedUrl_CustomFeedsPath_UsesCustomPath() {
    // Given
    ReflectionTestUtils.setField(secureUrlBuilder, "feedsPath", "/api/v1/feeds");

    // When
    String result = secureUrlBuilder.buildFeedUrl("publisher=BfArM");

    // Then
    assertThat(result)
        .isEqualTo("https://example.com/api/v1/feeds?publisher=BfArM")
        .contains("/api/v1/feeds");
  }

  // Parameterized tests for valid protocols

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://example.com",
        "https://example.com",
        "example.com",
        "HTTPS://example.com",
        "HTTP://example.com",
        "HtTp://example.com",
        "HtTpS://example.com"
      })
  void testValidateConfiguration_WithValidProtocol_Succeeds(String baseUrl) {
    // Given
    ReflectionTestUtils.setField(secureUrlBuilder, "baseUrl", baseUrl);

    // When/Then - should not throw
    secureUrlBuilder.validateConfiguration();
  }

  // Parameterized tests for invalid protocols

  @ParameterizedTest
  @ValueSource(
      strings = {
        "grpc://example.com",
        "ftp://example.com",
        "ws://example.com",
        "wss://example.com",
        "file://example.com",
        "ssh://example.com",
        "mailto://example.com"
      })
  void testValidateConfiguration_WithInvalidProtocol_ThrowsException(String baseUrl) {
    // Given
    ReflectionTestUtils.setField(secureUrlBuilder, "baseUrl", baseUrl);

    // When/Then
    assertThatThrownBy(() -> secureUrlBuilder.validateConfiguration())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid proxy.hostname")
        .hasMessageContaining("Only http:// and https:// are supported");
  }
}
