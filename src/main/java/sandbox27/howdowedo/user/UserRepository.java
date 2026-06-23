package sandbox27.howdowedo.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndSubject(String provider, String subject);

    @Query("select count(u) from User u join u.roles r where r = :role")
    long countByRole(@Param("role") Role role);
}
