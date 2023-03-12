package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.Collection;
import java.util.List;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {

    int countLemmaEntitiesBySiteId(SiteEntity siteEntity);

    List<LemmaEntity> findLemmaEntitiesByLemmaIn(Collection<String> list);

    List<LemmaEntity> findLemmaEntitiesByLemmaInAndSiteId(Collection<String> list, SiteEntity siteEntity);

    @Modifying
    @Transactional
    @Query(value = "UPDATE lemmas SET frequency = frequency - 1 WHERE site_id = :siteId AND lemma IN :lemmas",
            nativeQuery = true)
    void reduceByOneLemmaFrequencies(@Param("siteId") int siteId, @Param("lemmas") Collection<String> lemmas);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM lemmas WHERE site_id = :siteId AND frequency < 1", nativeQuery = true)
    void deleteLemmasWithLowFrequencies(@Param("siteId") int siteId);
}
