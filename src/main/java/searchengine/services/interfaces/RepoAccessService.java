package searchengine.services.interfaces;

import searchengine.config.Site;
import searchengine.model.*;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface RepoAccessService {

    SiteEntity findSiteEntityByUrl(String url);

    SiteEntity prepareSiteIndexing(Site site);

    List<SiteEntity> getAllSites();

    boolean existsByStatus(Status status);

    PageEntity createPageEntity(String path, int code, SiteEntity siteEntity);

    PageEntity deleteOldPageEntity(String path, SiteEntity siteEntity);

    void savePageContentAndSiteStatusTime(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity);

    float getAbsRelevance(int pageId, Collection<Integer> lemmaIds);

    Set<Integer> findPagesIdsByLemmaIdIn(Collection<Integer> lemmaIds);

    void fixSiteStatusAfterSinglePageIndexed(SiteEntity siteEntity);

    void markSiteAsIndexed(Site site);

    void fixSiteIndexingError(Site site, Exception exception);

    List<PageEntity> getAllPagesByIdIn(Collection<Integer> pageIdSet);

    int countPageEntitiesBySiteEntity(SiteEntity siteEntity);

    float get95perCentPagesLimit(int siteId);

    void saveLemmaCollection(Collection<LemmaEntity> lemmaEntityCollection);

    void saveIndexCollection(Collection<IndexEntity> indexEntityCollection);

    int countLemmaEntitiesBySiteId(SiteEntity siteEntity);

    List<LemmaEntity> findLemmaEntitiesByLemmaIn(Collection<String> list);

    List<LemmaEntity> findLemmaEntitiesByLemmaInAndSiteId(Collection<String> list, SiteEntity siteEntity);

    void reduceByOneLemmaFrequencies(Collection<String> lemmas, int siteId);

    void deleteLemmasWithLowFrequencies(int siteId);
}
