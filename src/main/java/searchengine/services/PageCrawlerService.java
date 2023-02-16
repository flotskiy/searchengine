package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jsoup.nodes.Document;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.*;
import searchengine.util.JsoupUtil;
import searchengine.util.PropertiesHolder;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Getter
public class PageCrawlerService {

    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmatizerService lemmatizerService;
    private final PropertiesHolder properties;

    @Setter
    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    @Setter
    private Set<String> webpagesPathSet;
    @Setter
    private Map<Integer, Map<String, LemmaEntity>> lemmasMap;
    @Setter
    private Map<Integer, Set<IndexEntity>> indexEntityMap;

    @Bean
    @Scope("prototype")
    public PageCrawlerUnit createPageCrawler() {
        return new PageCrawlerUnit(this, properties);
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

    public void handleLemmasAndIndexOnSinglePage(String html, PageEntity page, SiteEntity site) {
        List<Map<String, Integer>> mapList = getUniqueLemmasListOfMaps(html);
        Set<IndexEntity> indexEntities = new HashSet<>();
        for (String lemma : mapList.get(2).keySet()) {
            LemmaEntity lemmaEntity = lemmaRepository.findTopByLemmaAndSiteId(lemma, site);
            if (lemmaEntity == null) {
                lemmaEntity =  new LemmaEntity();
                lemmaEntity.setLemma(lemma);
                lemmaEntity.setFrequency(1);
                lemmaEntity.setSiteId(site);
            } else {
                int currentFrequency = lemmaEntity.getFrequency();
                lemmaEntity.setFrequency(currentFrequency + 1);
            }
            lemmaRepository.save(lemmaEntity);

            float lemmaRank = calculateLemmaRank(lemma, mapList.get(0), mapList.get(1));
            IndexEntity indexEntity = new IndexEntity();
            indexEntity.setPageId(page);
            indexEntity.setLemmaId(lemmaEntity);
            indexEntity.setLemmaRank(lemmaRank);
            indexEntities.add(indexEntity);
        }
        indexRepository.saveAll(indexEntities);
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

    private float calculateLemmaRank(
            String lemma, Map<String, Integer> titleLemmasCount, Map<String, Integer> bodyLemmasCount
    ) {
        return titleLemmasCount.getOrDefault(lemma, 0) * properties.getWeightTitle() +
                bodyLemmasCount.getOrDefault(lemma, 0) * properties.getWeightBody();
    }

    public PageEntity createPageEntity(String path, int code, SiteEntity siteEntity) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(path);
        pageEntity.setCode(code);
        pageEntity.setSiteEntity(siteEntity);
        return pageEntity;
    }

    public void savePageEntityAndSiteStatus(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity) {
        if (!forkJoinPool.isTerminating() && !forkJoinPool.isTerminated()) {
            pageEntity.setContent(pageHtml);
            pageRepository.save(pageEntity);
            siteEntity.setStatusTime(new Date());
            siteRepository.save(siteEntity);
        }
    }
}