package sandbox27.howdowedo.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SurveyAccessRepository extends JpaRepository<SurveyAccess, Long> {

    List<SurveyAccess> findBySurveyId(Long surveyId);

    List<SurveyAccess> findByUserId(Long userId);

    Optional<SurveyAccess> findBySurveyIdAndUserId(Long surveyId, Long userId);
}
