package searchengine.services.interfaces;

import org.springframework.http.ResponseEntity;
import searchengine.dto.ApiResponse;

public interface IndexingService {

    ResponseEntity<ApiResponse> startIndexing();

    ResponseEntity<ApiResponse> stopIndexing();

    ResponseEntity<ApiResponse> indexPage(String pagePath);
}
