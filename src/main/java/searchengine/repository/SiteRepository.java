package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;

public interface SiteRepository extends JpaRepository<SiteEntity, Long> {

    @Transactional
    void deleteSiteEntityByUrl(String url);

    SiteEntity findSiteEntityByUrl(String url);
}
