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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.zts.npmproxy.feeds.DynamicFeedGenerator;
import de.gematik.zts.npmproxy.model.FeedType;
import de.gematik.zts.npmproxy.model.FhirPackageVersionInfo;
import de.gematik.zts.npmproxy.repository.LuceneBackedPackageRepository;
import de.gematik.zts.npmproxy.security.SecureUrlBuilder;
import java.util.Collections;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Integration tests for {@link DynamicFeedController}. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.active=test"})
@TestPropertySource(locations = "classpath:application-test.properties")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
@Import(DynamicFeedControllerIT.TestConfig.class)
class DynamicFeedControllerIT {

  @Autowired private LuceneBackedPackageRepository packageRepository;

  @Autowired private DynamicFeedGenerator feedGenerator;

  @Autowired private SecureUrlBuilder secureUrlBuilder;

  private WebTestClient webTestClient;

  @Value("${local.server.port}")
  private int port;

  @BeforeEach
  void setUp() {
    String baseUrl = "http://localhost:" + port;
    log.info("Setting up tests on: {}", baseUrl);
    webTestClient = WebTestClient.bindToServer().baseUrl(baseUrl).build();

    // Reset mocks
    Mockito.reset(packageRepository, feedGenerator, secureUrlBuilder);

    // By default, assume the repository is ready unless overridden in a test
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(true);

    // Mock SecureUrlBuilder to return a predictable URL
    when(secureUrlBuilder.buildFeedUrl(any()))
        .thenAnswer(
            invocation -> {
              String queryParams = invocation.getArgument(0);
              String baseSecureUrl = "https://test-domain.com/feeds";
              return queryParams != null && !queryParams.isEmpty()
                  ? baseSecureUrl + "?" + queryParams
                  : baseSecureUrl;
            });
  }

  @Test
  void testHandleGetDynamicRssFeed_RepositoryNotInitialized() {
    // Simulate service not ready
    when(packageRepository.isInitialUpdateSucceeded()).thenReturn(false);

    webTestClient
        .get()
        .uri("/feeds")
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertThat(responseBody).contains("Der Dienst wurde nicht korrekt initialisiert");
            });
  }

  @Test
  @SneakyThrows
  void testHandleGetDynamicRssFeed_RepositoryInitialized() {

    FhirPackageVersionInfo testInfo = new FhirPackageVersionInfo();
    testInfo.setName("example-package");
    testInfo.setVersion("1.0.0");
    // Simulate repository returning package version infos
    when(packageRepository.getPackageVersionInfos(any(), any(), any(), any()))
        .thenReturn(List.of(testInfo));

    // Simulate feed generator output
    String expectedXml = "<rss>some test content</rss>";
    when(feedGenerator.createFeed(any(), any(), any(), any(), any(), any(FeedType.class), any()))
        .thenReturn(expectedXml);

    webTestClient
        .get()
        .uri("/feeds")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith("application/xml")
        .expectBody(String.class)
        .value(
            body -> {
              assertThat(body).contains("some test content");
            });

    // Verify that SecureUrlBuilder was called
    verify(secureUrlBuilder, atLeastOnce()).buildFeedUrl(any());

    Mockito.verify(feedGenerator, atLeastOnce())
        .createFeed(any(), any(), any(), any(), any(), any(FeedType.class), any());
  }

  @Test
  @SneakyThrows
  void testHandleGetDynamicRssFeedWithMaliciousHost_IsDeniedByWebFilter() {
    FhirPackageVersionInfo testInfo = new FhirPackageVersionInfo();
    testInfo.setName("example-package");

    when(packageRepository.getPackageVersionInfos(any(), any(), any(), any()))
        .thenReturn(List.of(testInfo));
    when(feedGenerator.createFeed(any(), any(), any(), any(), any(), any(FeedType.class), any()))
        .thenReturn("<rss>test</rss>");

    webTestClient
        .get()
        .uri("/feeds?publisher=BfArM")
        .header("Host", "malicious-host.com") // Attempt to inject malicious host
        .exchange()
        .expectStatus()
        .isForbidden(); // Should be blocked by HostValidationFilter

    // Verify that neither the repository nor feed generator were called
    // because the request was blocked at the filter level
    verify(packageRepository, Mockito.never()).getPackageVersionInfos(any(), any(), any(), any());
    verify(feedGenerator, Mockito.never())
        .createFeed(any(), any(), any(), any(), any(), any(FeedType.class), any());
    verify(secureUrlBuilder, Mockito.never()).buildFeedUrl(any());
  }

  @Test
  @SneakyThrows
  void testHandleGetDynamicRssFeedWithNoHost_ReturnsBadRequest() {
    FhirPackageVersionInfo testInfo = new FhirPackageVersionInfo();
    testInfo.setName("example-package");

    when(packageRepository.getPackageVersionInfos(any(), any(), any(), any()))
        .thenReturn(List.of(testInfo));
    when(feedGenerator.createFeed(any(), any(), any(), any(), any(), any(FeedType.class), any()))
        .thenReturn("<rss>test</rss>");

    webTestClient
        .mutate()
        .defaultHeader("Host") // Remove default Host header
        .build()
        .get()
        .uri("/feeds?publisher=BfArM")
        .exchange()
        .expectStatus()
        .isBadRequest();

    verify(packageRepository, Mockito.never()).getPackageVersionInfos(any(), any(), any(), any());
    verify(feedGenerator, Mockito.never())
        .createFeed(any(), any(), any(), any(), any(), any(FeedType.class), any());
    verify(secureUrlBuilder, Mockito.never()).buildFeedUrl(any());
  }

  @Test
  @SneakyThrows
  void testHandleGetDynamicRssFeed_UsesSecureUrlBuilderWithValidHost() {
    FhirPackageVersionInfo testInfo = new FhirPackageVersionInfo();
    testInfo.setName("example-package");

    when(packageRepository.getPackageVersionInfos(any(), any(), any(), any()))
        .thenReturn(List.of(testInfo));
    when(feedGenerator.createFeed(any(), any(), any(), any(), any(), any(FeedType.class), any()))
        .thenReturn("<rss>test</rss>");

    // Use a valid host (localhost or 127.0.0.1 based on your test config)
    webTestClient
        .get()
        .uri("/feeds?publisher=BfArM")
        // Don't set Host header - it will use the default (localhost:port)
        .exchange()
        .expectStatus()
        .isOk();

    // Capture the URL passed to feedGenerator
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(feedGenerator)
        .createFeed(any(), any(), any(), any(), urlCaptor.capture(), any(FeedType.class), any());

    String capturedUrl = urlCaptor.getValue();

    // Verify that the URL uses the secure domain from configuration
    assertThat(capturedUrl).startsWith("https://test-domain.com/feeds").contains("publisher=BfArM");

    // Verify SecureUrlBuilder was called with correct query params
    verify(secureUrlBuilder).buildFeedUrl("publisher=BfArM");
  }

  @Test
  @SneakyThrows
  void testHandleGetDynamicRssFeed_SecureUrlBuilderCalledWithQueryParams() {
    when(packageRepository.getPackageVersionInfos(any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    when(feedGenerator.createFeed(any(), any(), any(), any(), any(), any(FeedType.class), any()))
        .thenReturn("<rss>test</rss>");

    webTestClient
        .get()
        .uri("/feeds?publisher=BfArM&packageName=test.package&keyword=TEST")
        .exchange()
        .expectStatus()
        .isOk();

    // Verify SecureUrlBuilder was called with the full query string
    verify(secureUrlBuilder).buildFeedUrl("publisher=BfArM&packageName=test.package&keyword=TEST");
  }

  // ------------------------------------------------------------------------------------------
  // Test publisher param (valid / invalid) - e.g., underscores vs. special chars
  // ------------------------------------------------------------------------------------------
  @Test
  void testHandleGetDynamicRssFeed_InvalidPublisher() {
    // If "publisher" doesn't match your REGEXP_FEED_PUBLISHER => expect 400 (bad request)
    String invalidPublisher = "###BadPublisher###";

    webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder.path("/feeds").queryParam("publisher", invalidPublisher).build())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertThat(responseBody).contains(MESSAGE_REGEXP_FEED_PUBLISHER);
            });
  }

  @Test
  @SneakyThrows
  void testHandleGetDynamicRssFeed_ValidPublisher() {
    // If publisher is valid, we expect an OK response
    String validPublisher = "BfArM";
    FhirPackageVersionInfo testInfo = new FhirPackageVersionInfo();
    testInfo.setName("some-package");
    when(packageRepository.getPackageVersionInfos(eq(validPublisher), any(), any(), any()))
        .thenReturn(List.of(testInfo));
    when(feedGenerator.createFeed(any(), eq(validPublisher), any(), any(), any(), any(), any()))
        .thenReturn("<rss>validPublisher</rss>");

    webTestClient
        .get()
        .uri(
            uriBuilder -> uriBuilder.path("/feeds").queryParam("publisher", validPublisher).build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .consumeWith(response -> assertThat(response.getResponseBody()).contains("validPublisher"));
  }

  // ------------------------------------------------------------------------------------------
  // Test packageName param (invalid pattern) => expect 400
  // ------------------------------------------------------------------------------------------
  @Test
  void testHandleGetDynamicRssFeed_InvalidPackageName() {
    String invalidName = "somePackage###invalid";

    webTestClient
        .get()
        .uri(uriBuilder -> uriBuilder.path("/feeds").queryParam("packageName", invalidName).build())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertThat(responseBody).contains(MESSAGE_REGEXP_PACKAGE_NAME);
            });
  }

  // ------------------------------------------------------------------------------------------
  // Test keyword param (invalid) => expect 400
  // ------------------------------------------------------------------------------------------
  @Test
  void testHandleGetDynamicRssFeed_InvalidKeyword() {
    String invalidKeyword = "Bad##Keyword";

    webTestClient
        .get()
        .uri(uriBuilder -> uriBuilder.path("/feeds").queryParam("keyword", invalidKeyword).build())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertThat(responseBody).contains(MESSAGE_REGEXP_KEYWORD);
            });
  }

  // ------------------------------------------------------------------------------------------
  // Test feed type = publication param
  // ------------------------------------------------------------------------------------------
  @Test
  @SneakyThrows
  void testHandleGetDynamicRssFeed_FeedTypePublication() {
    when(packageRepository.getPackageVersionInfos(any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());

    String expectedXml = "<rss>publication feed</rss>";
    when(feedGenerator.createFeed(
            any(), any(), any(), any(), any(), eq(FeedType.PUBLICATION), any()))
        .thenReturn(expectedXml);

    webTestClient
        .get()
        .uri(uriBuilder -> uriBuilder.path("/feeds").queryParam("type", "publication").build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertThat(responseBody).contains("publication feed");
            });
  }

  // ------------------------------------------------------------------------------------------
  // Test repository throwing an Exception => expect 503
  // ------------------------------------------------------------------------------------------
  @Test
  void testHandleGetDynamicRssFeed_RepoThrowsException() {
    when(packageRepository.getPackageVersionInfos(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("simulated exception"));

    webTestClient
        .get()
        .uri("/feeds")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String responseBody = response.getResponseBody();
              assertThat(responseBody).contains(PROBLEMDETAILS_TITLE_INTERNAL_SERVER_ERROR);
            });
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    @Primary
    public LuceneBackedPackageRepository packageRepository() {
      return Mockito.mock(LuceneBackedPackageRepository.class);
    }

    @Bean
    @Primary
    public DynamicFeedGenerator mockDynamicFeedGenerator() {
      return Mockito.mock(DynamicFeedGenerator.class);
    }

    @Bean
    @Primary
    public SecureUrlBuilder mockSecureUrlBuilder() {
      return Mockito.mock(SecureUrlBuilder.class);
    }

    @Bean
    public DynamicFeedController dynamicFeedController(
        LuceneBackedPackageRepository packageRepository,
        DynamicFeedGenerator mockDynamicFeedGenerator,
        SecureUrlBuilder mockSecureUrlBuilder) {
      return new DynamicFeedController(
          packageRepository, mockDynamicFeedGenerator, mockSecureUrlBuilder);
    }
  }
}
