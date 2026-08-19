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

public class NpmProxyConstants {

  public static final String ATTRIBUTE_USER = "user";
  public static final String ATTRIBUTE_LOG_MESSAGE = "logMessage";
  public static final String ATTRIBUTE_EXCEPTION = "exception";
  public static final String BACKEND_MODE_GITLAB = "gitlab";
  public static final String BACKEND_MODE_BASICAUTH = "basicauth";
  public static final String REGEXP_PACKAGE_NAME = "^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+$";
  public static final String MESSAGE_REGEXP_PACKAGE_NAME =
      "FHIR Spezifikation nicht beachtet: Der Paketname muss in zwei oder mehr durch Punkte getrennte Namensräume unterteilt sein. Jeder Namensraum beginnt mit einem Kleinbuchstaben und kann gefolgt werden von Kleinbuchstaben, Zahlen oder Bindestrichen.";
  public static final String REGEXP_PACKAGE_NAME_CATALOG = "^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*$";
  public static final String MESSAGE_REGEXP_PACKAGE_NAME_CATALOG =
      "Der Paketname enthält ungültige Zeichen";
  public static final String REGEXP_PACKAGE_VERSION =
      "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-(?:0|[1-9A-Za-z-][0-9A-Za-z-]*)(?:\\.(?:0|[1-9A-Za-z-][0-9A-Za-z-]*))*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$";
  public static final String MESSAGE_REGEXP_PACKAGE_VERSION =
      "FHIR Spezifikation nicht beachtet: Ungültige Versionsnummer: Die Versionsnummer muss dem SemVer-Standard entsprechen.";
  public static final String REGEXP_PACKAGE_VERSION_CATALOG =
      "^\\d+(\\.\\d+){0,2}(-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$";
  public static final String MESSAGE_REGEXP_PACKAGE_VERSION_CATALOG =
      "Ungültige Versionsnummer: Die Versionsnummer muss dem SemVer-Standard entsprechen (oder einem Teil davon entsprechen).";
  public static final String MESSAGE_TOKEN_GENERATION_NO_PACKAGE =
      "Fehler bei der Tokengenerierung: Die angegebene Liste der Packages darf nicht leer sein.";
  public static final String MESSAGE_TOKEN_GENERATION_INVALID_PACKAGE =
      "Fehler bei der Tokengenerierung: Die angegebene Liste enthält mindestens ein Paket, das nicht in der Liste der geschützten Packages enthalten ist: ";
  public static final String CLAIM_NOTE_DOWNLOADBEDINGUNGEN =
      "Ich habe die Downloadbedingungen für die aufgeführten Terminologiepakete gelesen und stimme diesen ausdrücklich zu.";
  public static final String PROBLEMDETAILS_TITLE_BAD_REQUEST =
      "Fehler bei der Verarbeitung der Anfrage (BAD_REQUEST)";
  public static final String PROBLEMDETAILS_TITLE_UNSUPPORTED_MEDIA_TYPE =
      "Fehler bei der Verarbeitung der Anfrage (UNSUPPORTED_MEDIA_TYPE)";
  public static final String PROBLEMDETAILS_TITLE_NOT_ACCEPTABLE =
      "Fehler bei der Verarbeitung der Anfrage (NOT_ACCEPTABLE)";
  public static final String PROBLEMDETAILS_TITLE_NOT_FOUND =
      "Ressource nicht gefunden (NOT_FOUND)";
  public static final String PROBLEMDETAILS_TITLE_METHOD_NOT_ALLOWED =
      "Methodenaufruf unzulässig (METHOD_NOT_ALLOWED)";
  public static final String PROBLEMDETAILS_TITLE_UNAUTHORIZED =
      "Authentifizierung erforderlich (UNAUTHORIZED)";
  public static final String PROBLEMDETAILS_TITLE_FORBIDDEN = "Zugriff verweigert (FORBIDDEN)";
  public static final String PROBLEMDETAILS_TITLE_TOO_MANY_REQUESTS =
      "Zu viele Anfragen (TOO_MANY_REQUESTS)";
  public static final String PROBLEMDETAILS_TITLE_INTERNAL_SERVER_ERROR =
      "Interner Server Fehler (INTERNAL_SERVER_ERROR)";
  public static final String MESSAGE_GENERIC_INTERNAL_SERVER_ERROR =
      "Ein interner Serverfehler ist aufgetreten. Bitte versuchen Sie es später erneut.";
  public static final String PROBLEMDETAILS_TITLE_SERVICE_UNAVAILABLE =
      "Der Service ist derzeit nicht verfügbar (SERVICE_UNAVAILABLE)";
  public static final String PROBLEMDETAILS_PROPERTY_TIMESTAMP = "timestamp";
  public static final String CATALOG_KEYWORD_OR_SEPARATOR_CHAR = ",";
  // allow Unicode letters, digits, spaces, underscores, periods, commas, and hyphens in keywords,
  // also limit a keyword to 30 characters
  public static final String REGEXP_KEYWORD = "^[\\p{L}0-9 _.,-]{1,30}$";
  public static final String MESSAGE_REGEXP_KEYWORD =
      "keyword beinhaltet unzulässige Zeichen oder ist zu lang (max. 30 Zeichen).";
  public static final int MAX_KEYWORDS = 10;
  public static final String MESSAGE_TOO_MANY_KEYWORDS = "Sie können maximal 10 keywords angeben.";
  // allow Unicode for Internationalized Domain Names (IDNs)
  public static final String REGEXP_URI_PART = "^[\\p{L}0-9\\-._:/]*$";

  public static final String REGEXP_URI_PART_WITH_VERSION =
      "^[\\p{L}0-9\\-._:/]{0,255}(?:\\|[\\p{L}0-9 :/.,\\-]{0,255})?$";
  public static final String MESSAGE_REGEXP_URI_PART = "canonical enthält unzulässige Zeichen.";
  public static final String REGEXP_FHIR_VERSION = "R2|R3|R4|R4B|R5";
  public static final String MESSAGE_REGEXP_FHIR_VERSION =
      "Ungültige FHIR-Version. Gültige Werte: R2,R3,R4,R4B,R5";
  public static final String MESSAGE_VALID_CANONICAL = "canonical ist nicht valide.";
  public static final String MESSAGE_VALID_PKG_CANONICAL = "pkgcanonical ist nicht valide.";
  public static final String REGEXP_FEED_TYPE = "^(?i)(package|publication)$";
  public static final String MESSAGE_REGEXP_FEED_TYPE =
      "Ungültiger Feed-Typ. Gültige Werte: package, publication.";

  public static final String REGEXP_FEED_PUBLISHER = "^[\\p{L}0-9 _.,-]{1,255}$";
  public static final String MESSAGE_REGEXP_FEED_PUBLISHER =
      "publisher beinhaltet unzulässige Zeichen oder ist zu lang (max. 255 Zeichen).";

  public static final String REGEXP_CANONICAL_VERSION_SEPARATOR = "\\|";
  public static final String MESSAGE_CANONICAL_VERSION_MISSING =
      "Wenn bei einer canonical ein Separator verwendet wird, muss die Version angegeben werden.";

  private NpmProxyConstants() {
    // Constants class
  }
}
