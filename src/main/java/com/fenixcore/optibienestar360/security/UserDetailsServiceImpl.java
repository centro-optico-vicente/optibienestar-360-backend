package com.fenixcore.optibienestar360.security;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final PermissionResolver permissionResolver;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        // Same resolution as the JWT claim: SYSTEM users get the full catalog.
        List<SimpleGrantedAuthority> authorities = permissionResolver.resolvePermissionNames(user).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        return new CustomUserDetails(
                user.getId(),
                user.getUuid(),
                null,
                user.getPerson().getLocale(),
                user.getEmail(),
                user.getPasswordHash(),
                authorities,
                user.isActive()
        );
    }
}
