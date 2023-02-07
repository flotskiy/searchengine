package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

public interface PageRepository extends JpaRepository<PageEntity, Long> {

    PageEntity findPageEntityByPathAndSiteEntity(String path, SiteEntity siteEntity);

    int countPageEntitiesBySiteEntity(SiteEntity siteEntity);

    @Query(value = "SELECT COUNT(*) * 95 / 100 FROM Pages WHERE site_id = :siteId", nativeQuery = true)
    float get95perCentPagesLimit(@Param("siteId") int siteId);

    @Query(
            value = "SELECT p.id, p.code, p.content, p.path, p.site_id " +
                    "FROM Pages p " +
                    "JOIN Search_index s " +
                    "ON p.id = s.page_id " +
                    "WHERE s.lemma_id IN " +
                    "(SELECT id FROM Lemmas l WHERE l.lemma = :lemma)",
            nativeQuery = true
    )
    Iterable<PageEntity> getPagesByLemma(@Param("lemma") String lemma);

    @Query(
            value = "SELECT p.id, p.code, p.content, p.path, p.site_id " +
                    "FROM Pages p " +
                    "JOIN Search_index s " +
                    "ON p.id = s.page_id " +
                    "WHERE s.lemma_id IN " +
                    "(SELECT id FROM Lemmas l WHERE l.lemma = :lemma AND l.site_id = :siteId)",
            nativeQuery = true
    )
    Iterable<PageEntity> getPagesByLemmaAndSiteId(@Param("lemma") String lemma, @Param("siteId") int siteId);
}
