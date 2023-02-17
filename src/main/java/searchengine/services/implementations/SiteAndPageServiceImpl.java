package searchengine.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.SiteAndPageService;
import searchengine.util.PropertiesHolder;
import searchengine.util.StringUtil;

import javax.net.ssl.SSLHandshakeException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.util.Date;
import java.util.concurrent.CancellationException;

@Service
@RequiredArgsConstructor
@Log4j2
public class SiteAndPageServiceImpl implements SiteAndPageService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final PropertiesHolder properties;

    @Override
    public SiteEntity findSiteEntityByUrl(String url) {
        return siteRepository.findSiteEntityByUrl(url);
    }

    @Override
    public SiteEntity prepareSiteIndexing(Site site) {
        String homePage = StringUtil.getStartPage(site.getUrl());
        SiteEntity oldSiteEntity = siteRepository.findSiteEntityByUrl(homePage);
        if (oldSiteEntity != null) {
            oldSiteEntity.setStatus(Status.INDEXING);
            siteRepository.save(oldSiteEntity);
            siteRepository.deleteSiteEntityByUrl(homePage);
        }
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setStatusTime(new Date());
        siteEntity.setUrl(homePage);
        siteEntity.setName(site.getName());
        return siteRepository.save(siteEntity);
    }

    @Override
    public PageEntity createPageEntity(String path, int code, SiteEntity siteEntity) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(path);
        pageEntity.setCode(code);
        pageEntity.setSiteEntity(siteEntity);
        return pageEntity;
    }

    @Override
    public PageEntity deleteOldPageEntity(String path, SiteEntity siteEntity) {
        PageEntity pageEntityToDelete = pageRepository.findPageEntityByPathAndSiteEntity(path, siteEntity);
        pageRepository.delete(pageEntityToDelete);
        return pageEntityToDelete;
    }

    @Override
    public void savePageContentAndSiteStatus(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity) {
        pageEntity.setContent(pageHtml);
        pageRepository.save(pageEntity);
        siteEntity.setStatusTime(new Date());
        siteRepository.save(siteEntity);
    }

    @Override
    public void markSiteAsIndexed(Site site) {
        String homePage = StringUtil.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(new Date());
        siteEntity.setStatus(Status.INDEXED);
        siteRepository.save(siteEntity);
    }

    @Override
    public void fixSiteIndexingError(Site site, Exception e) {
        String error = getErrorMessage(e);
        String homePage = StringUtil.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(new Date());
        siteEntity.setStatus(Status.FAILED);
        siteEntity.setLastError(error);
        siteRepository.save(siteEntity);
    }

    @Override
    public void fixSiteStatusAfterSinglePageIndexed(SiteEntity site) {
        site.setStatus(Status.INDEXED);
        siteRepository.save(site);
    }

    private String getErrorMessage(Exception e) {
        if (e instanceof CancellationException || e instanceof InterruptedException) {
            log.warn(e);
            return properties.getInterruptedByUserMessage();
        } else if (e instanceof CertificateExpiredException || e instanceof SSLHandshakeException
                || e instanceof CertPathValidatorException) {
            log.warn(e);
            return properties.getCertificateError();
        } else {
            log.warn(e);
            return properties.getUnknownError() + " (" + e + ")";
        }
    }
}
