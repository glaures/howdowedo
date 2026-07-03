package sandbox27.howdowedo.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    List<Survey> findByCreatedByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long createdByUserId);

    List<Survey> findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(Collection<Long> ids);

    boolean existsByTitle(String title);
}
