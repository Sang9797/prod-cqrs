package com.company.orders.presentation.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthenticationManager authManager;
  private final JwtService jwtService;

  public AuthController(AuthenticationManager authManager, JwtService jwtService) {
    this.authManager = authManager;
    this.jwtService = jwtService;
  }

  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    try {
      Authentication auth =
          authManager.authenticate(
              new UsernamePasswordAuthenticationToken(request.username(), request.password()));
      return ResponseEntity.ok(new TokenResponse(jwtService.generateToken(auth.getName())));
    } catch (AuthenticationException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

  public record TokenResponse(String token) {}
}
