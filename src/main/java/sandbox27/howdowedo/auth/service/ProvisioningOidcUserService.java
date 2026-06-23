package sandbox27.howdowedo.auth.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import sandbox27.howdowedo.user.User;
import sandbox27.howdowedo.user.UserService;

import java.util.HashSet;
import java.util.Set;

/**
 * Wraps the standard {@link OidcUserService}: after the provider authenticates the user, we
 * provision a local {@link User} and expose its roles as Spring Security authorities so that
 * {@code hasRole(...)} / {@code @PreAuthorize} reflect our own role model rather than the provider's.
 */
@Service
public class ProvisioningOidcUserService extends OidcUserService {

    private final UserService userService;

    public ProvisioningOidcUserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        String subject = oidcUser.getSubject();
        String email = oidcUser.getEmail() != null ? oidcUser.getEmail()
                : oidcUser.getClaimAsString("preferred_username");
        String name = oidcUser.getFullName() != null ? oidcUser.getFullName()
                : oidcUser.getClaimAsString("name");

        User user = userService.provisionOnLogin(provider, subject, email, name);

        Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
        user.getRoles().forEach(role -> authorities.add(new SimpleGrantedAuthority(role.authority())));

        String nameAttributeKey = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        return (nameAttributeKey == null || nameAttributeKey.isBlank())
                ? new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo())
                : new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), nameAttributeKey);
    }
}
