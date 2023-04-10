package searchengine.services.implementations;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.ApiResponse;
import searchengine.exceptions.SiteException;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.LemmatizerService;
import searchengine.services.PageCrawlerUnit;
import searchengine.services.interfaces.IndexingService;
import searchengine.util.JsoupUtil;
import searchengine.util.PropertiesHolder;
import searchengine.util.StringUtil;

import javax.net.ssl.SSLHandshakeException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Log4j2
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private final LemmatizerService lemmatizerService;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    @Getter
    private final PropertiesHolder properties;

    private volatile boolean isIndexing = false;
    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    @Getter
    private Set<String> webpagesPathSet;
    private ConcurrentMap<Integer, Map<String, LemmaEntity>> lemmasMapGropedBySiteId;
    private ConcurrentMap<Integer, Set<IndexEntity>> indexEntityMapGropedBySiteId;
    @Getter
    private ConcurrentMap<String, Status> siteStatusMap;

    @Override
    public ResponseEntity<ApiResponse> startIndexing() {
        ApiResponse apiResponse = new ApiResponse();
        if (isIndexing) {
            apiResponse.setResult(false);
            apiResponse.setError("Indexing already started");
        } else {
            new Thread(this::indexAll).start();
            apiResponse.setResult(true);
        }
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<ApiResponse> stopIndexing() {
        ApiResponse apiResponse = new ApiResponse();
        if (isIndexing) {
            shutdown();
            apiResponse.setResult(true);
        } else {
            apiResponse.setResult(false);
            apiResponse.setError("Indexing is not started");
        }
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<ApiResponse> indexPage(String path) {
        ApiResponse apiResponse = new ApiResponse();
        try {
            if (isPageBelongsToSiteSpecified(path)) {
                new Thread(() -> indexSinglePage(path)).start();
                apiResponse.setResult(true);
            } else {
                apiResponse.setResult(false);
                apiResponse.setError("Page is located outside the sites specified in the configuration file");
            }
        } catch (SiteException siteException) {
            apiResponse.setResult(false);
            apiResponse.setError("Path incorrect");
        }
        return ResponseEntity.ok(apiResponse);
    }

    public float calculateLemmaRank(
            String lemma, Map<String, Integer> titleLemmasCount, Map<String, Integer> bodyLemmasCount
    ) {
        return titleLemmasCount.getOrDefault(lemma, 0) * properties.getWeightTitle() +
                bodyLemmasCount.getOrDefault(lemma, 0) * properties.getWeightBody();
    }

    public void savePageContentAndSiteStatusTime(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity) {
        if (!forkJoinPool.isTerminating()
                && !forkJoinPool.isTerminated()
                && !siteStatusMap.get(siteEntity.getUrl()).equals(Status.FAILED)) {
            savePageAndSite(pageEntity, pageHtml, siteEntity);
        }
    }

    public void saveSinglePageContentAndSiteStatusTime(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity) {
        savePageAndSite(pageEntity, pageHtml, siteEntity);
    }

    public void extractLemmasAndIndexFromHtml(String html, PageEntity page, SiteEntity site) {
        List<Map<String, Integer>> groupedLemmas = getGroupedLemmas(html);
        for (String lemma : groupedLemmas.get(2).keySet()) { // index 2 contains all lemmas
            Map<String, LemmaEntity> stringLemmaEntityMap = lemmasMapGropedBySiteId.get(site.getId());
            LemmaEntity lemmaEntity = stringLemmaEntityMap.get(lemma);
            if (lemmaEntity == null) {
                lemmaEntity = new LemmaEntity();
                lemmaEntity.setLemma(lemma);
                lemmaEntity.setFrequency(1);
                lemmaEntity.setSite(site);
                lemmasMapGropedBySiteId.get(site.getId()).put(lemma, lemmaEntity);
            } else {
                lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
            }

            float lemmaRank = calculateLemmaRank(lemma, groupedLemmas.get(0), groupedLemmas.get(1)); // 0 - title lemmas, 1 - body lemmas
            IndexEntity indexEntity = new IndexEntity(page, lemmaEntity, lemmaRank);
            indexEntityMapGropedBySiteId.get(site.getId()).add(indexEntity);
        }
    }

    public List<Map<String, Integer>> getGroupedLemmas(String html) {
        Document htmlDocument = JsoupUtil.parse(html);
        String title = htmlDocument.title();
        String bodyText = htmlDocument.body().text();

        Map<String, Integer> titleLemmasCount = lemmatizerService.getLemmasCountMap(title);
        Map<String, Integer> bodyLemmasCount = lemmatizerService.getLemmasCountMap(bodyText);
        Map<String, Integer> titleAndBodyLemmasCount = Stream
                .concat(titleLemmasCount.entrySet().stream(), bodyLemmasCount.entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingInt(Map.Entry::getValue)));

        List<Map<String, Integer>> groupedLemmasList = new ArrayList<>();
        groupedLemmasList.add(titleLemmasCount);
        groupedLemmasList.add(bodyLemmasCount);
        groupedLemmasList.add(titleAndBodyLemmasCount);
        return groupedLemmasList;
    }

    public void indexSinglePage(String pageUrl) {
        SiteEntity siteEntity = findOrCreateNewSiteEntity(pageUrl);
        Connection connection = JsoupUtil.getConnection(pageUrl, properties.getUseragent(), properties.getReferrer());
        Connection.Response response = JsoupUtil.getResponse(connection);
        Document document = JsoupUtil.getDocument(connection);
        String pathToSave = StringUtil.getPathToSave(pageUrl, siteEntity.getUrl());
        int httpStatusCode = response.statusCode();

        PageEntity pageEntityDeleted = deleteOldPageEntity(pathToSave, siteEntity);
        String html = "";
        PageEntity pageEntity = new PageEntity(pathToSave, httpStatusCode, html, siteEntity);
        if (httpStatusCode != 200) {
            saveSinglePageContentAndSiteStatusTime(pageEntity, html, siteEntity);
        } else {
            html = document.outerHtml();
            if (pageEntityDeleted != null) {
                reduceLemmaFrequenciesByOne(html, siteEntity.getId());
            }
            saveSinglePageContentAndSiteStatusTime(pageEntity, html, siteEntity);
            extractLemmasAndIndexFromHtmlOnSinglePage(html, pageEntity, siteEntity);
        }
        fixSiteStatusAfterSinglePageIndexed(siteEntity);
    }

    private SiteEntity createSiteToHandleSinglePage(String siteHomePageToSave) {
        SiteEntity siteEntity = new SiteEntity();
        String currentSiteHomePage;
        for (Site site : sites.getSites()) {
            currentSiteHomePage = StringUtil.getStartPage(site.getUrl());
            if (siteHomePageToSave.equalsIgnoreCase(currentSiteHomePage)) {
                siteEntity = createAndPrepareSiteForIndexing(site);
                break;
            }
        }
        return siteEntity;
    }

    private void indexAll() {
        isIndexing = true;
        forkJoinPool = new ForkJoinPool();
        lemmasMapGropedBySiteId = new ConcurrentHashMap<>();
        indexEntityMapGropedBySiteId = new ConcurrentHashMap<>();
        webpagesPathSet = Collections.synchronizedSet(new HashSet<>());
        siteStatusMap = new ConcurrentHashMap<>();
        for (Site site : sites.getSites()) {
            Thread thread = new Thread(() -> indexSingleSite(site));
            thread.setName(site.getName());
            thread.start();
        }
    }

    private void shutdown() {
        forkJoinPool.shutdownNow();
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
            PageCrawlerUnit pageCrawlerUnit = initCollectionsForSiteAndCreateMainPageCrawlerUnit(site);
            forkJoinPool.invoke(pageCrawlerUnit);
            fillInLemmaAndIndexTables(site);
            markSiteAsIndexed(site);
            log.info("Indexing SUCCESSFULLY completed for site '{}'", site.getName());
        } catch (Exception exception) {
            log.warn("FAILED to complete indexing '{}' due to '{}'", site.getName(), exception);
            fixSiteIndexingError(site, exception);
            clearLemmaAndIndexCollections(site);
        } finally {
            markIndexingCompletionIfApplicable();
        }
    }

    private SiteEntity findOrCreateNewSiteEntity(String url) {
        String siteUrlFromPageUrl = StringUtil.getStartPage(url);
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(siteUrlFromPageUrl);
        if (siteEntity == null) {
            siteEntity = createSiteToHandleSinglePage(siteUrlFromPageUrl);
        }
        return siteEntity;
    }

    private void extractLemmasAndIndexFromHtmlOnSinglePage(String html, PageEntity pageEntity, SiteEntity siteEntity) {
        List<Map<String, Integer>> groupedLemmas = getGroupedLemmas(html);
        Set<String> allPageLemmas = groupedLemmas.get(2).keySet(); // index 2 contains all lemmas
        List<LemmaEntity> singlePageLemmaEntityList =
                lemmaRepository.findLemmaEntitiesByLemmaInAndSite(allPageLemmas, siteEntity);
        Map<String, LemmaEntity> lemmaEntityMap =
                singlePageLemmaEntityList.stream().collect(Collectors.toMap(LemmaEntity::getLemma, Function.identity()));

        Set<LemmaEntity> lemmaEntities = new HashSet<>();
        Set<IndexEntity> indexEntities = new HashSet<>();
        for (String lemma : allPageLemmas) {
            LemmaEntity lemmaEntity = lemmaEntityMap.get(lemma);
            if (lemmaEntity == null) {
                lemmaEntity = new LemmaEntity(lemma, 1, siteEntity);
            } else {
                int currentFrequency = lemmaEntity.getFrequency();
                lemmaEntity.setFrequency(currentFrequency + 1);
            }
            lemmaEntities.add(lemmaEntity);

            float lemmaRank = calculateLemmaRank(lemma, groupedLemmas.get(0), groupedLemmas.get(1)); // 0 - title lemmas, 1 - body lemmas
            indexEntities.add(new IndexEntity(pageEntity, lemmaEntity, lemmaRank));
        }
        lemmaRepository.saveAll(lemmaEntities);
        indexRepository.saveAll(indexEntities);
    }

    private void fillInLemmaAndIndexTables(Site site) {
        String homePage = StringUtil.getStartPage(site.getUrl());
        int siteEntityId = siteRepository.findSiteEntityByUrl(homePage).getId();
        Map<String, LemmaEntity> lemmaEntityMap = lemmasMapGropedBySiteId.get(siteEntityId);
        lemmaRepository.saveAll(lemmaEntityMap.values());
        lemmasMapGropedBySiteId.get(siteEntityId).clear();
        indexRepository.saveAll(indexEntityMapGropedBySiteId.get(siteEntityId));
        indexEntityMapGropedBySiteId.get(siteEntityId).clear();
    }

    private void clearLemmaAndIndexCollections(Site site) {
        String homePage = StringUtil.getStartPage(site.getUrl());
        int siteEntityId = siteRepository.findSiteEntityByUrl(homePage).getId();
        lemmasMapGropedBySiteId.get(siteEntityId).clear();
        indexEntityMapGropedBySiteId.get(siteEntityId).clear();
    }

    private void reduceLemmaFrequenciesByOne(String text, int siteId) {
        Map<String, Integer> allUniquePageLemmas = getGroupedLemmas(text).get(2); // index 2 contains all lemmas
        log.info("Correcting lemmas frequencies: reduce by one");
        lemmaRepository.reduceByOneLemmaFrequencies(siteId, allUniquePageLemmas.keySet());
        lemmaRepository.deleteLemmasWithNoFrequencies(siteId);
    }

    private PageCrawlerUnit initCollectionsForSiteAndCreateMainPageCrawlerUnit(Site siteToHandle) {
        SiteEntity siteEntity = createAndPrepareSiteForIndexing(siteToHandle);
        siteStatusMap.put(siteEntity.getUrl(), Status.INDEXING);
        Map<String, LemmaEntity> stringLemmaEntityMap = new HashMap<>();
        lemmasMapGropedBySiteId.put(siteEntity.getId(), stringLemmaEntityMap);
        Set<IndexEntity> indexEntitySet = new HashSet<>();
        indexEntityMapGropedBySiteId.put(siteEntity.getId(), indexEntitySet);
        String siteHomePage = siteEntity.getUrl();
        webpagesPathSet.add(siteHomePage);
        return new PageCrawlerUnit(this, siteEntity, siteHomePage);
    }

    private void markIndexingCompletionIfApplicable() {
        List<SiteEntity> allSites = siteRepository.findAll();
        for (SiteEntity site : allSites) {
            if (site.getStatus().equals(Status.INDEXING)) {
                return;
            }
        }
        isIndexing = false;
    }

    private void savePageAndSite(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity) {
        pageEntity.setContent(pageHtml);
        pageRepository.save(pageEntity);
        siteEntity.setStatusTime(new Date());
        siteRepository.save(siteEntity);
    }

    private PageEntity deleteOldPageEntity(String path, SiteEntity siteEntity) {
        PageEntity pageEntityToDelete = pageRepository.findPageEntityByPathAndSite(path, siteEntity);
        if (pageEntityToDelete == null) {
            return null;
        }
        pageRepository.delete(pageEntityToDelete);
        return pageEntityToDelete;
    }

    private void fixSiteStatusAfterSinglePageIndexed(SiteEntity site) {
        site.setStatus(Status.INDEXED);
        siteRepository.save(site);
    }

    private SiteEntity createAndPrepareSiteForIndexing(Site site) {
        String homePage = StringUtil.getStartPage(site.getUrl());
        SiteEntity oldSiteEntity = siteRepository.findSiteEntityByUrl(homePage);
        if (oldSiteEntity != null) {
            oldSiteEntity.setStatus(Status.INDEXING);
            oldSiteEntity.setStatusTime(new Date());
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

    private void markSiteAsIndexed(Site site) {
        String homePage = StringUtil.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(new Date());
        siteEntity.setStatus(Status.INDEXED);
        siteRepository.save(siteEntity);
    }

    private void fixSiteIndexingError(Site site, Exception e) {
        String error = getErrorMessage(e);
        String homePage = StringUtil.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(new Date());
        siteEntity.setStatus(Status.FAILED);
        siteEntity.setLastError(error);
        siteRepository.save(siteEntity);
    }

    private String getErrorMessage(Exception e) {
        log.info("Creating error message for: '{}'", e.toString());
        if (e instanceof CancellationException || e instanceof InterruptedException) {
            return properties.getInterruptedByUserMessage();
        } else if (e instanceof CertificateExpiredException || e instanceof SSLHandshakeException
                || e instanceof CertPathValidatorException) {
            return properties.getCertificateError();
        } else {
            return properties.getUnknownError() + " (" + e + ")";
        }
    }
}
