package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

public interface PageRepository extends JpaRepository<PageEntity, Long> {

    PageEntity findPageEntityByPathAndSiteEntity(String path, SiteEntity siteEntity);

    int countPageEntitiesBySiteEntity(SiteEntity siteEntity);
}
