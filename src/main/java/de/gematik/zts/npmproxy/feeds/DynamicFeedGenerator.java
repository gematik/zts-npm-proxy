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

import com.rometools.rome.feed.module.DCModule;
import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import de.gematik.zts.npmproxy.exceptions.FeedGenerationException;
import de.gematik.zts.npmproxy.model.FeedType;
import de.gematik.zts.npmproxy.model.FhirPackageVersionInfo;
import de.gematik.zts.npmproxy.model.SemverFhirVersionConverter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Element;
import org.springframework.stereotype.Component;

/** Helper class for generating an RSS feed. */
@Slf4j
@Component
public class DynamicFeedGenerator {

  public static final String FEED_GENERATOR = "ZTS Publication Tooling";
  public static final String FEED_LANGUAGE = "de";
  public static final String FEED_TTL_MIN = "600";
  public static final String PREFIX_TITLE_DEPRECATED = "Veraltet: ";
  public static final String PREFIX_TITLE_RELEASE = "Neuveröffentlichung: ";
  public static final String NAMESPACE_DC_MODULE = "http://purl.org/dc/elements/1.1/";
  public static final String NAMESPACE_FHIR_FEED = "http://hl7.org/fhir/feed";
  public static final String NAMESPACE_ATOM = "http://www.w3.org/2005/Atom";
  public static final String NAMESPACE_HL7_SYNDICATION = "http://hl7.org/fhir/uv/crmi/syndication";
  public static final String NAMESPACE_ATOM_PREFIX = "atom";
  public static final String NAMESPACE_FHIR_FEED_PREFIX = "fhir";
  public static final String NAMESPACE_HL7_SYNDICATION_PREFIX = "hl7";
  public static final String FEED_TITLE_BASE = "ZTS FHIR Pakete";
  public static final String FEED_TITLE_PUBLISHER = " von Herausgeber ";
  public static final String FEED_TITLE_PACKAGE_NAME = " für Paketname ";
  public static final String FEED_TITLE_KEYWORDS = " mit Schlüsselwörtern ";
  public static final String PREFIX_URN_UUID = "urn:uuid:";
  public static final String HL7_PUBLISH_ACTION_PUBLISH = "publish";
  public static final String HL7_PUBLISH_ACTION_UNPUBLISH = "unpublish";
  public static final String HL7_ARTIFACT_TYPE_PACKAGE = "package";
  public static final String HL7_ARTIFACT_TYPE_RESOURCE = "resource";
  public static final String FEED_TITLE_PREFIX_PUBLISH_TO_HL7 = "Nach HL7 veröffentlichte ";
  public static final String FEED_TITLE_PREFIX_PUBLIC = "Öffentliche ";
  public static final String FEED_TITLE_PREFIX_PROTECTED = "Geschützte ";

  private final SyndFeedOutput syndFeedOutput = new SyndFeedOutput();
  @Getter private String feedContent;

  public DynamicFeedGenerator() {
    this.feedContent = "";
  }

  /**
   * Return one or two SyndEntry objects for the given FhirPackageVersionInfo: - If
   * info.getUnlisted() == null, return a single entry using createdAt as pubDate. - Otherwise,
   * return two entries: * one with date = createdAt * one with date = updatedAt
   *
   * @param info The FhirPackageVersionInfo to map
   * @param type The type of entry to create, either "package" or "publication"
   * @return A list of one or two SyndEntries
   */
  public List<SyndEntry> mapToSyndEntries(FhirPackageVersionInfo info, FeedType type) {
    List<SyndEntry> result = new ArrayList<>();

    // If there's no 'unlisted' or a package feed is being created, we only create one entry from
    // createdAt
    // Maybe we should use the 'publicationAction' field in package feeds as well?
    // https://hl7.org/fhir/uv/crmi/distribution.html#distribution-syndication
    if (info.getUnlisted() == null || type == FeedType.PACKAGE) {
      if (info.getCreatedAt() != null) {
        result.add(buildSyndEntry(info, type, info.getCreatedAt(), EntryType.RELEASE));
      }
    } else {
      // We create two entries: one from createdAt (always "Neuveröffentlichung"),
      // one from updatedAt (marked "Veraltet").
      if (info.getCreatedAt() != null) {
        result.add(buildSyndEntry(info, type, info.getCreatedAt(), EntryType.RELEASE));
      }
      if (info.getUpdatedAt() != null) {
        result.add(buildSyndEntry(info, type, info.getUpdatedAt(), EntryType.DEPRECATED));
      }
    }

    return result;
  }

  /**
   * Build a single SyndEntry using a chosen date for pubDate. For the "publication" type, it adds a
   * prefix to the title based on the EntryType (deprecated or release).
   */
  private SyndEntry buildSyndEntry(
      @NonNull FhirPackageVersionInfo info, FeedType type, Instant pubDate, EntryType entryType) {

    SyndEntry entry = new SyndEntryImpl();

    // For "package" we keep the old logic (no "Veraltet"/"Neuveröffentlichung" prefixes).
    if (type == FeedType.PACKAGE) {
      entry.setTitle(info.getName() + "#" + info.getVersion());
      entry.setLink(info.getDist() != null ? info.getDist().getTarball() : null);
    } else {
      // type == PUBLICATION
      String prefix =
          entryType == EntryType.DEPRECATED ? PREFIX_TITLE_DEPRECATED : PREFIX_TITLE_RELEASE;
      String baseTitle = prefix + info.getName() + " (" + info.getVersion() + ")";
      if (info.getTitle() != null && !info.getTitle().isBlank()) {
        baseTitle += " - " + info.getTitle();
      }
      entry.setTitle(baseTitle);

      // linkToZts is a custom field pointing to an internal URL (example)
      entry.setLink(info.getLinkToZts());
    }

    // Description
    SyndContent description = new SyndContentImpl();
    description.setType("text/plain");
    description.setValue(info.getDescription());
    entry.setDescription(description);

    // pubDate is set from the caller
    entry.setPublishedDate(Date.from(pubDate));

    // Unique GUID: name#version + pubDate
    String uuidSource = info.getName() + "#" + info.getVersion() + pubDate;
    String guid =
        PREFIX_URN_UUID + UUID.nameUUIDFromBytes(uuidSource.getBytes(StandardCharsets.UTF_8));
    entry.setUri(guid);

    // Set dc:creator
    DCModule dcModule = (DCModule) entry.getModule(NAMESPACE_DC_MODULE);
    if (dcModule != null) {
      dcModule.setCreator(info.getAuthor() != null ? info.getAuthor().getName() : "");
    }

    var foreignMarkup = new ArrayList<Element>();
    // fhir:version as foreign markup if available
    if (info.getFhirVersion() != null) {
      Element fhirVersion = new Element("version", NAMESPACE_FHIR_FEED_PREFIX, NAMESPACE_FHIR_FEED);
      fhirVersion.addContent(
          SemverFhirVersionConverter.getSemverFromFhirVersion(info.getFhirVersion()));
      foreignMarkup.add(fhirVersion);
    }

    // add  hl7:artifactType and hl7:publishActions as foreign markup to package feed only
    if (type == FeedType.PACKAGE) {
      Element artifactType =
          new Element("artifactType", NAMESPACE_HL7_SYNDICATION_PREFIX, NAMESPACE_HL7_SYNDICATION);
      artifactType.setText(HL7_ARTIFACT_TYPE_PACKAGE);

      Element publishActions =
          new Element("publishAction", NAMESPACE_HL7_SYNDICATION_PREFIX, NAMESPACE_HL7_SYNDICATION);
      publishActions.setText(HL7_PUBLISH_ACTION_PUBLISH);

      foreignMarkup.add(artifactType);
      foreignMarkup.add(publishActions);
    }

    if (!foreignMarkup.isEmpty()) {
      entry.setForeignMarkup(foreignMarkup);
    }

    return entry;
  }

  /**
   * Creates an RSS Feed (RSS 2.0) as a String with the given package info items.
   *
   * @param fhirPackageVersionInfos A list of FhirPackageVersionInfo objects
   * @param publisher Optional author/publisher parameter
   * @param packageName Optional packageName parameter
   * @param keyword Optional keyword parameter
   * @param requestUrl The full request (with parameters) to use as channel link
   * @param type The feed type (PACKAGE or PUBLICATION)
   * @return The RSS feed content as a string
   * @throws FeedGenerationException if feed generation fails
   */
  public String createFeed(
      @NonNull List<FhirPackageVersionInfo> fhirPackageVersionInfos,
      String publisher,
      String packageName,
      String keyword,
      String requestUrl,
      FeedType type,
      Boolean publishToHl7)
      throws FeedGenerationException, FeedException {

    SyndFeed feed = new SyndFeedImpl();
    feed.setFeedType("rss_2.0");

    String feedTitle = buildFeedTitle(publisher, packageName, keyword, publishToHl7);
    String feedDescription = buildFeedDescription(feedTitle);

    feed.setTitle(feedTitle);
    feed.setDescription(feedDescription);
    feed.setLink(requestUrl);

    // Add <atom:link>, <ttl>, <lastBuildDate> as foreign markup
    Element atomLink = new Element("link", NAMESPACE_ATOM_PREFIX, NAMESPACE_ATOM);
    atomLink.setAttribute("href", requestUrl);
    atomLink.setAttribute("rel", "self");
    atomLink.setAttribute("type", "application/rss+xml");

    Element ttlElement = new Element("ttl");
    ttlElement.setText(FEED_TTL_MIN);

    Element lastBuildDateElement = new Element("lastBuildDate");

    List<Element> foreignElements = new ArrayList<>();
    foreignElements.add(atomLink);
    foreignElements.add(ttlElement);
    foreignElements.add(lastBuildDateElement);

    feed.setForeignMarkup(foreignElements);

    feed.setLanguage(FEED_LANGUAGE);
    feed.setGenerator(FEED_GENERATOR);

    // 1. Gather all entries (some info may yield 2 entries)
    List<SyndEntry> allEntries = new ArrayList<>();
    for (FhirPackageVersionInfo info : fhirPackageVersionInfos) {
      List<SyndEntry> entriesForInfo = mapToSyndEntries(info, type);
      allEntries.addAll(entriesForInfo);
    }

    // 2. Sort all entries by publishedDate descending
    allEntries.sort(Comparator.comparing(SyndEntry::getPublishedDate).reversed());
    feed.setEntries(allEntries);

    // 3. If we have entries, set channel pubDate/lastBuildDate to the newest
    if (!allEntries.isEmpty()) {
      Date newestPubDate = allEntries.getFirst().getPublishedDate();
      feed.setPublishedDate(newestPubDate); // <pubDate>
      lastBuildDateElement.setText(new Rfc822DateFormat().format(newestPubDate));
    } else {
      // If empty, set it to "now"
      Date now = new Date();
      feed.setPublishedDate(now);
      lastBuildDateElement.setText(new Rfc822DateFormat().format(now));
    }

    // 4. Generate the RSS XML
    String rss = syndFeedOutput.outputString(feed);

    // If you want top-level namespaces or to remove <dc:date>/<dc:language>, do it here:
    // For example, remove <dc:date> or <dc:language>:
    String updatedRss =
        rss.replaceAll("<dc:date>.*?</dc:date>", "")
            .replaceAll("<dc:language>.*?</dc:language>", "");

    this.feedContent = updatedRss;
    return updatedRss;
  }

  private String buildFeedTitle(
      String publisher, String packageName, String keyword, Boolean publishToHl7) {
    StringBuilder sb = new StringBuilder();

    if (publishToHl7 != null && publishToHl7) {
      sb.append(FEED_TITLE_PREFIX_PUBLISH_TO_HL7);
    }
    sb.append(FEED_TITLE_BASE);

    if (publisher != null && !publisher.isEmpty()) {
      sb.append(FEED_TITLE_PUBLISHER).append(publisher);
    }
    if (packageName != null && !packageName.isEmpty()) {
      sb.append(FEED_TITLE_PACKAGE_NAME).append("[").append(packageName).append("]");
    }
    if (keyword != null && !keyword.isEmpty()) {
      sb.append(FEED_TITLE_KEYWORDS).append("(").append(keyword).append(")");
    }
    return sb.toString();
  }

  private String buildFeedDescription(String title) {
    return "Generierter Feed für: " + title;
  }

  private enum EntryType {
    DEPRECATED,
    RELEASE
  }

  /**
   * RFC822 date format for <pubDate> and <lastBuildDate>. ROME normally handles it, but we use this
   * for custom markup.
   */
  public static class Rfc822DateFormat extends java.text.SimpleDateFormat {
    Rfc822DateFormat() {
      super("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US);
      setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
    }
  }
}
