package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.SiteRepository;

import javax.net.ssl.SSLHandshakeException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    @Value("${error.interrupted}")
    private String interruptedByUserMessage;
    @Value("${error.certificate}")
    private String certificateError;
    @Value("${error.unknown}")
    private String unknownError;

    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageCrawlerFactory pageCrawlerFactory;
    private ForkJoinPool forkJoinPool = new ForkJoinPool();

    @Override
    public void indexAll() {
        forkJoinPool = new ForkJoinPool();
        pageCrawlerFactory.setWebpagesPathSet(Collections.synchronizedSet(new HashSet<>()));
        for (Site site : sites.getSites()) {
            new Thread(() -> indexSingleSite(site)).start();
        }
    }

    @Override
    public void stopIndexing() {
        forkJoinPool.shutdownNow();
    }

    public boolean isIndexingNow() {
        return !forkJoinPool.isQuiescent();
    }

    public void indexSingleSite(Site site) {
        try {
            PageCrawler pageCrawler = handleSite(site);
            forkJoinPool.invoke(pageCrawler);
            markAsIndexed(site);
        } catch (Exception exception) {
            exception.printStackTrace();
            fixError(site, exception);
        }
    }

    private PageCrawler handleSite(Site siteToHandle) {
        SiteEntity siteEntity = prepareSiteIndexing(siteToHandle);
        PageCrawler pageCrawler = pageCrawlerFactory.createPageCrawler();
        String siteHomePage = siteEntity.getUrl();
        pageCrawlerFactory.getWebpagesPathSet().add(siteHomePage);
        pageCrawler.setPagePath(siteHomePage);
        pageCrawler.setSiteEntity(siteEntity);
        return pageCrawler;
    }

    private SiteEntity prepareSiteIndexing(Site site) {
        String homePage = pageCrawlerFactory.getStartPage(site.getUrl());
        siteRepository.deleteSiteEntityByUrl(homePage);
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setStatusTime(new Date());
        siteEntity.setUrl(homePage);
        siteEntity.setName(site.getName());
        return siteRepository.save(siteEntity);
    }

    private void fixError(Site site, Exception ex) {
        String error = getErrorMessage(ex);
        String homePage = pageCrawlerFactory.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(new Date());
        siteEntity.setStatus(Status.FAILED);
        siteEntity.setLastError(error);
        siteRepository.save(siteEntity);
    }

    private void markAsIndexed(Site site) {
        String homePage = pageCrawlerFactory.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(new Date());
        siteEntity.setStatus(Status.INDEXED);
        siteRepository.save(siteEntity);
    }

    private String getErrorMessage(Exception e) {
        if (e instanceof CancellationException || e instanceof InterruptedException) {
            return interruptedByUserMessage;
        } else if (e instanceof CertificateExpiredException
                || e instanceof SSLHandshakeException || e instanceof CertPathValidatorException) {
            return certificateError;
        } else {
            return unknownError;
        }
    }
}
