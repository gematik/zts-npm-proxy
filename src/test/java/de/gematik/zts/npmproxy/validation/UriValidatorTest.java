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

package de.gematik.zts.npmproxy.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintValidatorContext;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UriValidatorTest {

  private UriValidator uriValidator;

  @Mock private ConstraintValidatorContext context;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    uriValidator = new UriValidator();
  }

  // Parameterized test for valid URIs
  @ParameterizedTest
  @MethodSource("provideValidUris")
  void testIsValid_WithValidUris(String uri) {
    assertThat(uriValidator.isValid(uri, context)).isTrue();
  }

  // Parameterized test for invalid URIs
  @ParameterizedTest
  @MethodSource("provideInvalidUris")
  void testIsValid_WithInvalidUris(String uri) {
    assertThat(uriValidator.isValid(uri, context)).isFalse();
  }

  // Parameterized test for null and empty values
  @ParameterizedTest
  @MethodSource("provideNullAndEmptyValues")
  void testIsValid_WithNullAndEmptyValues(String uri) {
    assertThat(uriValidator.isValid(uri, context)).isTrue();
  }

  // Method sources for test data

  static Stream<String> provideValidUris() {
    return Stream.of(
        "http://example.com/resource",
        "https://example.com/resource",
        "ftp://example.com/resource",
        "mailto:user@example.com",
        "urn:isbn:0451450523",
        "urn:uuid:123e4567-e89b-12d3-a456-426614174000",
        "http://例子.测试", // Internationalized domain name
        "http://example.com:8080/resource", // With port
        "http://example.com/resource?query=param", // With query
        "http://example.com/resource#section", // With fragment
        "git+ssh://example.com/repo.git", // Scheme with '+'
        "HTTP://EXAMPLE.COM", // Scheme case insensitivity
        "http://user:pass@example.com", // With user info
        "unsupported://example.com" // Unsupported scheme but valid format
        );
  }

  static Stream<String> provideInvalidUris() {
    return Stream.of(
        "www.example.com/resource", // Missing scheme
        "/path/to/resource", // Relative URI
        "http://exa mple.com", // Invalid characters
        "http:", // Scheme only
        "   ", // Whitespace
        ":example", // Null scheme
        "http://example.com/\n", // Control characters
        "http://example.com/%GG", // Invalid percent encoding
        "1http://example.com", // Invalid scheme
        "ht^tp://example.com", // Illegal characters in scheme
        "ht tp://example.com", // Scheme with spaces
        "123://example.com", // Scheme starting with number
        "://example.com" // Empty scheme
        );
  }

  static Stream<String> provideNullAndEmptyValues() {
    return Stream.of(null, "");
  }
}
