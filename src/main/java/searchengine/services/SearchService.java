package searchengine.services;

import searchengine.dto.search.SearchResultResponse;

public interface SearchService {

    boolean isQueryExists(String query);

    SearchResultResponse getSearchResultPageList(String query, String url, int offset, int limit);

    boolean isIndexingOrFailed(String url);
}
