package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {

    LemmaEntity findTopByLemmaAndSiteId(String lemma, SiteEntity siteEntity);

    int countLemmaEntitiesBySiteId(SiteEntity siteEntity);
}
