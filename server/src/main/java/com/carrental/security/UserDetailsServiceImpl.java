package com.carrental.security;

import com.carrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Custom {@link UserDetailsService} used by Spring Security's
 * {@code DaoAuthenticationProvider}.
 *
 * <p>During login, the tenant is not yet known (the user supplies only email +
 * password), so we look up by e-mail alone. The subsequent JWT validates the
 * full tenant binding on every subsequent request.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        return userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(normalizedEmail)
                .orElseThrow(() ->
                    new UsernameNotFoundException("User not found with email: " + normalizedEmail));
    }
}
