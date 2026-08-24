package com.inno.orderservice.security;

import static com.inno.orderservice.util.ConfigConstants.USER_ID_HEADER;
import static com.inno.orderservice.util.ConfigConstants.USER_ROLE_HEADER;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String userIdHeader = request.getHeader(USER_ID_HEADER);
    String roleHeader = request.getHeader(USER_ROLE_HEADER);

    if (userIdHeader != null
        && !userIdHeader.isBlank()
        && roleHeader != null
        && !roleHeader.isBlank()
        && SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        Long userId = Long.valueOf(userIdHeader);

        String fullRole = roleHeader.startsWith("ROLE_") ? roleHeader : "ROLE_" + roleHeader;
        var authority = new SimpleGrantedAuthority(fullRole);

        var authToken =
            new UsernamePasswordAuthenticationToken(
                userId, null, Collections.singletonList(authority));

        SecurityContextHolder.getContext().setAuthentication(authToken);
      } catch (Exception e) {
        SecurityContextHolder.clearContext();
      }
    }

    filterChain.doFilter(request, response);
  }
}
