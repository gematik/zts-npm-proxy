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
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;

public class TestAppender extends AppenderBase<ILoggingEvent> {

  private final List<ILoggingEvent> events = new ArrayList<>();

  @Override
  protected void append(ILoggingEvent eventObject) {
    events.add(eventObject);
  }

  public boolean contains(String message, Level level) {
    return events.stream()
        .anyMatch(
            event ->
                event.getLevel().equals(level) && event.getFormattedMessage().contains(message));
  }

  public void clear() {
    events.clear();
  }

  public List<ILoggingEvent> getEvents() {
    return events;
  }

  public ILoggingEvent getLastEvent() {
    if (events.isEmpty()) {
      return null;
    } else {
      return events.get(events.size() - 1);
    }
  }
}
