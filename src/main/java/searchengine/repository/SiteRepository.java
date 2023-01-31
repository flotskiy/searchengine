package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.SiteEntity;

public interface SiteRepository extends JpaRepository<SiteEntity, Long> {
}
