package com.company.orders.presentation.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;
  private final UserDetailsService userDetailsService;

  public JwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService) {
    this.jwtService = jwtService;
    this.userDetailsService = userDetailsService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      chain.doFilter(request, response);
      return;
    }

    String token = header.substring(BEARER_PREFIX.length());
    var currentAuth = SecurityContextHolder.getContext().getAuthentication();
    boolean notAuthenticated =
        currentAuth == null || currentAuth instanceof AnonymousAuthenticationToken;

    if (jwtService.isTokenValid(token) && notAuthenticated) {
      String username = jwtService.extractUsername(token);
      var userDetails = userDetailsService.loadUserByUsername(username);
      var auth =
          new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
      auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(auth);
    }

    chain.doFilter(request, response);
  }
}
