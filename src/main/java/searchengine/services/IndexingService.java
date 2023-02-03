package searchengine.services;

public interface IndexingService {

    void indexAll();

    void stopIndexing();

    boolean isIndexingNow();
}
