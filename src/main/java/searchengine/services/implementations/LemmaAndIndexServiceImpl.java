package searchengine.services.implementations;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.services.interfaces.LemmaAndIndexService;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class LemmaAndIndexServiceImpl implements LemmaAndIndexService {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    @Override
    public LemmaEntity findLemmaEntityByLemmaAndSiteId(String lemma, SiteEntity siteEntity) {
        return lemmaRepository.findTopByLemmaAndSiteId(lemma, siteEntity);
    }

    @Override
    public void saveLemma(LemmaEntity lemmaEntity) {
        lemmaRepository.save(lemmaEntity);
    }

    @Override
    public void saveLemmaCollection(Collection<LemmaEntity> lemmaEntityCollection) {
        lemmaRepository.saveAll(lemmaEntityCollection);
    }

    @Override
    public void saveIndexCollection(Collection<IndexEntity> indexEntityCollection) {
        indexRepository.saveAll(indexEntityCollection);
    }

    @Override
    public void correctSingleLemmaFrequency(LemmaEntity lemmaEntity) {
        int frequency = lemmaEntity.getFrequency();
        if (frequency == 1) {
            lemmaRepository.delete(lemmaEntity);
        } else {
            frequency -= 1;
            lemmaEntity.setFrequency(frequency);
            lemmaRepository.save(lemmaEntity);
        }
    }
}
