package com.fenixcore.optisaludplus.security;

import com.fenixcore.optisaludplus.modules.auth.entity.User;
import com.fenixcore.optisaludplus.modules.auth.entity.UserRole;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        List<SimpleGrantedAuthority> authorities = user.getUserRoles().stream()
                .filter(ur -> ur.isActive() && (ur.getExpiresAt() == null || ur.getExpiresAt().isAfter(Instant.now())))
                .flatMap(ur -> ur.getRole().getPermissions().stream())
                .map(permission -> new SimpleGrantedAuthority(permission.getName()))
                .toList();

        return new CustomUserDetails(
                user.getId(),
                user.getUuid(),
                user.getEmail(),
                user.getPasswordHash(),
                authorities,
                user.isActive()
        );
    }
}
