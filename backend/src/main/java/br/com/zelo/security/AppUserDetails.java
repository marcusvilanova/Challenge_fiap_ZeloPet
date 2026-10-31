package br.com.zelo.security;

import br.com.zelo.domain.AppUser;
import br.com.zelo.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Usuário autenticado; guarda só o necessário para autorizar (nunca a entidade inteira). */
public record AppUserDetails(Long id, String email, String displayName, Role role, String passwordHash)
        implements UserDetails {

    public static AppUserDetails from(AppUser user) {
        return new AppUserDetails(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.getPasswordHash());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /** Nunca inclui o hash da senha (evita vazar em logs). */
    @Override
    public String toString() {
        return "AppUserDetails[id=" + id + ", role=" + role + "]";
    }
}
