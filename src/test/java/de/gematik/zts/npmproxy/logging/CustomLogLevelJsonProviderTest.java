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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tools.jackson.core.JsonGenerator;

class CustomLogLevelJsonProviderTest {

  @Mock private JsonGenerator jsonGenerator;

  @Mock private ILoggingEvent loggingEvent;

  @InjectMocks private CustomLogLevelJsonProvider customLogLevelJsonProvider;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testWriteTo_withTraceLevel() {
    when(loggingEvent.getLevel()).thenReturn(Level.TRACE);
    customLogLevelJsonProvider.writeTo(jsonGenerator, loggingEvent);
    verify(jsonGenerator).writeStringProperty(CustomLogLevelJsonProvider.SEVERITY_LEVEL, "DEBUG");
  }

  @Test
  void testWriteTo_withDebugLevel() {
    when(loggingEvent.getLevel()).thenReturn(Level.DEBUG);
    customLogLevelJsonProvider.writeTo(jsonGenerator, loggingEvent);
    verify(jsonGenerator).writeStringProperty(CustomLogLevelJsonProvider.SEVERITY_LEVEL, "DEBUG");
  }

  @Test
  void testWriteTo_withInfoLevel() {
    when(loggingEvent.getLevel()).thenReturn(Level.INFO);
    customLogLevelJsonProvider.writeTo(jsonGenerator, loggingEvent);
    verify(jsonGenerator).writeStringProperty(CustomLogLevelJsonProvider.SEVERITY_LEVEL, "INFO");
  }

  @Test
  void testWriteTo_withWarnLevel() {
    when(loggingEvent.getLevel()).thenReturn(Level.WARN);
    customLogLevelJsonProvider.writeTo(jsonGenerator, loggingEvent);
    verify(jsonGenerator).writeStringProperty(CustomLogLevelJsonProvider.SEVERITY_LEVEL, "WARNING");
  }

  @Test
  void testWriteTo_withErrorLevel() {
    when(loggingEvent.getLevel()).thenReturn(Level.ERROR);
    customLogLevelJsonProvider.writeTo(jsonGenerator, loggingEvent);
    verify(jsonGenerator).writeStringProperty(CustomLogLevelJsonProvider.SEVERITY_LEVEL, "ERROR");
  }

  @Test
  void testSetFieldNames() {
    LogstashFieldNames fieldNames = new LogstashFieldNames();
    customLogLevelJsonProvider.setFieldNames(fieldNames);
    assertEquals(
        CustomLogLevelJsonProvider.SEVERITY_LEVEL, customLogLevelJsonProvider.getFieldName());
  }
}
