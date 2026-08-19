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

package de.gematik.zts.npmproxy.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.FieldNamesAware;
import net.logstash.logback.composite.JsonWritingUtils;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import tools.jackson.core.JsonGenerator;

/**
 * Spezieller JSON Provider, um in die Logdatei das Feld "severity" zu schreiben. Dabei werden die
 * Standrad Logback Level auf RFC5424/GCP-Logging kompatible Level gemappt.
 */
public class CustomLogLevelJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent>
    implements FieldNamesAware<LogstashFieldNames> {

  // Feldname
  public static final String SEVERITY_LEVEL = "severity";

  public CustomLogLevelJsonProvider() {
    setFieldName(SEVERITY_LEVEL);
  }

  @Override
  public void writeTo(JsonGenerator generator, ILoggingEvent event) {

    String mappedLogLevel = "DEFAULT";

    // Mapping der Logback Level auf RFC5424/GCP-Logging kompatible Level
    switch (event.getLevel().toInt()) {
      case Level.TRACE_INT, Level.DEBUG_INT:
        mappedLogLevel = "DEBUG";
        break;
      case Level.INFO_INT:
        mappedLogLevel = "INFO";
        break;
      case Level.WARN_INT:
        mappedLogLevel = "WARNING";
        break;
      case Level.ERROR_INT:
        mappedLogLevel = "ERROR";
        break;
      default:
        break;
    }

    JsonWritingUtils.writeStringField(generator, getFieldName(), mappedLogLevel);
  }

  @Override
  public void setFieldNames(LogstashFieldNames fieldNames) {
    setFieldName(SEVERITY_LEVEL);
  }
}
