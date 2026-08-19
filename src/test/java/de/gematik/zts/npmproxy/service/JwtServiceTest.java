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

package de.gematik.zts.npmproxy.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import de.gematik.zts.npmproxy.security.JwtAuthenticationException;
import de.gematik.zts.npmproxy.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Collections;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

class JwtServiceTest {

  @InjectMocks private JwtService jwtService;

  @Mock private NpmProxyConfiguration configuration;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(configuration.getKey()).thenReturn("N4pKMNt+7+5FF7Vyzm3/PVDL689ATD2Ye7j6vTtMetw=");
    when(configuration.getValidityDurationInDays()).thenReturn(1L);
  }

  private SecretKey getSigningKey() {
    byte[] bytes = Decoders.BASE64.decode(configuration.getKey());
    return Keys.hmacShaKeyFor(bytes);
  }

  @Test
  void testGenerateToken() {

    UserDetails userDetails = mock(UserDetails.class);
    when(userDetails.getUsername()).thenReturn("username");

    // Mocking GrantedAuthority
    GrantedAuthority authority = mock(GrantedAuthority.class);
    List<GrantedAuthority> authList = Collections.singletonList(authority);
    when(authority.getAuthority()).thenReturn("bfarm.terminologien.test");
    doReturn(authList).when(userDetails).getAuthorities();

    String token = jwtService.generateToken(userDetails, "note");
    assertNotNull(token);

    // Decode the token to verify claims
    var claims =
        Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();

    assertEquals("username", claims.getSubject());
    assertEquals("note", claims.get("note"));
    assertInstanceOf(List.class, claims.get("packages"));
    assertTrue(((List<?>) claims.get("packages")).contains("bfarm.terminologien.test"));
  }

  @Test
  void testTokenValidity() {
    // Arrange
    UserDetails userDetails = mock(UserDetails.class);
    when(userDetails.getUsername()).thenReturn("username");

    GrantedAuthority authority = mock(GrantedAuthority.class);
    List<GrantedAuthority> authList = Collections.singletonList(authority);
    when(authority.getAuthority()).thenReturn("bfarm.terminologien.test");
    doReturn(authList).when(userDetails).getAuthorities();

    // Generate a valid token
    String validToken = jwtService.generateToken(userDetails, "note");

    // Assert that the token is valid
    assertThat(jwtService.isTokenValid(validToken)).isTrue();

    // Manipulate validity duration to create an expired token
    when(configuration.getValidityDurationInDays()).thenReturn(-1L);
    String expiredToken = jwtService.generateToken(userDetails, "note");

    // Act & Assert: Verify that a JwtAuthenticationException is thrown
    assertThatThrownBy(() -> jwtService.isTokenValid(expiredToken))
        .isInstanceOf(JwtAuthenticationException.class)
        .hasMessageContaining("JWT expired");
  }
}
