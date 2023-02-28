package searchengine.services.interfaces;

import searchengine.config.Site;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.Collection;

public interface RepoAccessService {

    SiteEntity findSiteEntityByUrl(String url);

    SiteEntity prepareSiteIndexing(Site site);

    PageEntity createPageEntity(String path, int code, SiteEntity siteEntity);

    PageEntity deleteOldPageEntity(String path, SiteEntity siteEntity);

    void savePageContentAndSiteStatus(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity);

    void fixSiteStatusAfterSinglePageIndexed(SiteEntity siteEntity);

    void markSiteAsIndexed(Site site);

    void fixSiteIndexingError(Site site, Exception exception);

    LemmaEntity findLemmaEntityByLemmaAndSiteId(String lemma, SiteEntity siteEntity);

    void saveLemma(LemmaEntity lemmaEntity);

    void saveLemmaCollection(Collection<LemmaEntity> lemmaEntityCollection);

    void saveIndexCollection(Collection<IndexEntity> indexEntityCollection);

    void correctSingleLemmaFrequency(LemmaEntity lemmaEntity);
}
