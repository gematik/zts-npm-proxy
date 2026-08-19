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

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.exceptions.FeedGenerationException;
import de.gematik.zts.npmproxy.exceptions.NpmProxyException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Attribute;
import org.jdom2.Element;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Helferklasse zur Generierung des RSS Feeds. */
@Slf4j
@Component
public class FeedGenerator {

  public static final String FEED_TITLE = "BfArM FHIR Packages (Terminologies)";
  public static final String FEED_DESCRIPTION =
      "References to publicly available FHIR Terminology Packages (published by BfArM) will be available in this channel";
  public static final String FEED_GENERATOR = "ZTS Publication Tooling";
  public static final String FEED_LANGUAGE = "en";
  public static final String FEED_TTL = "60";
  public static final String FEED_BUILD_DATETIME = "Fri, 20 Dec 2024 17:00:00 +0100";

  @Getter private String feedContent;
  private final NpmProxyConfiguration properties;
  private final SyndFeedOutput syndFeedOutput = new SyndFeedOutput();

  public FeedGenerator(NpmProxyConfiguration properties) throws NpmProxyException {
    this.properties = properties;
    this.feedContent = createFeed();
  }

  /**
   * Aktualisiert den Feed alle 60 Sekunden. Das ist aktuell ziemlich überflüssig, da sich der
   * Inhalt nicht ändert, kann aber als Implementierungsansatz gewählt werden, wenn wir die Daten
   * zukünftig dynamisch generieren wollen.
   */
  @Scheduled(fixedRateString = "${proxy.feed-update-interval-in-ms:60000}")
  public void refreshFeeds() {
    feedContent = createFeed();
  }

  /**
   * Erzeugt den RSS Feed.
   *
   * @return RSS Feed als String
   * @throws NpmProxyException wenn ein Fehler beim Erzeugen des Feeds auftritt
   */
  private String createFeed() throws NpmProxyException {

    // Spezifikation unter: https://www.rssboard.org/rss-specification
    // W3C Validator: https://validator.w3.org/feed/#validate_by_input
    SyndFeed feed = new SyndFeedImpl();
    feed.setFeedType("rss_2.0");

    // "The name of the channel. It's how people refer to your service."
    feed.setTitle(FEED_TITLE);

    // "Phrase or sentence describing the channel."
    feed.setDescription(FEED_DESCRIPTION);

    // "The URL to the HTML website corresponding to the channel." -> Hier halten wir uns aber mal
    // an das Beispiel von HL7 und geben den Link zum Feed selbst an.
    // PROD: "https://terminologien.bfarm.de/feeds/package-feed.xml"
    feed.setLink(properties.getHostName() + properties.getFeedPath());

    // A string indicating the program used to generate the channel.
    feed.setGenerator(FEED_GENERATOR);

    // "The language the channel is written in."
    Element language = new Element("language", null, null).addContent(FEED_LANGUAGE);

    // "The publication date for the content in the channel. For example, the New York Times
    // publishes on a daily basis, the publication date flips once every 24 hours. That's when the
    // pubDate of the channel changes."
    Element pubDate = new Element("pubDate", null, null).addContent(FEED_BUILD_DATETIME);

    // "The last time the content of the channel changed"
    Element lastBuildDateElement =
        new Element("lastBuildDate", null, null).addContent(FEED_BUILD_DATETIME);

    // atom Link
    Element atomLinkElement = new Element("link", "atom", "http://www.w3.org/2005/Atom");
    Attribute href = new Attribute("href", properties.getHostName() + properties.getFeedPath());
    Attribute rel = new Attribute("rel", "self");
    Attribute type = new Attribute("type", "application/rss+xml");
    atomLinkElement.setAttribute(href);
    atomLinkElement.setAttribute(rel);
    atomLinkElement.setAttribute(type);

    // "ttl stands for time to live. It's a number of minutes that indicates how long a channel can
    // be cached before refreshing from the source."
    Element ttl = new Element("ttl", null, null).addContent(FEED_TTL);

    // Elemente zur Liste hinzufügen
    feed.setForeignMarkup(List.of(language, lastBuildDateElement, atomLinkElement, pubDate, ttl));

    try {
      return syndFeedOutput.outputString(feed);
    } catch (FeedException e) {
      throw new FeedGenerationException("Error creating feed");
    }
  }

  /**
   * Gibt das aktuelle Datum und die aktuelle Uhrzeit im RFC 2822 Format zurück.
   *
   * @return Datum und Uhrzeit im RFC 2822 Format
   */
  public static String getCurrentDateTime() {
    SimpleDateFormat formatter =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
    formatter.setTimeZone(TimeZone.getTimeZone("GMT+1"));
    return formatter.format(new Date());
  }
}
