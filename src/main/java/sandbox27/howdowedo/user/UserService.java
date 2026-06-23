package sandbox27.howdowedo.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.NotFoundException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * User provisioning and role administration.
 */
@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /**
     * Returns the user for the given provider identity, creating one on first login. New users get
     * the {@link Role#USER} role; the very first user in the system additionally becomes
     * {@link Role#ADMINISTRATOR} so the instance is manageable out of the box.
     */
    @Transactional
    public User provisionOnLogin(String provider, String subject, String email, String name) {
        return users.findByProviderAndSubject(provider, subject)
                .map(existing -> {
                    existing.updateProfile(email, name);
                    return existing;
                })
                .orElseGet(() -> createUser(provider, subject, email, name));
    }

    private User createUser(String provider, String subject, String email, String name) {
        User user = new User(provider, subject, email, name);
        user.addRole(Role.USER);
        if (users.count() == 0) {
            user.addRole(Role.ADMINISTRATOR);
        }
        return users.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return users.findAll();
    }

    /**
     * Replaces a user's roles. {@link Role#USER} is always kept, and the last remaining
     * administrator cannot be demoted (otherwise nobody could manage roles anymore).
     */
    @Transactional
    public User setRoles(Long userId, Set<Role> roles) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));

        Set<Role> updated = EnumSet.of(Role.USER);
        updated.addAll(roles);

        boolean removesAdmin = user.hasRole(Role.ADMINISTRATOR) && !updated.contains(Role.ADMINISTRATOR);
        if (removesAdmin && users.countByRole(Role.ADMINISTRATOR) <= 1) {
            throw new IllegalStateException("Cannot remove the last administrator");
        }

        user.setRoles(updated);
        return user;
    }
}
