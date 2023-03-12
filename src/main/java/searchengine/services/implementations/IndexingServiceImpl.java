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
import searchengine.model.*;
import searchengine.services.interfaces.LemmatizerService;
import searchengine.services.PageCrawlerUnit;
import searchengine.services.interfaces.IndexingService;
import searchengine.services.interfaces.RepoAccessService;
import searchengine.util.JsoupUtil;
import searchengine.util.PropertiesHolder;
import searchengine.util.StringUtil;

import java.util.*;
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
    private final RepoAccessService repoAccessService;
    private final LemmatizerService lemmatizerService;
    @Getter
    private final PropertiesHolder properties;

    private volatile boolean isIndexing = false;
    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    @Getter
    private Set<String> webpagesPathSet;
    private Map<Integer, Map<String, LemmaEntity>> lemmasMap;
    private Map<Integer, Set<IndexEntity>> indexEntityMap;
    @Getter
    private ConcurrentMap<String, Status> statusMap;

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
        if (isPageBelongsToSiteSpecified(path)) {
            new Thread(() -> indexSinglePage(path)).start();
            apiResponse.setResult(true);
        } else {
            apiResponse.setResult(false);
            apiResponse.setError("Page is located outside the sites specified in the configuration file");
        }
        return ResponseEntity.ok(apiResponse);
    }

    public float calculateLemmaRank(
            String lemma, Map<String, Integer> titleLemmasCount, Map<String, Integer> bodyLemmasCount
    ) {
        return titleLemmasCount.getOrDefault(lemma, 0) * properties.getWeightTitle() +
                bodyLemmasCount.getOrDefault(lemma, 0) * properties.getWeightBody();
    }

    public PageEntity createPageEntity(String path, int code, SiteEntity siteEntity) {
        return repoAccessService.createPageEntity(path, code, siteEntity);
    }

    public void savePageContentAndSiteStatusTime(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity) {
        if (!forkJoinPool.isTerminating()
                && !forkJoinPool.isTerminated()
                && !statusMap.get(siteEntity.getUrl()).equals(Status.FAILED)) {
            repoAccessService.savePageContentAndSiteStatusTime(pageEntity, pageHtml, siteEntity);
        }
    }

    public void saveSinglePageContentAndSiteStatusTime(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity) {
        repoAccessService.savePageContentAndSiteStatusTime(pageEntity, pageHtml, siteEntity);
    }

    public void handleLemmasAndIndex(String html, PageEntity page, SiteEntity site) {
        List<Map<String, Integer>> mapList = getUniqueLemmasListOfMaps(html);
        for (String lemma : mapList.get(2).keySet()) {
            Map<String, LemmaEntity> stringLemmaEntityMap = lemmasMap.get(site.getId());
            LemmaEntity lemmaEntity = stringLemmaEntityMap.get(lemma);
            if (lemmaEntity == null) {
                lemmaEntity = new LemmaEntity();
                lemmaEntity.setLemma(lemma);
                lemmaEntity.setFrequency(1);
                lemmaEntity.setSiteId(site);
                lemmasMap.get(site.getId()).put(lemma, lemmaEntity);
            } else {
                lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
            }

            float lemmaRank = calculateLemmaRank(lemma, mapList.get(0), mapList.get(1));
            IndexEntity indexEntity = new IndexEntity();
            indexEntity.setPageId(page);
            indexEntity.setLemmaId(lemmaEntity);
            indexEntity.setLemmaRank(lemmaRank);
            indexEntityMap.get(site.getId()).add(indexEntity);
        }
    }

    public List<Map<String, Integer>> getUniqueLemmasListOfMaps(String html) {
        Document htmlDocument = JsoupUtil.parse(html);
        String title = htmlDocument.title();
        String bodyText = htmlDocument.body().text();

        Map<String, Integer> titleLemmasCount = lemmatizerService.getLemmasCountMap(title); // 0 list element
        Map<String, Integer> bodyLemmasCount = lemmatizerService.getLemmasCountMap(bodyText); // 1 list element
        Map<String, Integer> uniqueLemmasInTitleAndBody = Stream // 2 list element
                .concat(titleLemmasCount.entrySet().stream(), bodyLemmasCount.entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingInt(Map.Entry::getValue)));

        List<Map<String, Integer>> mapList = new ArrayList<>();
        mapList.add(titleLemmasCount);
        mapList.add(bodyLemmasCount);
        mapList.add(uniqueLemmasInTitleAndBody);
        return mapList;
    }

    public void indexSinglePage(String pageUrl) {
        SiteEntity siteEntity = findOrCreateNewSiteEntity(pageUrl);
        Connection connection = JsoupUtil.getConnection(pageUrl, properties.getUseragent(), properties.getReferrer());
        Connection.Response response = JsoupUtil.getResponse(connection);
        Document document = JsoupUtil.getDocument(connection);
        String pathToSave = StringUtil.getPathToSave(pageUrl, siteEntity.getUrl());
        int httpStatusCode = response.statusCode();

        PageEntity pageEntityDeleted = repoAccessService.deleteOldPageEntity(pathToSave, siteEntity);
        PageEntity pageEntity = createPageEntity(pathToSave, httpStatusCode, siteEntity);
        String html = "";
        if (httpStatusCode != 200) {
            saveSinglePageContentAndSiteStatusTime(pageEntity, html, siteEntity);
        } else {
            html = document.outerHtml();
            if (pageEntityDeleted != null) {
                correctAllPageLemmaFrequency(html, siteEntity.getId());
            }
            saveSinglePageContentAndSiteStatusTime(pageEntity, html, siteEntity);
            handleLemmasAndIndexOnSinglePage(html, pageEntity, siteEntity);
        }
        repoAccessService.fixSiteStatusAfterSinglePageIndexed(siteEntity);
    }

    private SiteEntity createSiteToHandleSinglePage(String siteHomePageToSave) {
        SiteEntity siteEntity = null;
        String currentSiteHomePage;
        for (Site site : sites.getSites()) {
            currentSiteHomePage = StringUtil.getStartPage(site.getUrl());
            if (siteHomePageToSave.equalsIgnoreCase(currentSiteHomePage)) {
                siteEntity = repoAccessService.prepareSiteIndexing(site);
                break;
            }
        }
        return siteEntity;
    }

    private void indexAll() {
        isIndexing = true;
        forkJoinPool = new ForkJoinPool();
        lemmasMap = new HashMap<>();
        indexEntityMap = new HashMap<>();
        webpagesPathSet = Collections.synchronizedSet(new HashSet<>());
        statusMap = new ConcurrentHashMap<>();
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
            PageCrawlerUnit pageCrawlerUnit = handleSite(site);
            forkJoinPool.invoke(pageCrawlerUnit);
            fillInLemmasAndIndexTables(site);
            repoAccessService.markSiteAsIndexed(site);
            log.info("Indexing SUCCESSFULLY completed for site '{}'", site.getName());
        } catch (Exception exception) {
            log.warn("FAILED to complete indexing '{}' due to '{}'", site.getName(), exception);
            repoAccessService.fixSiteIndexingError(site, exception);
            clearLemmasAndIndexCollections(site);
        } finally {
            checkForCompletion();
        }
    }

    private SiteEntity findOrCreateNewSiteEntity(String url) {
        String siteUrlFromPageUrl = StringUtil.getStartPage(url);
        SiteEntity siteEntity = repoAccessService.findSiteEntityByUrl(siteUrlFromPageUrl);
        if (siteEntity == null) {
            siteEntity = createSiteToHandleSinglePage(siteUrlFromPageUrl);
        }
        return siteEntity;
    }

    private void handleLemmasAndIndexOnSinglePage(String html, PageEntity pageEntity, SiteEntity siteEntity) {
        List<Map<String, Integer>> lemmasMapList = getUniqueLemmasListOfMaps(html);
        Set<String> allUniquePageLemmas = lemmasMapList.get(2).keySet();
        List<LemmaEntity> singlePageLemmaEntityList =
                repoAccessService.findLemmaEntitiesByLemmaInAndSiteId(allUniquePageLemmas, siteEntity);
        Map<String, LemmaEntity> lemmaEntityMap =
                singlePageLemmaEntityList.stream().collect(Collectors.toMap(LemmaEntity::getLemma, Function.identity()));

        Set<LemmaEntity> lemmaEntities = new HashSet<>();
        Set<IndexEntity> indexEntities = new HashSet<>();
        for (String lemma : allUniquePageLemmas) {
            LemmaEntity lemmaEntity = lemmaEntityMap.get(lemma);
            if (lemmaEntity == null) {
                lemmaEntity = createNewLemmaEntity(lemma, siteEntity);
            } else {
                int currentFrequency = lemmaEntity.getFrequency();
                lemmaEntity.setFrequency(currentFrequency + 1);
            }
            lemmaEntities.add(lemmaEntity);

            float lemmaRank = calculateLemmaRank(lemma, lemmasMapList.get(0), lemmasMapList.get(1));
            indexEntities.add(createNewIndexEntity(pageEntity, lemmaEntity, lemmaRank));
        }
        repoAccessService.saveLemmaCollection(lemmaEntities);
        repoAccessService.saveIndexCollection(indexEntities);
    }

    private LemmaEntity createNewLemmaEntity(String lemma, SiteEntity siteEntity) {
        LemmaEntity lemmaEntity = new LemmaEntity();
        lemmaEntity.setLemma(lemma);
        lemmaEntity.setFrequency(1);
        lemmaEntity.setSiteId(siteEntity);
        return lemmaEntity;
    }

    private IndexEntity createNewIndexEntity(PageEntity pageEntity, LemmaEntity lemmaEntity, float lemmaRank) {
        IndexEntity indexEntity = new IndexEntity();
        indexEntity.setPageId(pageEntity);
        indexEntity.setLemmaId(lemmaEntity);
        indexEntity.setLemmaRank(lemmaRank);
        return indexEntity;
    }

    private void fillInLemmasAndIndexTables(Site site) {
        String homePage = StringUtil.getStartPage(site.getUrl());
        int siteEntityId = repoAccessService.findSiteEntityByUrl(homePage).getId();
        Map<String, LemmaEntity> lemmaEntityMap = lemmasMap.get(siteEntityId);
        repoAccessService.saveLemmaCollection(lemmaEntityMap.values());
        lemmasMap.get(siteEntityId).clear();
        repoAccessService.saveIndexCollection(indexEntityMap.get(siteEntityId));
        indexEntityMap.get(siteEntityId).clear();
    }

    private void clearLemmasAndIndexCollections(Site site) {
        String homePage = StringUtil.getStartPage(site.getUrl());
        int siteEntityId = repoAccessService.findSiteEntityByUrl(homePage).getId();
        lemmasMap.get(siteEntityId).clear();
        indexEntityMap.get(siteEntityId).clear();
    }

    private void correctAllPageLemmaFrequency(String text, int siteId) {
        Map<String, Integer> allUniquePageLemmas = getUniqueLemmasListOfMaps(text).get(2);
        log.info("Correcting lemmas frequencies: reduce by one");
        repoAccessService.reduceByOneLemmaFrequencies(allUniquePageLemmas.keySet(), siteId);
        repoAccessService.deleteLemmasWithLowFrequencies(siteId);
    }

    private PageCrawlerUnit handleSite(Site siteToHandle) {
        SiteEntity siteEntity = repoAccessService.prepareSiteIndexing(siteToHandle);
        statusMap.put(siteEntity.getUrl(), Status.INDEXING);
        Map<String, LemmaEntity> stringLemmaEntityMap = new HashMap<>();
        lemmasMap.put(siteEntity.getId(), stringLemmaEntityMap);
        Set<IndexEntity> indexEntitySet = new HashSet<>();
        indexEntityMap.put(siteEntity.getId(), indexEntitySet);
        String siteHomePage = siteEntity.getUrl();
        webpagesPathSet.add(siteHomePage);
        return new PageCrawlerUnit(this, siteEntity, siteHomePage);
    }

    private void checkForCompletion() {
        List<SiteEntity> allSites = repoAccessService.getAllSites();
        for (SiteEntity site : allSites) {
            if (site.getStatus().equals(Status.INDEXING)) {
                return;
            }
        }
        isIndexing = false;
    }
}
