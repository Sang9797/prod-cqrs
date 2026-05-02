package com.company.orders.presentation.auth;

import com.company.orders.infrastructure.persistence.UserJpaRepository;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

  private final UserJpaRepository userRepository;

  public AppUserDetailsService(UserJpaRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    var entity =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

    // Authorities = roles + permissions (Spring Security sees both)
    var authorities =
        entity.getRoles().stream()
            .flatMap(
                role ->
                    Stream.concat(
                        Stream.of(new SimpleGrantedAuthority(role.getName())),
                        role.getPermissions().stream()
                            .map(p -> new SimpleGrantedAuthority(p.getName()))))
            .collect(Collectors.toSet());

    return User.builder()
        .username(entity.getUsername())
        .password(entity.getPasswordHash())
        .authorities(authorities)
        .disabled(!entity.isEnabled())
        .build();
  }
}
