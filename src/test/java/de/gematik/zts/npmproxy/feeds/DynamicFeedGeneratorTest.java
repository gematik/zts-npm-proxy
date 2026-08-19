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

package de.gematik.zts.npmproxy.feeds;

import static de.gematik.zts.npmproxy.feeds.DynamicFeedGenerator.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import de.gematik.zts.npmproxy.exceptions.FeedGenerationException;
import de.gematik.zts.npmproxy.model.FeedType;
import de.gematik.zts.npmproxy.model.FhirPackageAuthor;
import de.gematik.zts.npmproxy.model.FhirPackageVersionDistInfo;
import de.gematik.zts.npmproxy.model.FhirPackageVersionInfo;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@ExtendWith(MockitoExtension.class)
class DynamicFeedGeneratorTest {

  @InjectMocks private DynamicFeedGenerator dynamicFeedGenerator;

  @Test
  @SneakyThrows
  void testCreateFeed_emptyList_shouldReturnEmptyFeedContent()  {
    String result =
        dynamicFeedGenerator.createFeed(
            List.of(),
            null, // publisher
            null, // packageName
            null, // keyword
            "http://test-request-url",
            FeedType.PACKAGE,
            false);

    assertThat(result).isNotNull().contains("<rss").doesNotContain("<item>");
  }

  @Test
  @SneakyThrows
  void testCreateFeed_nullPublisherOrParams_shouldNotFail()  {
    FhirPackageVersionInfo info = new FhirPackageVersionInfo();
    info.setName("another-package");
    info.setVersion("1.0.0");
    info.setCreatedAt(Instant.now());

    String result =
        dynamicFeedGenerator.createFeed(
            List.of(info), null, null, null, "http://test-request-url", FeedType.PACKAGE, false);
    assertThat(result).isNotNull().contains("another-package#1.0.0").contains("<item>");
  }

  @Test
  void testCreateFeed_throwExceptionFromMapToSyndEntries() {
    // create a spy of DynamicFeedGenerator to mock the mapToSyndEntries method
    DynamicFeedGenerator generatorSpy = spy(new DynamicFeedGenerator());

    // throw a FeedGenerationException when mapToSyndEntries is called
    doThrow(new FeedGenerationException("Mocked exception"))
        .when(generatorSpy)
        .mapToSyndEntries(any(FhirPackageVersionInfo.class), any(FeedType.class));

    var packageInfos = List.of(new FhirPackageVersionInfo());

    // check that the exception is thrown when createFeed is called
    assertThatThrownBy(
            () ->
                generatorSpy.createFeed(
                    packageInfos,
                    "publisher",
                    "packageName",
                    "keyword",
                    "http://test-url",
                    FeedType.PUBLICATION,
                    false))
        .isInstanceOf(FeedGenerationException.class)
        .hasMessageContaining("Mocked exception");
  }

  @Test
  void testFeedContent_package_withSingleItem() throws Exception {
    FhirPackageVersionInfo singleInfo = createExamplePackageVersionInfo();

    String xmlContent =
        dynamicFeedGenerator.createFeed(
            List.of(singleInfo),
            "MyPublisher",
            "my-publication-package",
            "keyword",
            "http://test-request-url",
            FeedType.PACKAGE,
            false);

    DynamicFeedGenerator.Rfc822DateFormat rfc822DateFormat =
        new DynamicFeedGenerator.Rfc822DateFormat();

    String expectedPubDate = rfc822DateFormat.format(Date.from(singleInfo.getCreatedAt()));

    // Parse the string as XML
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document document =
        builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

    // Create an XPath to evaluate expressions
    XPath xPath = XPathFactory.newInstance().newXPath();

    // ======================
    // Channel-level checks
    // ======================
    // channel/title
    String channelTitle = xPath.evaluate("/rss/channel/title", document);
    assertThat(channelTitle)
        .isNotEmpty()
        .contains(FEED_TITLE_PUBLISHER)
        .contains("MyPublisher")
        .contains(FEED_TITLE_PACKAGE_NAME)
        .contains("my-publication-package")
        .contains(FEED_TITLE_KEYWORDS)
        .contains("keyword");

    // channel/link
    String channelLink = xPath.evaluate("/rss/channel/link", document);
    assertThat(channelLink).isEqualTo("http://test-request-url");

    // channel/description
    String channelDescription = xPath.evaluate("/rss/channel/description", document);
    assertThat(channelDescription)
        .isNotEmpty()
        .contains(FEED_TITLE_PUBLISHER)
        .contains("MyPublisher")
        .contains(FEED_TITLE_PACKAGE_NAME)
        .contains("my-publication-package")
        .contains(FEED_TITLE_KEYWORDS)
        .contains("keyword");

    // channel/language
    String channelLanguage = xPath.evaluate("/rss/channel/language", document);
    assertThat(channelLanguage).isEqualTo("de");

    // channel/pubDate
    String channelPubDate = xPath.evaluate("/rss/channel/pubDate", document);
    assertThat(channelPubDate).isNotEmpty().isEqualTo(expectedPubDate);

    // channel/generator
    String channelGenerator = xPath.evaluate("/rss/channel/generator", document);
    assertThat(channelGenerator).isEqualTo("ZTS Publication Tooling");

    // channel/atom:link — using local-name() to avoid setting namespace context
    String atomLinkHref =
        xPath.evaluate(
            "/rss/channel/*[local-name()='link' and namespace-uri()='http://www.w3.org/2005/Atom']/@href",
            document);
    assertThat(atomLinkHref).isEqualTo("http://test-request-url");

    String atomLinkRel =
        xPath.evaluate(
            "/rss/channel/*[local-name()='link' and namespace-uri()='http://www.w3.org/2005/Atom']/@rel",
            document);
    assertThat(atomLinkRel).isEqualTo("self");

    String atomLinkType =
        xPath.evaluate(
            "/rss/channel/*[local-name()='link' and namespace-uri()='http://www.w3.org/2005/Atom']/@type",
            document);
    assertThat(atomLinkType).isEqualTo("application/rss+xml");

    // channel/ttl
    String channelTtl = xPath.evaluate("/rss/channel/ttl", document);
    assertThat(channelTtl).isEqualTo("600");

    // channel/lastBuildDate
    String channelLastBuildDate = xPath.evaluate("/rss/channel/lastBuildDate", document);
    assertThat(channelLastBuildDate).isNotEmpty().isEqualTo(expectedPubDate);

    // ======================
    // Item-level checks
    // ======================
    // Get the number of items
    String itemCountStr = xPath.evaluate("count(/rss/channel/item)", document);
    int itemCount = Integer.parseInt(itemCountStr);
    // There should be exactly one item in the feed
    assertThat(itemCount).isEqualTo(1);
    // item/title
    String itemTitle = xPath.evaluate("/rss/channel/item/title", document);
    assertThat(itemTitle)
        .isNotEmpty()
        .isEqualTo(singleInfo.getName().concat("#").concat(singleInfo.getVersion()));

    // item/link
    String itemLink = xPath.evaluate("/rss/channel/item/link", document);
    assertThat(itemLink).isEqualTo("http://example.com/tarball.tgz");

    // version
    String itemVersion = xPath.evaluate("/rss/channel/item/*[local-name()='version']", document);
    assertThat(itemVersion).isEqualTo("4.0.1");

    // artifactType
    String itemArtifactType =
        xPath.evaluate("/rss/channel/item/*[local-name()='artifactType']", document);
    assertThat(itemArtifactType).isEqualTo("package");

    // publishAction
    String itemPublishAction =
        xPath.evaluate("/rss/channel/item/*[local-name()='publishAction']", document);
    assertThat(itemPublishAction).isEqualTo("publish");

    // item/description
    String itemDescription = xPath.evaluate("/rss/channel/item/description", document);
    assertThat(itemDescription).contains("Sample publication description");

    // item/pubDate
    String itemPubDate = xPath.evaluate("/rss/channel/item/pubDate", document);
    assertThat(itemPubDate).isNotEmpty().isEqualTo(expectedPubDate);

    // Unique GUID: name#version + pubDate
    String uuidSource =
        singleInfo.getName() + "#" + singleInfo.getVersion() + singleInfo.getCreatedAt();
    String expectedGuid =
        PREFIX_URN_UUID + UUID.nameUUIDFromBytes(uuidSource.getBytes(StandardCharsets.UTF_8));
    // item/guid
    String itemGuid = xPath.evaluate("/rss/channel/item/guid", document);
    assertThat(itemGuid).isNotEmpty().isEqualTo(expectedGuid);

    // item/dc:creator
    String itemCreator =
        xPath.evaluate(
            "/rss/channel/item/*[local-name()='creator' and namespace-uri()='http://purl.org/dc/elements/1.1/']",
            document);
    assertThat(itemCreator).isEqualTo("MyPublisher");
  }

  @Test
  void testFeedContent_publication_withSingleItem() throws Exception {
    // Prepare test data
    FhirPackageVersionInfo singleInfo = createExamplePackageVersionInfo();

    // Generate feed for PUBLICATION
    String xmlContent =
        dynamicFeedGenerator.createFeed(
            List.of(singleInfo),
            "MyPublisher",
            "my-publication-package",
            "keyword",
            "http://test-request-url",
            FeedType.PUBLICATION,
            true);

    DynamicFeedGenerator.Rfc822DateFormat rfc822DateFormat =
        new DynamicFeedGenerator.Rfc822DateFormat();

    String expectedPubDate = rfc822DateFormat.format(Date.from(singleInfo.getCreatedAt()));

    // Parse the string as XML
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document document =
        builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

    // Create an XPath to evaluate expressions
    XPath xPath = XPathFactory.newInstance().newXPath();

    // ======================
    // Channel-level checks
    // ======================
    // channel/title
    String channelTitle = xPath.evaluate("/rss/channel/title", document);
    assertThat(channelTitle)
        .isNotEmpty()
        .contains(FEED_TITLE_PREFIX_PUBLISH_TO_HL7)
        .contains(FEED_TITLE_PUBLISHER)
        .contains("MyPublisher")
        .contains(FEED_TITLE_PACKAGE_NAME)
        .contains("my-publication-package")
        .contains(FEED_TITLE_KEYWORDS)
        .contains("keyword");

    // channel/link
    String channelLink = xPath.evaluate("/rss/channel/link", document);
    assertThat(channelLink).isEqualTo("http://test-request-url");

    // channel/description
    String channelDescription = xPath.evaluate("/rss/channel/description", document);
    assertThat(channelDescription)
        .isNotEmpty()
        .contains(FEED_TITLE_PREFIX_PUBLISH_TO_HL7)
        .contains(FEED_TITLE_PUBLISHER)
        .contains("MyPublisher")
        .contains(FEED_TITLE_PACKAGE_NAME)
        .contains("my-publication-package")
        .contains(FEED_TITLE_KEYWORDS)
        .contains("keyword");

    // channel/language
    String channelLanguage = xPath.evaluate("/rss/channel/language", document);
    assertThat(channelLanguage).isEqualTo("de");

    // channel/pubDate
    String channelPubDate = xPath.evaluate("/rss/channel/pubDate", document);
    assertThat(channelPubDate).isNotEmpty().isEqualTo(expectedPubDate);

    // channel/generator
    String channelGenerator = xPath.evaluate("/rss/channel/generator", document);
    assertThat(channelGenerator).isEqualTo("ZTS Publication Tooling");

    // channel/atom:link — using local-name() to avoid setting namespace context
    String atomLinkHref =
        xPath.evaluate(
            "/rss/channel/*[local-name()='link' and namespace-uri()='http://www.w3.org/2005/Atom']/@href",
            document);
    assertThat(atomLinkHref).isEqualTo("http://test-request-url");

    String atomLinkRel =
        xPath.evaluate(
            "/rss/channel/*[local-name()='link' and namespace-uri()='http://www.w3.org/2005/Atom']/@rel",
            document);
    assertThat(atomLinkRel).isEqualTo("self");

    String atomLinkType =
        xPath.evaluate(
            "/rss/channel/*[local-name()='link' and namespace-uri()='http://www.w3.org/2005/Atom']/@type",
            document);
    assertThat(atomLinkType).isEqualTo("application/rss+xml");

    // channel/ttl
    String channelTtl = xPath.evaluate("/rss/channel/ttl", document);
    assertThat(channelTtl).isEqualTo("600");

    // channel/lastBuildDate
    String channelLastBuildDate = xPath.evaluate("/rss/channel/lastBuildDate", document);
    assertThat(channelLastBuildDate).isNotEmpty().isEqualTo(expectedPubDate);

    // ======================
    // Item-level checks
    // ======================

    // Get the number of items
    String itemCountStr = xPath.evaluate("count(/rss/channel/item)", document);
    int itemCount = Integer.parseInt(itemCountStr);
    // There should be exactly one item in the feed
    assertThat(itemCount).isEqualTo(1);

    // item/title
    String itemTitle = xPath.evaluate("/rss/channel/item/title", document);
    assertThat(itemTitle).isNotEmpty().contains("my-publication-package (2.1.0)");

    // item/link
    String itemLink = xPath.evaluate("/rss/channel/item/link", document);
    assertThat(itemLink).isEqualTo("http://example.com/package-info");

    // version
    String itemVersion = xPath.evaluate("/rss/channel/item/*[local-name()='version']", document);
    assertThat(itemVersion).isEqualTo("4.0.1");

    // item/description
    String itemDescription = xPath.evaluate("/rss/channel/item/description", document);
    assertThat(itemDescription).contains("Sample publication description");

    // item/pubDate
    String itemPubDate = xPath.evaluate("/rss/channel/item/pubDate", document);
    assertThat(itemPubDate).isNotEmpty().isEqualTo(expectedPubDate);

    // item/guid
    // Unique GUID: name#version + pubDate
    String uuidSource =
        singleInfo.getName() + "#" + singleInfo.getVersion() + singleInfo.getCreatedAt();
    String expectedGuid =
        PREFIX_URN_UUID + UUID.nameUUIDFromBytes(uuidSource.getBytes(StandardCharsets.UTF_8));

    String itemGuid = xPath.evaluate("/rss/channel/item/guid", document);
    assertThat(itemGuid).isNotEmpty().isEqualTo(expectedGuid);

    // item/dc:creator
    String itemCreator =
        xPath.evaluate(
            "/rss/channel/item/*[local-name()='creator' and namespace-uri()='http://purl.org/dc/elements/1.1/']",
            document);
    assertThat(itemCreator).isEqualTo("MyPublisher");
  }

  /**
   * Tests the feed content generation for a PUBLICATION feed with two items. This test checks that
   * the feed is generated correctly, when a package is unlisted. In this case, the feed should
   * generate an item for the publication (Neuveröffentlichung) and an item for the deprecation
   * (Veraltet)
   */
  @Test
  void testFeedContent_publication_withTwoItem() throws Exception {
    // Prepare test data
    FhirPackageVersionInfo singleInfo = createExamplePackageVersionInfo();
    // set the package to be unlisted
    singleInfo.setUnlisted("some deprecation message");

    // Generate feed for PUBLICATION
    String xmlContent =
        dynamicFeedGenerator.createFeed(
            List.of(singleInfo),
            "MyPublisher",
            "my-publication-package",
            "keyword",
            "http://test-request-url",
            FeedType.PUBLICATION,
            false);

    DynamicFeedGenerator.Rfc822DateFormat rfc822DateFormat =
        new DynamicFeedGenerator.Rfc822DateFormat();

    // Expected dates for publication date and deprecation date
    String expectedPublicationDate = rfc822DateFormat.format(Date.from(singleInfo.getCreatedAt()));
    String expectedDeprecationDate = rfc822DateFormat.format(Date.from(singleInfo.getUpdatedAt()));

    // Parse the string as XML
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document document =
        builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

    // Create an XPath to evaluate expressions
    XPath xPath = XPathFactory.newInstance().newXPath();

    // check channel-level dates (they should be the same as the latest item date, which is the
    // deprecation date)
    // channel/pubDate
    String channelPubDate = xPath.evaluate("/rss/channel/pubDate", document);
    assertThat(channelPubDate).isNotEmpty().isEqualTo(expectedDeprecationDate);

    // channel/lastBuildDate
    String channelLastBuildDate = xPath.evaluate("/rss/channel/lastBuildDate", document);
    assertThat(channelLastBuildDate).isNotEmpty().isEqualTo(expectedDeprecationDate);

    // ======================
    // Item-level checks
    // ======================

    // Get the number of items
    String itemCountStr = xPath.evaluate("count(/rss/channel/item)", document);
    int itemCount = Integer.parseInt(itemCountStr);
    // there should be exactly two items in the feed, one for the publication and one for the
    // deprecation
    assertThat(itemCount).isEqualTo(2);

    // Access items individually
    NodeList items =
        (NodeList) xPath.evaluate("/rss/channel/item", document, XPathConstants.NODESET);

    // the first item should be the deprecation item
    Node deprecationItem = items.item(0);
    // item/title
    String title = xPath.evaluate("title", deprecationItem);
    assertThat(title)
        .isNotEmpty()
        .contains(PREFIX_TITLE_DEPRECATED)
        .contains("my-publication-package (2.1.0)");
    // item/pubDate
    String pubDate = xPath.evaluate("pubDate", deprecationItem);
    assertThat(pubDate).isNotEmpty().isEqualTo(expectedDeprecationDate);

    // the second item should be the publication item
    Node publicationItem = items.item(1);
    // item/title
    title = xPath.evaluate("title", publicationItem);
    assertThat(title)
        .isNotEmpty()
        .contains(PREFIX_TITLE_RELEASE)
        .contains("my-publication-package (2.1.0)");
    // item/pubDate
    pubDate = xPath.evaluate("pubDate", publicationItem);
    assertThat(pubDate).isNotEmpty().isEqualTo(expectedPublicationDate);
  }

  private FhirPackageVersionInfo createExamplePackageVersionInfo() {
    FhirPackageVersionInfo fhirPackageVersionInfo = new FhirPackageVersionInfo();

    var distInfo = new FhirPackageVersionDistInfo();
    distInfo.setTarball("http://example.com/tarball.tgz");
    var author = new FhirPackageAuthor();
    author.setName("MyPublisher");

    fhirPackageVersionInfo.setName("my-publication-package");
    fhirPackageVersionInfo.setVersion("2.1.0");
    fhirPackageVersionInfo.setDescription("Sample publication description");
    fhirPackageVersionInfo.setDist(distInfo);
    fhirPackageVersionInfo.setFhirVersion("R4");
    fhirPackageVersionInfo.setUrl("http://example.com/tarball.tgz");

    fhirPackageVersionInfo.setProtectedPackage(true);
    fhirPackageVersionInfo.setDownloadConditions("http://example.com/download-conditions");

    fhirPackageVersionInfo.setKeywords(List.of("keyword1", "keyword2"));
    fhirPackageVersionInfo.setLinkToZts("http://example.com/package-info");
    fhirPackageVersionInfo.setAuthor(author);
    fhirPackageVersionInfo.setTitle("My Publication Package");

    fhirPackageVersionInfo.setCreatedAt(Instant.now());
    fhirPackageVersionInfo.setUpdatedAt(Instant.now().plusSeconds(1000));
    return fhirPackageVersionInfo;
  }
}
