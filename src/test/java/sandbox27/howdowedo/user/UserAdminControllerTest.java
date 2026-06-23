package sandbox27.howdowedo.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.user.Role;
import sandbox27.howdowedo.user.User;
import sandbox27.howdowedo.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserAdminControllerTest {

    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority(Role.ADMINISTRATOR.authority());
    private static final SimpleGrantedAuthority USER = new SimpleGrantedAuthority(Role.USER.authority());

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void administratorCanOpenUserAdministration() throws Exception {
        mockMvc.perform(get("/admin/users").with(oauth2Login().authorities(ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdministratorIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/users").with(oauth2Login().authorities(USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanAssignRoles() throws Exception {
        User member = newMember();

        mockMvc.perform(post("/admin/users/{id}/roles", member.getId())
                        .param("roles", Role.SURVEY_MANAGER.name())
                        .with(oauth2Login().authorities(ADMIN))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        User reloaded = userRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getRoles()).containsExactlyInAnyOrder(Role.USER, Role.SURVEY_MANAGER);
    }

    private User newMember() {
        User user = new User("azure", "member-sub", "member@example.com", "Member");
        user.addRole(Role.USER);
        return userRepository.save(user);
    }
}
