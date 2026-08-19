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
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

class JwtAuthenticationTokenTest {

  private JwtAuthenticationToken jwtAuthenticationToken;
  private UserDetails userDetails;

  @BeforeEach
  void setUp() {

    String packageName = "bfarm.terminologien.test";
    userDetails = Mockito.mock(UserDetails.class);
    Collection<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority(packageName));
    when(userDetails.getAuthorities())
        .thenAnswer((Answer<Collection<GrantedAuthority>>) invocation -> authorities);
    jwtAuthenticationToken = new JwtAuthenticationToken("testToken", userDetails);
  }

  @Test
  void testConstructor() {
    assertEquals("testToken", jwtAuthenticationToken.getToken());
    assertEquals(userDetails, jwtAuthenticationToken.getPrincipal());
  }

  @Test
  void testWithAuthenticated() {
    JwtAuthenticationToken authenticatedToken =
        (JwtAuthenticationToken) jwtAuthenticationToken.withAuthenticated(true);
    assertTrue(authenticatedToken.isAuthenticated());
    assertEquals(jwtAuthenticationToken.getToken(), authenticatedToken.getToken());
    assertEquals(jwtAuthenticationToken.getPrincipal(), authenticatedToken.getPrincipal());
  }

  @Test
  void testEqualsAndHashCode() {
    JwtAuthenticationToken sameToken = new JwtAuthenticationToken("testToken", userDetails);
    JwtAuthenticationToken differentToken =
        new JwtAuthenticationToken("differentToken", userDetails);

    assertEquals(jwtAuthenticationToken, sameToken);
    assertNotEquals(jwtAuthenticationToken, differentToken);
    assertEquals(jwtAuthenticationToken.hashCode(), sameToken.hashCode());
    assertNotEquals(jwtAuthenticationToken.hashCode(), differentToken.hashCode());
  }

  @Test
  void testEqualsWithDifferentObject() {
    assertNotEquals(jwtAuthenticationToken, new Object());
  }

  @Test
  void testEqualsWithNullToken() {
    JwtAuthenticationToken nullToken = new JwtAuthenticationToken(null, userDetails);
    assertNotEquals(nullToken, jwtAuthenticationToken);
  }

  @Test
  void testHashCodeWithNullToken() {
    JwtAuthenticationToken nullToken = new JwtAuthenticationToken(null, userDetails);
    assertNotEquals(nullToken.hashCode(), jwtAuthenticationToken.hashCode());
  }

  @Test
  void testGetCredentials() {
    assertNull(jwtAuthenticationToken.getCredentials());
  }
}
