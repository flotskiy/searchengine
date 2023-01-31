package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.PageEntity;

public interface PageRepository extends JpaRepository<PageEntity, Long> {
}
