package searchengine.services.interfaces;

import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.Collection;

public interface LemmaAndIndexService {

    LemmaEntity findLemmaEntityByLemmaAndSiteId(String lemma, SiteEntity siteEntity);

    void saveLemma(LemmaEntity lemmaEntity);

    void saveLemmaCollection(Collection<LemmaEntity> lemmaEntityCollection);

    void saveIndexCollection(Collection<IndexEntity> indexEntityCollection);

    void correctSingleLemmaFrequency(LemmaEntity lemmaEntity);
}
