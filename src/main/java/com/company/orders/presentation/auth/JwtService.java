package com.company.orders.presentation.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final JwtProperties properties;

  public JwtService(JwtProperties properties) {
    this.properties = properties;
  }

  public String generateToken(String username) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .subject(username)
        .issuedAt(new Date(now))
        .expiration(new Date(now + properties.expirationMs()))
        .signWith(signingKey())
        .compact();
  }

  public String extractUsername(String token) {
    return claims(token).getSubject();
  }

  public boolean isTokenValid(String token) {
    try {
      claims(token);
      return true;
    } catch (JwtException e) {
      return false;
    }
  }

  private Claims claims(String token) {
    return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
  }

  private SecretKey signingKey() {
    return Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
  }
}
