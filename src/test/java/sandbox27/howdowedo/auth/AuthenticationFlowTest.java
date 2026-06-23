package sandbox27.howdowedo.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageIsPublicAndListsConfiguredProvider() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Microsoft 365")))
                .andExpect(content().string(containsString("/oauth2/authorization/azure")));
    }

    @Test
    void protectedPageRedirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void oauthCallbackIsMountedAtCustomPath() throws Exception {
        // Hitting the callback without a valid authorization request fails authentication and is
        // redirected to the login page - proving the OAuth2 login filter listens on /auth/callback.
        mockMvc.perform(get("/auth/callback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void authenticatedUserSeesPersonalisedHome() throws Exception {
        mockMvc.perform(get("/").with(oauth2Login()
                        .attributes(attrs -> attrs.put("name", "Ada Lovelace"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ada Lovelace")));
    }
}
