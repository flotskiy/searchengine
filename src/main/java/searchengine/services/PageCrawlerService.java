package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.*;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Getter
public class PageCrawlerService {

    @Value("${connect.useragent}")
    private String useragent;
    @Value("${connect.referrer}")
    private String referrer;
    @Value("${file.extensions}")
    private String fileExtensions;
    @Value("${selector.weight.title}")
    private float weightTitle;
    @Value("${selector.weight.body}")
    private float weightBody;

    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmatizerService lemmatizerService;
    private final StringService stringService;

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
        return new PageCrawlerUnit(this);
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
        Document htmlDocument = Jsoup.parse(html);
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
            String lemma,
            Map<String, Integer> titleLemmasCount,
            Map<String, Integer> bodyLemmasCount
    ) {
        return titleLemmasCount.getOrDefault(lemma, 0) * getWeightTitle() +
                bodyLemmasCount.getOrDefault(lemma, 0) * getWeightBody();
    }

    public PageEntity createPageEntity(String path, int code, SiteEntity siteEntity) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(path);
        pageEntity.setCode(code);
        pageEntity.setSiteEntity(siteEntity);
        return pageEntity;
    }

    public Connection getConnection(String pagePath) {
        return Jsoup.connect(pagePath)
                .userAgent(useragent)
                .referrer(referrer)
                .ignoreHttpErrors(true);
    }

    public boolean isHrefValid(String homePage, String href) {
        return href.startsWith(homePage)
                && isHrefToPage(href)
                && !isPageAdded(href)
                && !href.equals(homePage)
                && !href.equals(homePage + "/");
    }

    private boolean isHrefToPage(String href) {
        if (href.matches(".*(#|\\?).*")) {
            return false;
        }
        return !href.matches(".*\\.(" + fileExtensions + ")/?");
    }

    private boolean isPageAdded(String pagePath) {
        pagePath += pagePath.endsWith("/") ? "" : "/";
        return webpagesPathSet.contains(pagePath);
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
