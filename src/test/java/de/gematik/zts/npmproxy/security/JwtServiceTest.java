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
import static org.mockito.Mockito.*;

import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import io.jsonwebtoken.Claims;
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

  private static final long VALIDITY_DURATION_IN_DAYS = 1L;
  private static final String USERNAME = "username";
  private static final String PACKAGENAME = "bfarm.terminologien.test";
  private static final String NOTE = "Ich akzeptiere die Downloadbedingungen";

  @InjectMocks private JwtService jwtService;
  @Mock private NpmProxyConfiguration configuration;

  UserDetails userDetails;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(configuration.getKey()).thenReturn("N4pKMNt+7+5FF7Vyzm3/PVDL689ATD2Ye7j6vTtMetw=");
    when(configuration.getValidityDurationInDays()).thenReturn(VALIDITY_DURATION_IN_DAYS);

    userDetails = mock(UserDetails.class);
    when(userDetails.getUsername()).thenReturn(USERNAME);

    GrantedAuthority authority = mock(GrantedAuthority.class);
    when(authority.getAuthority()).thenReturn(PACKAGENAME);
    List<GrantedAuthority> authList = Collections.singletonList(authority);
    doReturn(authList).when(userDetails).getAuthorities();
  }

  private SecretKey getSigningKey() {
    byte[] bytes = Decoders.BASE64.decode(configuration.getKey());
    return Keys.hmacShaKeyFor(bytes);
  }

  // ==========================================================================================
  // Tests
  // ==========================================================================================

  @Test
  void testGenerateToken() {
    // Methodenaufruf
    String token = jwtService.generateToken(userDetails, NOTE);
    assertNotNull(token);

    Claims claims =
        Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();

    // Überprüfung
    assertEquals(USERNAME, claims.getSubject());
    assertEquals(NOTE, claims.get("note"));
    assertInstanceOf(List.class, claims.get("packages"));
    assertTrue(((List<?>) claims.get("packages")).contains(PACKAGENAME));
    long expiration = claims.getExpiration().getTime();
    long issuedAt = claims.getIssuedAt().getTime();
    assertEquals(VALIDITY_DURATION_IN_DAYS * 24 * 60 * 60 * 1000, expiration - issuedAt);
  }

  @Test
  void testExtractUsername() {
    // Vorbereitung
    String token = jwtService.generateToken(userDetails, NOTE);

    // Methodenaufruf
    String extractedUsername = jwtService.extractUsername(token);

    // Überprüfung
    assertEquals(USERNAME, extractedUsername);
  }

  @Test
  void testExtractPackages() {
    // Vorbereitung
    String token = jwtService.generateToken(userDetails, NOTE);

    // Methodenaufruf
    List<String> packages = jwtService.extractPackages(token);

    // Überprüfung
    assertTrue(packages.contains(PACKAGENAME));
  }

  @Test
  void testIsTokenValid() {
    // Vorbereitung
    String token = jwtService.generateToken(userDetails, NOTE);

    // Methodenaufruf und Überprüfung
    assertTrue(jwtService.isTokenValid(token));
  }

  @Test
  void testIsTokenExpired() {
    // Vorbereitung
    String token = jwtService.generateToken(userDetails, NOTE);

    // Methodenaufruf und Überprüfung
    assertFalse(jwtService.isTokenExpired(token));
  }
}
