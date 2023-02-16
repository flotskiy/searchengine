package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import searchengine.dto.search.SearchResultPage;
import searchengine.dto.search.SearchResultResponse;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.util.PropertiesHolder;
import searchengine.util.StringUtil;

import javax.persistence.EntityNotFoundException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Log4j2
public class SearchServiceImpl implements SearchService {

    private final LemmatizerService lemmatizerService;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PropertiesHolder properties;

    @Override
    public boolean isQueryExists(String query) {
        return StringUtil.isStringExists(query);
    }

    @Override
    public SearchResultResponse getSearchResultPageList(String query, String site, int offset, int limit) {
        site = site + "/";
        List<SearchResultPage> searchResultPageList = getSearchResultPageList(query, site);

        SearchResultResponse searchResult = new SearchResultResponse();
        int noOfPagesResult = searchResultPageList.size();
        searchResult.setCount(noOfPagesResult);

        int dataArrayEndIndex = Math.min(noOfPagesResult, offset + limit);
        int dataValueSize;
        if (noOfPagesResult < offset) {
            dataValueSize = 0;
        } else {
            dataValueSize = dataArrayEndIndex - offset;
        }
        List<SearchResultPage> dataValue = new ArrayList<>();
        for (int i = offset; i < offset + dataValueSize; i++) {
            dataValue.add(searchResultPageList.get(i));
        }
        searchResult.setData(dataValue);
        searchResult.setResult(true);
        return searchResult;
    }

    @Override
    public boolean isIndexingOrFailed(String siteName) {
        siteName = siteName + "/";
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(siteName);
        if (siteEntity != null) {
            return !siteEntity.getStatus().equals(Status.INDEXED);
        }
        return siteRepository.existsByStatus(Status.INDEXING) || siteRepository.existsByStatus(Status.FAILED);
    }

    private List<SearchResultPage> getSearchResultPageList(String query, String siteUrl) {
        List<SiteEntity> siteEntityList = siteRepository.findAll();
        List<LemmaEntity> lemmasQueryListSorted = getSortedLemmasQueryList(query);
        Set<Integer> removedSiteIdSet = cleanLemmasQueryListSortedFromFrequentLemmas(lemmasQueryListSorted, siteEntityList);
        if (lemmasQueryListSorted.isEmpty()) {
            return leaveSearchResultMethodAndReturnEmptyList();
        }
        int siteId = -1;
        for (SiteEntity siteEntity : siteEntityList) {
            if (siteEntity.getUrl().equals(siteUrl)) {
                siteId = siteEntity.getId();
            }
        }
        Set<PageEntity> pages = getPagesSet(lemmasQueryListSorted, siteId);
        pages.removeIf(page -> removedSiteIdSet.contains(page.getSiteEntity().getId()));
        if (pages.isEmpty()) {
            return leaveSearchResultMethodAndReturnEmptyList();
        }
        List<Integer> lemmasIdList = lemmasQueryListSorted.stream().map(LemmaEntity::getId).toList();
        List<String> lemmasStringList = lemmasQueryListSorted.stream().map(LemmaEntity::getLemma).toList();
        Map<List<Integer>, List<String>> map = new HashMap<>();
        map.put(lemmasIdList, lemmasStringList);
        List<SearchResultPage> searchResultPageList = new ArrayList<>();
        for (PageEntity pageEntity : pages) {
            addNewSearchResult(searchResultPageList, pageEntity, map);
        }
        searchResultPageList.sort(Comparator.comparing(SearchResultPage::getRelevance).reversed()
                .thenComparing(SearchResultPage::getTitle));
        convertAbsoluteRelevanceToRelative(searchResultPageList);
        return searchResultPageList;
    }

    private void addNewSearchResult(
            List<SearchResultPage> listToAdd, PageEntity pageEntity, Map<List<Integer>, List<String>> map
    ) {
        SiteEntity tempSite = pageEntity.getSiteEntity();
        String siteUrl = StringUtil.cutSlash(tempSite.getUrl());
        String siteName = tempSite.getName();
        String pagePath = pageEntity.getPath();
        Document document = Jsoup.parse(pageEntity.getContent());
        String title = document.title();

        Map.Entry<List<Integer>, List<String>> entry =
                map.entrySet().stream().findFirst().orElseThrow(EntityNotFoundException::new);
        List<Integer> lemmasIdList = entry.getKey();
        List<String> lemmasStringList = entry.getValue();
        String snippet = getSnippet(document, lemmasStringList);
        Float relevanceWrapped = indexRepository.getAbsRelevance(pageEntity.getId(), lemmasIdList);
        float relevance = relevanceWrapped == null ? 0 : relevanceWrapped;

        SearchResultPage searchResultPage = new SearchResultPage();
        searchResultPage.setSite(siteUrl);
        searchResultPage.setSiteName(siteName);
        searchResultPage.setUri(pagePath);
        searchResultPage.setTitle(title);
        searchResultPage.setSnippet(snippet);
        searchResultPage.setRelevance(relevance);
        listToAdd.add(searchResultPage);
    }

    private Set<PageEntity> getPagesSet(List<LemmaEntity> lemmasQueryList, int siteId) {
        String firstLemma = lemmasQueryList.get(0).getLemma();
        Set<PageEntity> pagesResultSet = new HashSet<>();
        Set<PageEntity> pagesTempSet = new HashSet<>();
        Iterable<PageEntity> pagesIterable = getPagesByLemmaAndSiteId(firstLemma, siteId);
        pagesIterable.forEach(pagesResultSet::add);

        for (int i = 1; i < lemmasQueryList.size(); i++) {
            pagesTempSet.clear();
            pagesIterable = getPagesByLemmaAndSiteId(lemmasQueryList.get(i).getLemma(), siteId);
            pagesIterable.forEach(pagesTempSet::add);
            pagesResultSet.retainAll(pagesTempSet);
            if (pagesResultSet.isEmpty()) {
                break;
            }
        }
        return pagesResultSet;
    }

    private Iterable<PageEntity> getPagesByLemmaAndSiteId(String lemma, int siteId) {
        if (siteId == -1) {
            return pageRepository.getPagesByLemma(lemma);
        }
        return pageRepository.getPagesByLemmaAndSiteId(lemma, siteId);
    }

    private List<LemmaEntity> getSortedLemmasQueryList(String query) {
        Set<String> queryWordsSet = lemmatizerService.getLemmasCountMap(query).keySet();
        List<LemmaEntity> lemmaEntityList = lemmaRepository.findLemmaEntitiesByLemmaIn(queryWordsSet);
        lemmaEntityList.sort((l1, l2) -> l1.getFrequency() < l2.getFrequency() ? -1 : 1);
        return lemmaEntityList;
    }

    private void convertAbsoluteRelevanceToRelative(List<SearchResultPage> searchResultPageList) {
        float maxRelevanceValue = searchResultPageList.get(0).getRelevance();
        for (SearchResultPage result : searchResultPageList) {
            result.setRelevance(result.getRelevance() / maxRelevanceValue);
        }
    }

    private String getSnippet(Document document, List<String> queryList) {
        String documentText = document.text();
        List<String> textList = new ArrayList<>(Arrays.asList(documentText.split("\\s+")));
        List<String> textListLemmatized = lemmatizerService.getLemmatizedList(textList);

        Map<Integer, String> textMapLemmatized =
                textListLemmatized.stream().collect(HashMap::new, (map, s) -> map.put(map.size(), s), Map::putAll);
        Map<Integer, String> filteredMap = textMapLemmatized.entrySet().stream()
                .filter(e -> {
                    for (String queryWord : queryList) {
                        if (queryWord.equals(e.getValue())) {
                            return true;
                        }
                    }
                    return false;
                }).collect(HashMap::new, (map, e) -> map.put(e.getKey(), e.getValue()), Map::putAll);
        List<Integer> lemmasPositions = new ArrayList<>(filteredMap.keySet());
        lemmasPositions.sort(Integer::compareTo);

        if (lemmasPositions.isEmpty()) {
            return "";
        }
        return StringUtil.buildSnippet(textList, lemmasPositions, properties.getSnippetBorder());
    }

    private List<SearchResultPage> leaveSearchResultMethodAndReturnEmptyList() {
        log.info("Nothing found!");
        return Collections.emptyList();
    }

    private Set<Integer> cleanLemmasQueryListSortedFromFrequentLemmas(List<LemmaEntity> lemmaList, List<SiteEntity> siteList) {
        Map<Integer, Float> siteIdAnd95perCentOfAllPages = new HashMap<>();
        for (SiteEntity site : siteList) {
            int id = site.getId();
            float occurrenceOf95perCentLimit = pageRepository.get95perCentPagesLimit(id);
            siteIdAnd95perCentOfAllPages.put(id, occurrenceOf95perCentLimit);
        }

        Set<Integer> removedSiteIdSet = new HashSet<>();
        for (LemmaEntity lemma : new ArrayList<>(lemmaList)) {
            int siteId = lemma.getSiteId().getId();
            if (lemma.getFrequency() > siteIdAnd95perCentOfAllPages.get(siteId)) {
                removedSiteIdSet.add(siteId);
                lemmaList.remove(lemma);
            }
        }
        return removedSiteIdSet;
    }
}
