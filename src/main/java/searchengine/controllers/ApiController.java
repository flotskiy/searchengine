package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchResultResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private static final String RESULT_KEY = "result";
    private static final String ERROR_KEY = "error";

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        Map<String, Object> response = new HashMap<>();
        if (indexingService.isIndexingNow()) {
            response.put(RESULT_KEY, false);
            response.put(ERROR_KEY, "Indexing already started");
            return ResponseEntity.badRequest().body(response);
        } else {
            new Thread(indexingService::indexAll).start();
            response.put(RESULT_KEY, true);
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        Map<String, Object> result = new HashMap<>();
        if (indexingService.isIndexingNow()) {
            indexingService.stopIndexing();
            result.put(RESULT_KEY, true);
            return ResponseEntity.ok(result);
        } else {
            result.put(RESULT_KEY, false);
            result.put(ERROR_KEY, "Indexing is not started");
            return ResponseEntity.badRequest().body(result);
        }
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestParam(name="url", required = false) String path) {
        Map<String, Object> response = new HashMap<>();
        if (indexingService.isPageBelongsToSiteSpecified(path)) {
            new Thread(() -> indexingService.indexSinglePage(path)).start();
            response.put(RESULT_KEY, true);
            return ResponseEntity.ok(response);
        }
        response.put(RESULT_KEY, false);
        response.put(ERROR_KEY, "Page is located outside the sites specified in the configuration file");
        return ResponseEntity.badRequest().body(response);
    }

    @GetMapping("/search")
    public ResponseEntity<Object> startIndexing(
            @RequestParam(name="query", required = false) String query,
            @RequestParam(name="site", required = false) String site,
            @RequestParam(name="offset", required = false) Integer offset,
            @RequestParam(name="limit", required = false) Integer limit
    ) {
        HashMap<String, Object> result = new HashMap<>();
        result.put(RESULT_KEY, false);
        if (!searchService.isQueryExists(query)) {
            result.put(ERROR_KEY, "Empty search query");
            return ResponseEntity.badRequest().body(result);
        } else if (searchService.isIndexingOrFailed(site)) {
            result.put(ERROR_KEY, "Indexing not finished yet successfully");
            return ResponseEntity.status(403).body(result);
        }
        SearchResultResponse searchResult = searchService.getSearchResultPageList(query, site, offset, limit);
        return ResponseEntity.ok(searchResult);
    }
}
