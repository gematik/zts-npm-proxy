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

import de.gematik.zts.npmproxy.NpmProxyConfiguration;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JwtService implements TokenProvider {

  NpmProxyConfiguration configuration;

  @Autowired
  public JwtService(NpmProxyConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public String generateToken(UserDetails userDetails, String note) {
    return generateToken(Map.of("note", note), userDetails);
  }

  private String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
    long currentTimeMillis = System.currentTimeMillis();
    return Jwts.builder()
        .claims(extraClaims)
        .subject(userDetails.getUsername())
        .claim(
            "packages",
            userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toArray())
        .issuedAt(new Date(currentTimeMillis))
        .expiration(
            new Date(
                currentTimeMillis
                    + configuration.getValidityDurationInDays() * 24 * 60 * 60 * 1000))
        .signWith(getSigningKey(), Jwts.SIG.HS256)
        .compact();
  }

  public String extractUsername(String jwt) {
    return extractClaim(jwt, Claims::getSubject);
  }

  public List<String> extractPackages(String jwt) {
    return extractClaim(jwt, claims -> (List<String>) claims.get("packages"));
  }

  public boolean isTokenValid(String jwt) {
    return !isTokenExpired(jwt);
  }

  public boolean isTokenExpired(String jwt) {
    return extractClaim(jwt, Claims::getExpiration).before(new Date());
  }

  private <T> T extractClaim(String jwt, Function<Claims, T> claimResolver) {
    Claims claims = extractAllClaims(jwt);
    return claimResolver.apply(claims);
  }

  private Claims extractAllClaims(String jwt) {
    try {
      return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(jwt).getPayload();
    } catch (JwtException e) {
      throw new JwtAuthenticationException(e.getMessage(), e);
    }
  }

  private SecretKey getSigningKey() {
    byte[] bytes = Decoders.BASE64.decode(configuration.getKey());
    return Keys.hmacShaKeyFor(bytes);
  }
}
