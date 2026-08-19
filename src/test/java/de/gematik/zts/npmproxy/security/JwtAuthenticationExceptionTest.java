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

package de.gematik.zts.npmproxy.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.AuthenticationException;

class JwtAuthenticationExceptionTest {

  @Test
  void testExceptionMessage() {
    String errorMessage = "Invalid JWT token";
    JwtAuthenticationException exception = new JwtAuthenticationException(errorMessage);

    assertEquals(errorMessage, exception.getMessage(), "Exception message should be set correctly");
  }

  @Test
  void testExceptionIsAuthenticationException() {
    JwtAuthenticationException exception = new JwtAuthenticationException("Test message");

    assertTrue(
        exception instanceof AuthenticationException,
        "JwtAuthenticationException should be an instance of AuthenticationException");
  }
}
