package searchengine.services.interfaces;

import searchengine.config.Site;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

public interface SiteAndPageService {

    SiteEntity findSiteEntityByUrl(String url);

    SiteEntity prepareSiteIndexing(Site site);

    PageEntity createPageEntity(String path, int code, SiteEntity siteEntity);

    PageEntity deleteOldPageEntity(String path, SiteEntity siteEntity);

    void savePageContentAndSiteStatus(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity);

    void fixSiteStatusAfterSinglePageIndexed(SiteEntity siteEntity);

    void markSiteAsIndexed(Site site);

    void fixSiteIndexingError(Site site, Exception exception);
}
