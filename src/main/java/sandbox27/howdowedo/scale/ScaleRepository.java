package sandbox27.howdowedo.scale;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScaleRepository extends JpaRepository<Scale, Long> {

    List<Scale> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
