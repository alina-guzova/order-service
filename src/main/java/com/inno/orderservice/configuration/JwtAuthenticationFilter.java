package com.inno.orderservice.configuration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  @Value("${application.security.jwt.secret-key}")
  private String secretKey;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    String jwt = authHeader.substring(7);

    try {
      SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));

      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();

      Long userId = Long.valueOf(claims.getSubject());
      String role = claims.get("role", String.class);

      if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        var authority = new SimpleGrantedAuthority("ROLE_" + role);

        var authToken =
            new UsernamePasswordAuthenticationToken(
                userId, null, Collections.singletonList(authority));
        SecurityContextHolder.getContext().setAuthentication(authToken);
      }
    } catch (Exception ex) {
      SecurityContextHolder.clearContext();
    }

    filterChain.doFilter(request, response);
  }
}
