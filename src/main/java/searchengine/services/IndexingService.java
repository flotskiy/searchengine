package searchengine.services;

public interface IndexingService {

    void indexAll();

    void stopIndexing();

    boolean isIndexingNow();

    boolean isPageBelongsToSiteSpecified(String url);

    void indexSinglePage(String url);
}
