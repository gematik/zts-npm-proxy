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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.boolex.EvaluationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class LogLevelThresholdEvaluatorTest {

  private LogLevelThresholdEvaluator evaluator;
  private ILoggingEvent loggingEvent;

  @BeforeEach
  void setUp() {
    evaluator = new LogLevelThresholdEvaluator();
    // By default, thresholdLevel = ERROR; we set it explicitly for clarity
    evaluator.setThreshold("ERROR");

    // Create a mock for ILoggingEvent
    loggingEvent = Mockito.mock(ILoggingEvent.class);
  }

  @Test
  void testEvaluateBelowError() throws EvaluationException {
    // WARN is below ERROR
    when(loggingEvent.getLevel()).thenReturn(Level.WARN);
    assertThat(evaluator.evaluate(loggingEvent)).as("WARN < ERROR should return true").isTrue();
  }

  @Test
  void testEvaluateEqualError() throws EvaluationException {
    // ERROR is equal to ERROR
    when(loggingEvent.getLevel()).thenReturn(Level.ERROR);
    assertThat(evaluator.evaluate(loggingEvent))
        .as("ERROR == ERROR, so should return false (not < ERROR)")
        .isFalse();
  }

  @Test
  void testEvaluateAboveWarning() throws EvaluationException {
    // Set threshold to WARN
    evaluator.setThreshold("WARN");
    // Set the event level to ERROR
    when(loggingEvent.getLevel()).thenReturn(Level.ERROR);

    // Evaluate: ERROR < WARN ? -> false (ERROR = 40000, WARN = 30000)
    assertThat(evaluator.evaluate(loggingEvent))
        .as("ERROR is above WARN => should return false")
        .isFalse();
  }

  @Test
  void testChangeThreshold() throws EvaluationException {
    // Change threshold from ERROR to WARN
    evaluator.setThreshold("WARN");
    // INFO is below WARN
    when(loggingEvent.getLevel()).thenReturn(Level.INFO);
    assertThat(evaluator.evaluate(loggingEvent)).as("INFO < WARN should return true").isTrue();
  }
}
