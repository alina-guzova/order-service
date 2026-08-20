package com.inno.orderservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  @Value("${application.security.jwt.secret-key}")
  private String secretKey;

  public Claims extractAllClaims(String token) {
    SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }
}
