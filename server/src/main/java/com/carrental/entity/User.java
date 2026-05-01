package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Application user — implements Spring Security's {@link UserDetails} so it
 * can be used directly in the authentication pipeline.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(columnNames = {"email", "tenant_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** E-mail is used as the login identifier */
    @Column(nullable = false)
    private String email;

    /** BCrypt-hashed password */
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // ── Multi-tenancy link ──────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // ── UserDetails contract ────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** Spring Security uses this as the "username" */
    @Override
    public String getUsername() {
        return email;
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
