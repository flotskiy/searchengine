package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.exceptions.SiteException;
import searchengine.model.*;
import searchengine.repository.SiteRepository;
import searchengine.util.JsoupUtil;
import searchengine.util.PropertiesHolder;
import searchengine.util.StringUtil;

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

    private static final String RESULT_KEY = "result";
    private static final String ERROR_KEY = "error";

    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageCrawlerService pageCrawlerService;
    private final PropertiesHolder properties;

    @Override
    public ResponseEntity<Map<String, Object>> startIndexing() {
        Map<String, Object> response = new HashMap<>();
        if (isIndexingNow()) {
            response.put(RESULT_KEY, false);
            response.put(ERROR_KEY, "Indexing already started");
            return ResponseEntity.badRequest().body(response);
        } else {
            new Thread(this::indexAll).start();
            response.put(RESULT_KEY, true);
            return ResponseEntity.ok(response);
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        Map<String, Object> result = new HashMap<>();
        if (isIndexingNow()) {
            shutdown();
            result.put(RESULT_KEY, true);
            return ResponseEntity.ok(result);
        } else {
            result.put(RESULT_KEY, false);
            result.put(ERROR_KEY, "Indexing is not started");
            return ResponseEntity.badRequest().body(result);
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> indexPage(String path) {
        Map<String, Object> response = new HashMap<>();
        if (isPageBelongsToSiteSpecified(path)) {
            new Thread(() -> indexSinglePage(path)).start();
            response.put(RESULT_KEY, true);
            return ResponseEntity.ok(response);
        }
        response.put(RESULT_KEY, false);
        response.put(ERROR_KEY, "Page is located outside the sites specified in the configuration file");
        return ResponseEntity.badRequest().body(response);
    }

    public SiteEntity createSiteToHandleSinglePage(String siteHomePageToSave) {
        SiteEntity siteEntity = null;
        String currentSiteHomePage;
        for (Site site : sites.getSites()) {
            currentSiteHomePage = StringUtil.getStartPage(site.getUrl());
            if (siteHomePageToSave.equalsIgnoreCase(currentSiteHomePage)) {
                siteEntity = prepareSiteIndexing(site);
                break;
            }
        }
        return siteEntity;
    }

    private boolean isIndexingNow() {
        return !pageCrawlerService.getForkJoinPool().isQuiescent();
    }

    private void indexAll() {
        pageCrawlerService.setForkJoinPool(new ForkJoinPool());
        pageCrawlerService.setLemmasMap(new HashMap<>());
        pageCrawlerService.setIndexEntityMap(new HashMap<>());
        pageCrawlerService.setWebpagesPathSet(Collections.synchronizedSet(new HashSet<>()));
        for (Site site : sites.getSites()) {
            new Thread(() -> indexSingleSite(site)).start();
        }
    }

    private void shutdown() {
        pageCrawlerService.getForkJoinPool().shutdownNow();
    }

    private boolean isPageBelongsToSiteSpecified(String pageUrl) {
        if (pageUrl == null || pageUrl.isEmpty()) {
            return false;
        }
        List<Site> siteList = sites.getSites();
        for (Site site : siteList) {
            String siteHomePage = StringUtil.getStartPage(site.getUrl());
            String passedHomePage = StringUtil.getStartPage(pageUrl);
            if (passedHomePage.equalsIgnoreCase(siteHomePage)) {
                return true;
            }
        }
        return false;
    }

    private void indexSingleSite(Site site) {
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

    private void indexSinglePage(String pageUrl) {
        String siteUrlFromPageUrl = StringUtil.getStartPage(pageUrl);
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(siteUrlFromPageUrl);
        if (siteEntity == null) {
            siteEntity = createSiteToHandleSinglePage(siteUrlFromPageUrl);
        }
        Connection connection = JsoupUtil.getConnection(pageUrl, properties.getUseragent(), properties.getReferrer());
        Connection.Response response;
        Document document;
        try {
            response = connection.execute();
            document = connection.get();
        } catch (IOException e) {
            throw new SiteException("Connection requests failed");
        }
        String pathToSave = StringUtil.getPathToSave(pageUrl, siteEntity.getUrl());
        int httpStatusCode = response.statusCode();

        PageEntity pageEntityToDelete =
                pageCrawlerService.getPageRepository().findPageEntityByPathAndSiteEntity(pathToSave, siteEntity);
        pageCrawlerService.getPageRepository().delete(pageEntityToDelete);
        PageEntity pageEntity = pageCrawlerService.createPageEntity(pathToSave, httpStatusCode, siteEntity);
        String html = "";
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

    private void fillInLemmasAndIndexTables(Site site) {
        String homePage = StringUtil.getStartPage(site.getUrl());
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

    private void fixError(Site site, Exception e) {
        String error = getErrorMessage(e);
        String homePage = StringUtil.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(new Date());
        siteEntity.setStatus(Status.FAILED);
        siteEntity.setLastError(error);
        siteRepository.save(siteEntity);
    }

    private void markAsIndexed(Site site) {
        String homePage = StringUtil.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(new Date());
        siteEntity.setStatus(Status.INDEXED);
        siteRepository.save(siteEntity);
    }

    private String getErrorMessage(Exception e) {
        if (e instanceof CancellationException || e instanceof InterruptedException) {
            return properties.getInterruptedByUserMessage();
        } else if (e instanceof CertificateExpiredException || e instanceof SSLHandshakeException
                || e instanceof CertPathValidatorException) {
            return properties.getCertificateError();
        } else {
            e.printStackTrace();
            return properties.getUnknownError() + " (" + e + ")";
        }
    }
}
