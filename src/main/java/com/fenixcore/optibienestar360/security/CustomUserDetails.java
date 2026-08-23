package com.fenixcore.optibienestar360.security;

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

    @Getter
    private final String jti;

    @Getter
    private final String locale;

    /** The {@code sid} claim (login_audit_log.uuid) — {@code null} for DB-loaded instances or tokens issued with {@code login_audit_enabled=false}. */
    @Getter
    private final UUID sessionId;

    private final String email;
    private final String passwordHash;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean active;

    public CustomUserDetails(Long id, UUID uuid, String jti, String locale, String email, String passwordHash,
                             Collection<? extends GrantedAuthority> authorities, boolean active) {
        this(id, uuid, jti, locale, null, email, passwordHash, authorities, active);
    }

    public CustomUserDetails(Long id, UUID uuid, String jti, String locale, UUID sessionId, String email, String passwordHash,
                             Collection<? extends GrantedAuthority> authorities, boolean active) {
        this.id = id;
        this.uuid = uuid;
        this.jti = jti;
        this.locale = locale;
        this.sessionId = sessionId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.authorities = authorities;
        this.active = active;
    }

    /** Lightweight instance built from JWT claims — no DB lookup required. */
    public static CustomUserDetails fromJwt(UUID uuid, String jti, String locale,
                                            Collection<? extends GrantedAuthority> authorities) {
        return fromJwt(uuid, jti, locale, null, authorities);
    }

    /** Same as {@link #fromJwt(UUID, String, String, Collection)}, carrying the {@code sid} claim too. */
    public static CustomUserDetails fromJwt(UUID uuid, String jti, String locale, UUID sessionId,
                                            Collection<? extends GrantedAuthority> authorities) {
        return new CustomUserDetails(null, uuid, jti, locale, sessionId, uuid.toString(), null, authorities, true);
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
