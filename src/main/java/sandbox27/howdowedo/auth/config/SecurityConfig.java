package sandbox27.howdowedo.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import sandbox27.howdowedo.auth.service.ProvisioningOidcUserService;
import sandbox27.howdowedo.user.Role;

/**
 * Central security configuration.
 *
 * <p>Authentication is delegated entirely to OAuth2 / OIDC identity providers. Which providers are
 * available is driven purely by the {@code spring.security.oauth2.client.registration.*} entries in
 * configuration - no code change is required to add another provider (e.g. Google). The custom login
 * page ({@code /login}) renders one button per registered provider.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String LOGIN_PAGE = "/login";

    /** Where identity providers redirect back to after authentication. Must match the configured
     *  {@code redirect-uri} ({baseUrl}/auth/callback). A single fixed path serves all providers -
     *  Spring resolves which registration to use from the OAuth2 {@code state} parameter. */
    static final String CALLBACK_PATH = "/auth/callback";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ProvisioningOidcUserService oidcUserService) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(LOGIN_PAGE, "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/admin/**").hasRole(Role.ADMINISTRATOR.name())
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .loginPage(LOGIN_PAGE)
                        .redirectionEndpoint(redirect -> redirect.baseUri(CALLBACK_PATH))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService)))
                .logout(logout -> logout
                        .logoutSuccessUrl(LOGIN_PAGE + "?logout")
                        .permitAll());
        return http.build();
    }
}
