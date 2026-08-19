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
import ch.qos.logback.core.boolex.EvaluationException;
import ch.qos.logback.core.boolex.EventEvaluatorBase;

/**
 * LogLevelThresholdEvaluator is a custom evaluator for Logback that checks if the log level of an
 * event is below a specified threshold. This can be used to filter out logs based on their
 * severity. It is because JaninoEventEvaluator has been removed due to identified vulnerabilities.
 */
public class LogLevelThresholdEvaluator extends EventEvaluatorBase<ILoggingEvent> {
  // Default threshold is ERROR
  private Level thresholdLevel = Level.ERROR;

  // Setter to pass the desired log level dynamically
  public void setThreshold(String levelStr) {
    this.thresholdLevel = Level.valueOf(levelStr);
  }

  @Override
  public boolean evaluate(ILoggingEvent event) throws NullPointerException, EvaluationException {
    return event.getLevel().toInt() < thresholdLevel.toInt();
  }
}
