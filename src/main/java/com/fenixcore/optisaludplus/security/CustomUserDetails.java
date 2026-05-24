package com.fenixcore.optisaludplus.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

public class CustomUserDetails implements UserDetails {

    @Getter
    private final Long id;

    @Getter
    private final UUID uuid;

    private final String email;
    private final String passwordHash;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean active;

    public CustomUserDetails(Long id, UUID uuid, String email, String passwordHash,
                             Collection<? extends GrantedAuthority> authorities, boolean active) {
        this.id = id;
        this.uuid = uuid;
        this.email = email;
        this.passwordHash = passwordHash;
        this.authorities = authorities;
        this.active = active;
    }

    /** Lightweight instance built from JWT claims — no DB lookup required. */
    public static CustomUserDetails fromJwt(UUID uuid, Collection<? extends GrantedAuthority> authorities) {
        return new CustomUserDetails(null, uuid, uuid.toString(), null, authorities, true);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
