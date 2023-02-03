package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
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
}
