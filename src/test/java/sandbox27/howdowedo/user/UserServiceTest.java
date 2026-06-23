package sandbox27.howdowedo.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.NotFoundException;
import sandbox27.howdowedo.user.Role;
import sandbox27.howdowedo.user.User;
import sandbox27.howdowedo.user.UserRepository;
import sandbox27.howdowedo.user.UserService;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;

    @Test
    void firstUserBecomesAdministrator() {
        User user = userService.provisionOnLogin("azure", "sub-1", "ada@example.com", "Ada");

        assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.USER, Role.ADMINISTRATOR);
    }

    @Test
    void subsequentUserIsPlainUser() {
        userService.provisionOnLogin("azure", "sub-1", "ada@example.com", "Ada");

        User second = userService.provisionOnLogin("azure", "sub-2", "bob@example.com", "Bob");

        assertThat(second.getRoles()).containsExactly(Role.USER);
    }

    @Test
    void provisioningIsIdempotentAndRefreshesProfile() {
        User first = userService.provisionOnLogin("azure", "sub-1", "old@example.com", "Old Name");
        User again = userService.provisionOnLogin("azure", "sub-1", "new@example.com", "New Name");

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(again.getName()).isEqualTo("New Name");
        assertThat(again.getEmail()).isEqualTo("new@example.com");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void setRolesKeepsUserRoleAndGrantsExtraRole() {
        userService.provisionOnLogin("azure", "admin", "admin@example.com", "Admin"); // first -> admin
        User member = userService.provisionOnLogin("azure", "member", "m@example.com", "Member");

        User updated = userService.setRoles(member.getId(), Set.of(Role.SURVEY_MANAGER));

        assertThat(updated.getRoles()).containsExactlyInAnyOrder(Role.USER, Role.SURVEY_MANAGER);
    }

    @Test
    void cannotRemoveTheLastAdministrator() {
        User admin = userService.provisionOnLogin("azure", "admin", "admin@example.com", "Admin");

        assertThatThrownBy(() -> userService.setRoles(admin.getId(), Set.of(Role.USER)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void setRolesOnUnknownUserThrowsNotFound() {
        assertThatThrownBy(() -> userService.setRoles(999L, Set.of(Role.USER)))
                .isInstanceOf(NotFoundException.class);
    }
}
