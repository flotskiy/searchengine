package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.Collection;
import java.util.List;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {

    LemmaEntity findTopByLemmaAndSiteId(String lemma, SiteEntity siteEntity);

    int countLemmaEntitiesBySiteId(SiteEntity siteEntity);

    List<LemmaEntity> findLemmaEntitiesByLemmaIn(Collection<String> list);
}
