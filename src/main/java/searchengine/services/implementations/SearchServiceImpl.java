package searchengine.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import searchengine.dto.search.LemmaDto;
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
import searchengine.services.interfaces.LemmatizerService;
import searchengine.services.interfaces.SearchService;
import searchengine.util.PropertiesHolder;
import searchengine.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;

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
    public ResponseEntity<SearchResultResponse> search(String query, String site, int offset, int limit) {
        SearchResultResponse searchResult = new SearchResultResponse();
        searchResult.setResult(false);
        if (!isQueryExists(query)) {
            searchResult.setError("Empty search query");
        } else if (isIndexingOrFailed(site)) {
            searchResult.setError("Indexing not finished yet successfully");
        } else {
            searchResult = getSearchResultPageList(query, site, offset, limit);
        }
        return ResponseEntity.ok(searchResult);
    }

    private boolean isQueryExists(String query) {
        return StringUtil.isStringExists(query);
    }

    private boolean isIndexingOrFailed(String siteName) {
        siteName = siteName + "/";
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(siteName);
        if (siteEntity != null) {
            return !siteEntity.getStatus().equals(Status.INDEXED);
        }
        return siteRepository.existsByStatus(Status.INDEXING) || siteRepository.existsByStatus(Status.FAILED);
    }

    private SearchResultResponse getSearchResultPageList(String query, String site, int offset, int limit) {
        site = site + "/";
        SearchResultResponse searchResult = getSearchResultPageList(query, site);

        int noOfPagesResult = searchResult.getCount();
        int dataArrayEndIndex = Math.min(noOfPagesResult, offset + limit);
        int dataValueSize;
        if (noOfPagesResult < offset) {
            dataValueSize = 0;
        } else {
            dataValueSize = dataArrayEndIndex - offset;
        }
        List<SearchResultPage> allPages = searchResult.getData();
        List<SearchResultPage> limitedPages = new ArrayList<>();
        for (int i = offset; i < offset + dataValueSize; i++) {
            limitedPages.add(allPages.get(i));
        }
        searchResult.setData(limitedPages);
        return searchResult;
    }

    private SearchResultResponse getSearchResultPageList(String query, String siteUrl) {
        List<SiteEntity> siteEntityList = siteRepository.findAll();
        SiteEntity searchingSite = getSearchingSiteEntity(siteEntityList, siteUrl);
        List<LemmaEntity> sortedLemmasFromQuery = getSortedByFrequencyAscLemmasQueryList(query, searchingSite);
        List<LemmaEntity> frequentLemmas = getFrequentLemmas(siteEntityList, sortedLemmasFromQuery);

        SearchResultResponse searchResultResponse = createSearchResultResponse(frequentLemmas);
        if (sortedLemmasFromQuery.isEmpty() || sortedLemmasFromQuery.size() == frequentLemmas.size()) {
            return returnEmptySearchResult(searchResultResponse);
        }

        Set<Integer> pagesIdSet = getAllPagesId(sortedLemmasFromQuery);
        List<PageEntity> pages = pageRepository.findAllById(pagesIdSet);
        if (searchingSite == null) {
            pages = filterPages(pages, sortedLemmasFromQuery, frequentLemmas);
        }
        if (pages.isEmpty()) {
            return returnEmptySearchResult(searchResultResponse);
        }

        List<SearchResultPage> pagesFoundList = getSortedSearchResultPageList(pages, sortedLemmasFromQuery);
        return putPagesIntoSearchResultResponse(searchResultResponse, pagesFoundList);
    }

    private SearchResultResponse createSearchResultResponse(List<LemmaEntity> frequentLemmas) {
        SearchResultResponse searchResultResponse = new SearchResultResponse();
        searchResultResponse.setResult(true);
        List<LemmaDto> frequentLemmaDtoList = convertLemmaEntityListToLemmaDtoList(frequentLemmas);
        searchResultResponse.setFrequentLemmas(frequentLemmaDtoList);
        return searchResultResponse;
    }

    private SearchResultResponse putPagesIntoSearchResultResponse(
            SearchResultResponse response, List<SearchResultPage> pageList
    ) {
        response.setCount(pageList.size());
        response.setData(pageList);
        return response;
    }

    private List<PageEntity> filterPages(
            List<PageEntity> pages, List<LemmaEntity> allLemmas, List<LemmaEntity> frequentLemmas
    ) {
        List<PageEntity> filteredPages = pages;
        Map<SiteEntity, List<LemmaEntity>> groupedLemmas =
                allLemmas.stream().collect(Collectors.groupingBy(LemmaEntity::getSiteId));
        Map<SiteEntity, List<LemmaEntity>> groupedFrequentLemmas =
                frequentLemmas.stream().collect(Collectors.groupingBy(LemmaEntity::getSiteId));
        for (SiteEntity site : groupedLemmas.keySet()) {
            if ((groupedLemmas.containsKey(site) && groupedFrequentLemmas.containsKey(site))
                    && groupedLemmas.get(site).size() == groupedFrequentLemmas.get(site).size()) {
                filteredPages = filteredPages.stream()
                        .filter(pageEntity -> !pageEntity.getSiteEntity().equals(site)).toList();
            }
        }
        return filteredPages;
    }

    private SearchResultResponse returnEmptySearchResult(SearchResultResponse searchResult) {
        log.info("Nothing found!");
        searchResult.setCount(0);
        return searchResult;
    }

    private SiteEntity getSearchingSiteEntity(List<SiteEntity> sites, String url) {
        for (SiteEntity siteEntity : sites) {
            if (siteEntity.getUrl().equals(url)) {
                return siteEntity;
            }
        }
        return null;
    }

    private List<SearchResultPage> getSortedSearchResultPageList(List<PageEntity> pages,  List<LemmaEntity> lemmaList) {
        List<SearchResultPage> searchResultPageList = new ArrayList<>();
        List<Integer> lemmasIdList = lemmaList.stream().map(LemmaEntity::getId).toList();
        Set<String> lemmasStringSet = lemmaList.stream().map(LemmaEntity::getLemma).collect(Collectors.toSet());

        for (PageEntity pageEntity : pages) {
            SearchResultPage searchResultPage = createSearchResultPage(pageEntity, lemmasIdList, lemmasStringSet);
            searchResultPageList.add(searchResultPage);
        }
        searchResultPageList.sort(Comparator.comparing(SearchResultPage::getRelevance).reversed()
                .thenComparing(SearchResultPage::getTitle));
        convertAbsoluteRelevanceToRelative(searchResultPageList);
        return searchResultPageList;
    }

    private SearchResultPage createSearchResultPage(
            PageEntity pageEntity, List<Integer> lemmasIdList, Set<String> lemmasString
    ) {
        SiteEntity tempSite = pageEntity.getSiteEntity();
        String siteUrl = StringUtil.cutSlash(tempSite.getUrl());
        String siteName = tempSite.getName();
        String pagePath = pageEntity.getPath();
        Document document = Jsoup.parse(pageEntity.getContent());
        String title = document.title();

        String snippet = getSnippet(document, lemmasString);
        Float relevanceWrapped = indexRepository.getAbsRelevance(pageEntity.getId(), lemmasIdList);
        float relevance = relevanceWrapped == null ? 0 : relevanceWrapped;

        SearchResultPage searchResultPage = new SearchResultPage();
        searchResultPage.setSite(siteUrl);
        searchResultPage.setSiteName(siteName);
        searchResultPage.setUri(pagePath);
        searchResultPage.setTitle(title);
        searchResultPage.setSnippet(snippet);
        searchResultPage.setRelevance(relevance);
        return searchResultPage;
    }

    private Set<Integer> getAllPagesId(List<LemmaEntity> lemmasQueryList) {
        Map<String, Integer> frequencyLemmaMap = lemmasQueryList.stream()
                .collect(Collectors.groupingBy(LemmaEntity::getLemma, Collectors.summingInt(LemmaEntity::getFrequency)));

        List<String> lemmasSortedAscList =
                frequencyLemmaMap.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList();

        Map<String, Set<Integer>> gropedLemmaIds = lemmasQueryList.stream().collect(
                Collectors.groupingBy(LemmaEntity::getLemma, Collectors.mapping(LemmaEntity::getId, Collectors.toSet()))
        );

        return findPagesId(lemmasSortedAscList, gropedLemmaIds);
    }

    private Set<Integer> findPagesId(List<String> lemmasSorted, Map<String, Set<Integer>> gropedLemmaIdMap) {
        String firstLemma = lemmasSorted.get(0);
        Set<Integer> firstLemmaIdsSet = gropedLemmaIdMap.get(firstLemma);
        Set<Integer> pagesIdResultSet = indexRepository.findPagesIdsByLemmaIdIn(firstLemmaIdsSet);

        String currentLemma;
        Set<Integer> currentLemmasIdSet;
        Set<Integer> pagesIdTempSet = new HashSet<>();
        for (int i = 1; i < lemmasSorted.size(); i++) {
            pagesIdTempSet.clear();
            currentLemma = lemmasSorted.get(i);
            currentLemmasIdSet = gropedLemmaIdMap.get(currentLemma);
            pagesIdTempSet = indexRepository.findPagesIdsByLemmaIdIn(currentLemmasIdSet);
            pagesIdResultSet.retainAll(pagesIdTempSet);
            if (pagesIdResultSet.isEmpty()) {
                return Collections.emptySet();
            }
        }
        return pagesIdResultSet;
    }

    private List<LemmaEntity> getSortedByFrequencyAscLemmasQueryList(String query, SiteEntity siteEntity) {
        Set<String> queryWordsSet = lemmatizerService.getLemmasCountMap(query).keySet();
        List<LemmaEntity> lemmaEntityList;
        if (siteEntity == null) {
            lemmaEntityList = lemmaRepository.findLemmaEntitiesByLemmaIn(queryWordsSet);
        } else {
            lemmaEntityList = lemmaRepository.findLemmaEntitiesByLemmaInAndSiteId(queryWordsSet, siteEntity);
        }
        lemmaEntityList.sort((l1, l2) -> l1.getFrequency() < l2.getFrequency() ? -1 : 1);
        return lemmaEntityList;
    }

    private void convertAbsoluteRelevanceToRelative(List<SearchResultPage> searchResultPageList) {
        float maxRelevanceValue = searchResultPageList.get(0).getRelevance();
        for (SearchResultPage result : searchResultPageList) {
            result.setRelevance(result.getRelevance() / maxRelevanceValue);
        }
    }

    private String getSnippet(Document document, Set<String> querySet) {
        String documentText = document.text();
        List<String> textList = new ArrayList<>(Arrays.asList(documentText.split("\\s+")));
        List<String> textListLemmatized = lemmatizerService.getLemmatizedList(textList);

        Map<Integer, String> textMapLemmatized =
                textListLemmatized.stream().collect(HashMap::new, (map, s) -> map.put(map.size(), s), Map::putAll);
        Map<Integer, String> filteredMap = textMapLemmatized.entrySet().stream()
                .filter(e -> {
                    for (String queryWord : querySet) {
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

    private List<LemmaEntity> getFrequentLemmas(List<SiteEntity> siteEntityList, List<LemmaEntity> lemmaList) {
        Map<Integer, Float> frequentWordsBorderMap = get95perCentFrequencyLimitForEachSite(siteEntityList);
        List<LemmaEntity> removedLemmas = new ArrayList<>();
        for (LemmaEntity lemma : new ArrayList<>(lemmaList)) {
            int siteId = lemma.getSiteId().getId();
            if (lemma.getFrequency() > frequentWordsBorderMap.get(siteId)) {
                removedLemmas.add(lemma);
            }
        }
        return removedLemmas;
    }

    private Map<Integer, Float> get95perCentFrequencyLimitForEachSite(List<SiteEntity> siteList) {
        Map<Integer, Float> frequencyMap = new HashMap<>();
        for (SiteEntity site : siteList) {
            int id = site.getId();
            float occurrenceOf95perCentLimit = pageRepository.get95perCentPagesLimit(id);
            frequencyMap.put(id, occurrenceOf95perCentLimit);
        }
        return frequencyMap;
    }

    private List<LemmaDto> convertLemmaEntityListToLemmaDtoList(List<LemmaEntity> lemmaEntityList) {
        List<LemmaDto> result = new ArrayList<>();
        for (LemmaEntity lemmaEntity : lemmaEntityList) {
            LemmaDto lemmaDto = new LemmaDto();
            lemmaDto.setLemma(lemmaEntity.getLemma());
            lemmaDto.setSite(lemmaEntity.getSiteId().getName());
            result.add(lemmaDto);
        }
        return result;
    }
}
