package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

public interface PageRepository extends JpaRepository<PageEntity, Integer> {

    PageEntity findPageEntityByPathAndSite(String path, SiteEntity siteEntity);

    int countPageEntitiesBySite(SiteEntity siteEntity);

    @Query(value = "SELECT COUNT(*) * :limit / 100 FROM Pages WHERE site_id = :siteId", nativeQuery = true)
    float getPageFrequencyOccurrence(@Param("limit") int limit, @Param("siteId") int siteId);
}
