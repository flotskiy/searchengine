package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.LemmaEntity;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {
}
