package com.lms.auth.security;

import com.lms.auth.entity.RoleName;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class JwtUserPrincipal implements UserDetails {

    private final Long userId;
    private final String email;
    private final RoleName role;
    private final Long impersonatorId;

    public JwtUserPrincipal(Long userId, String email, RoleName role, Long impersonatorId) {
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.impersonatorId = impersonatorId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public RoleName getRole() {
        return role;
    }

    public Long getImpersonatorId() {
        return impersonatorId;
    }

    public boolean isImpersonating() {
        return impersonatorId != null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return String.valueOf(userId);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
