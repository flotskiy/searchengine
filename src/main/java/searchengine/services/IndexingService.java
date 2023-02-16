package searchengine.services;

import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface IndexingService {

    ResponseEntity<Map<String, Object>> startIndexing();

    ResponseEntity<Map<String, Object>> stopIndexing();

    ResponseEntity<Map<String, Object>> indexPage(String pagePath);
}
