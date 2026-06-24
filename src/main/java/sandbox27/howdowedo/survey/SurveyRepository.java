package sandbox27.howdowedo.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    List<Survey> findByCreatedByUserIdOrderByCreatedAtDesc(Long createdByUserId);

    List<Survey> findByIdInOrderByCreatedAtDesc(Collection<Long> ids);

    boolean existsByTitle(String title);
}
