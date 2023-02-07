package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.exceptions.SiteException;
import searchengine.model.*;
import searchengine.repository.SiteRepository;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
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
    private final PageCrawlerService pageCrawlerService;

    @Override
    public void indexAll() {
        pageCrawlerService.setForkJoinPool(new ForkJoinPool());
        pageCrawlerService.setLemmasMap(new HashMap<>());
        pageCrawlerService.setIndexEntityMap(new HashMap<>());
        pageCrawlerService.setWebpagesPathSet(Collections.synchronizedSet(new HashSet<>()));
        for (Site site : sites.getSites()) {
            new Thread(() -> indexSingleSite(site)).start();
        }
    }

    @Override
    public void stopIndexing() {
        pageCrawlerService.getForkJoinPool().shutdownNow();
    }

    @Override
    public boolean isIndexingNow() {
        return !pageCrawlerService.getForkJoinPool().isQuiescent();
    }

    @Override
    public boolean isPageBelongsToSiteSpecified(String pageUrl) {
        if (pageUrl == null || pageUrl.isEmpty()) {
            return false;
        }
        List<Site> siteList = sites.getSites();
        for (Site site : siteList) {
            String siteHomePage = pageCrawlerService.getStringService().getStartPage(site.getUrl());
            String passedHomePage = pageCrawlerService.getStringService().getStartPage(pageUrl);
            if (passedHomePage.equalsIgnoreCase(siteHomePage)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void indexSinglePage(String pageUrl) {
        String siteUrlFromPageUrl = pageCrawlerService.getStringService().getStartPage(pageUrl);
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(siteUrlFromPageUrl);
        if (siteEntity == null) {
            siteEntity = createSiteToHandleSinglePage(siteUrlFromPageUrl);
        }
        Connection connection = pageCrawlerService.getConnection(pageUrl);
        Connection.Response response;
        Document document;
        try {
            response = connection.execute();
            document = connection.get();
        } catch (IOException e) {
            throw new SiteException("Connection requests failed");
        }
        String startPage = siteEntity.getUrl();
        String pathToSave = pageCrawlerService.getStringService().cutProtocolAndHost(pageUrl, startPage);
        pathToSave = pathToSave.contains(".") ? pathToSave : pathToSave + "/";
        String html = "";
        int httpStatusCode = response.statusCode();

        PageEntity pageEntityToDelete =
                pageCrawlerService.getPageRepository().findPageEntityByPathAndSiteEntity(pathToSave, siteEntity);
        pageCrawlerService.getPageRepository().delete(pageEntityToDelete);
        PageEntity pageEntity = pageCrawlerService.createPageEntity(pathToSave, httpStatusCode, siteEntity);
        if (httpStatusCode != 200) {
            pageCrawlerService.savePageEntityAndSiteStatus(pageEntity, html, siteEntity);
        } else {
            html = document.outerHtml();
            if (pageEntityToDelete != null) {
                correctLemmaFrequency(html, siteEntity);
            }
            pageCrawlerService.savePageEntityAndSiteStatus(pageEntity, html, siteEntity);
            pageCrawlerService.handleLemmasAndIndexOnSinglePage(html, pageEntity, siteEntity);
        }
        siteEntity.setStatus(Status.INDEXED);
        pageCrawlerService.getSiteRepository().save(siteEntity);
    }

    public void indexSingleSite(Site site) {
        try {
            PageCrawlerUnit pageCrawlerUnit = handleSite(site);
            pageCrawlerService.getForkJoinPool().invoke(pageCrawlerUnit);
            fillInLemmasAndIndexTables(site);
            markAsIndexed(site);
        } catch (Exception exception) {
            exception.printStackTrace();
            fillInLemmasAndIndexTables(site);
            fixError(site, exception);
        }
    }

    public SiteEntity createSiteToHandleSinglePage(String siteHomePageToSave) {
        SiteEntity siteEntity = null;
        String currentSiteHomePage;
        for (Site site : sites.getSites()) {
            currentSiteHomePage = pageCrawlerService.getStringService().getStartPage(site.getUrl());
            if (siteHomePageToSave.equalsIgnoreCase(currentSiteHomePage)) {
                siteEntity = prepareSiteIndexing(site);
                break;
            }
        }
        return siteEntity;
    }

    private void fillInLemmasAndIndexTables(Site site) {
        String homePage = pageCrawlerService.getStringService().getStartPage(site.getUrl());
        int siteEntityId = siteRepository.findSiteEntityByUrl(homePage).getId();
        Map<String, LemmaEntity> lemmaEntityMap = pageCrawlerService.getLemmasMap().get(siteEntityId);
        pageCrawlerService.getLemmaRepository().saveAll(lemmaEntityMap.values());
        pageCrawlerService.getLemmasMap().get(siteEntityId).clear();
        pageCrawlerService.getIndexRepository().saveAll(pageCrawlerService.getIndexEntityMap().get(siteEntityId));
        pageCrawlerService.getIndexEntityMap().get(siteEntityId).clear();
    }

    private void correctLemmaFrequency(String text, SiteEntity site) {
        List<Map<String, Integer>> mapList = pageCrawlerService.getUniqueLemmasListOfMaps(text);
        for (String lemma : mapList.get(2).keySet()) {
            LemmaEntity lemmaEntity = pageCrawlerService.getLemmaRepository().findTopByLemmaAndSiteId(lemma, site);
            if (lemmaEntity != null) {
                handleLemma(lemmaEntity);
            }
        }
    }

    private void handleLemma(LemmaEntity lemmaEntity) {
        int frequency = lemmaEntity.getFrequency();
        if (frequency == 1) {
            pageCrawlerService.getLemmaRepository().delete(lemmaEntity);
        } else {
            frequency -= 1;
            lemmaEntity.setFrequency(frequency);
            pageCrawlerService.getLemmaRepository().save(lemmaEntity);
        }
    }

    private PageCrawlerUnit handleSite(Site siteToHandle) {
        SiteEntity siteEntity = prepareSiteIndexing(siteToHandle);
        Map<String, LemmaEntity> stringLemmaEntityMap = new HashMap<>();
        pageCrawlerService.getLemmasMap().put(siteEntity.getId(), stringLemmaEntityMap);
        Set<IndexEntity> indexEntitySet = new HashSet<>();
        pageCrawlerService.getIndexEntityMap().put(siteEntity.getId(), indexEntitySet);

        PageCrawlerUnit pageCrawlerUnit = pageCrawlerService.createPageCrawler();
        String siteHomePage = siteEntity.getUrl();
        pageCrawlerService.getWebpagesPathSet().add(siteHomePage);
        pageCrawlerUnit.setPagePath(siteHomePage);
        pageCrawlerUnit.setSiteEntity(siteEntity);
        return pageCrawlerUnit;
    }

    private SiteEntity prepareSiteIndexing(Site site) {
        String homePage = pageCrawlerService.getStringService().getStartPage(site.getUrl());
        siteRepository.deleteSiteEntityByUrl(homePage);
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setStatusTime(new Date());
        siteEntity.setUrl(homePage);
        siteEntity.setName(site.getName());
        return siteRepository.save(siteEntity);
    }

    private void fixError(Site site, Exception e) {
        String error = getErrorMessage(e);
        String homePage = pageCrawlerService.getStringService().getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(new Date());
        siteEntity.setStatus(Status.FAILED);
        siteEntity.setLastError(error);
        siteRepository.save(siteEntity);
    }

    private void markAsIndexed(Site site) {
        String homePage = pageCrawlerService.getStringService().getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(new Date());
        siteEntity.setStatus(Status.INDEXED);
        siteRepository.save(siteEntity);
    }

    private String getErrorMessage(Exception e) {
        if (e instanceof CancellationException || e instanceof InterruptedException) {
            return interruptedByUserMessage;
        } else if (e instanceof CertificateExpiredException || e instanceof SSLHandshakeException
                || e instanceof CertPathValidatorException) {
            return certificateError;
        } else {
            e.printStackTrace();
            return unknownError + " (" + e + ")";
        }
    }
}
