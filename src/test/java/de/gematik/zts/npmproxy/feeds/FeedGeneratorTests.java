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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.exceptions.FeedGenerationException;
import de.gematik.zts.npmproxy.exceptions.NpmProxyException;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

class FeedGeneratorTest {

  private static final String HOST_NAME = "https://terminologien.bfarm.de";
  private static final String FEED_PATH_HL7 = "/feeds/package-feed.xml";

  private FeedGenerator feedGenerator;

  @BeforeEach
  void setUp() throws NpmProxyException {

    NpmProxyConfiguration properties = Mockito.mock(NpmProxyConfiguration.class);
    when(properties.getHostName()).thenReturn(HOST_NAME);
    when(properties.getFeedPath()).thenReturn(FEED_PATH_HL7);

    feedGenerator = new FeedGenerator(properties);
  }

  @Test
  void testGetFeedContentXml()
      throws NpmProxyException,
          ParserConfigurationException,
          IOException,
          SAXException,
          XPathExpressionException {

    String feedContent = feedGenerator.getFeedContent();

    assertNotNull(feedContent);

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(new InputSource(new StringReader(feedContent)));

    XPathFactory xPathfactory = XPathFactory.newInstance();
    XPath xpath = xPathfactory.newXPath();

    assertEquals(FeedGenerator.FEED_TITLE, xpath.evaluate("/rss/channel/title", doc));
    assertEquals(HOST_NAME + FEED_PATH_HL7, xpath.evaluate("/rss/channel/link", doc));
    assertEquals(FeedGenerator.FEED_DESCRIPTION, xpath.evaluate("/rss/channel/description", doc));
    assertEquals(FeedGenerator.FEED_GENERATOR, xpath.evaluate("/rss/channel/generator", doc));
    assertEquals(FeedGenerator.FEED_LANGUAGE, xpath.evaluate("/rss/channel/language", doc));
    assertEquals(
        FeedGenerator.FEED_BUILD_DATETIME, xpath.evaluate("/rss/channel/lastBuildDate", doc));
    assertEquals(FeedGenerator.FEED_BUILD_DATETIME, xpath.evaluate("/rss/channel/pubDate", doc));
    assertEquals(FeedGenerator.FEED_TTL, xpath.evaluate("/rss/channel/ttl", doc));

    assertEquals(
        HOST_NAME + FEED_PATH_HL7,
        xpath.evaluate(
            "/rss/channel/*[namespace-uri()='http://www.w3.org/2005/Atom' and local-name()='link']/@href",
            doc));

    assertEquals(
        "self",
        xpath.evaluate(
            "/rss/channel/*[namespace-uri()='http://www.w3.org/2005/Atom' and local-name()='link']/@rel",
            doc));
    assertEquals(
        "application/rss+xml",
        xpath.evaluate(
            "/rss/channel/*[namespace-uri()='http://www.w3.org/2005/Atom' and local-name()='link']/@type",
            doc));
  }

  @Test
  void testGetFeedContentXmlInvalidOutput() throws NoSuchFieldException, IllegalAccessException {

    SyndFeedOutput syndFeedOutput = Mockito.mock(SyndFeedOutput.class);
    try {
      when(syndFeedOutput.outputString(any())).thenThrow(FeedException.class);
    } catch (FeedException e) {
      throw new RuntimeException(e);
    }

    // Reflection
    Field field = FeedGenerator.class.getDeclaredField("syndFeedOutput");
    field.setAccessible(true);
    field.set(feedGenerator, syndFeedOutput);

    assertThrows(FeedGenerationException.class, () -> feedGenerator.refreshFeeds());
  }

  @Test
  void testRefreshFeeds()
      throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
    feedGenerator.refreshFeeds();
    testGetFeedContentXml();
  }

  @Test
  void testGetCurrentDateTime() {
    String currentDateTime = FeedGenerator.getCurrentDateTime();
    assertNotNull(currentDateTime);
    assertTrue(
        currentDateTime.matches("\\w{3}, \\d{2} \\w{3} \\d{4} \\d{2}:\\d{2}:\\d{2} \\+\\d{4}"));
  }
}
