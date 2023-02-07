package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.IndexEntity;

import java.util.Collection;

public interface IndexRepository extends JpaRepository<IndexEntity, Long> {

    @Query(value = "SELECT SUM(lemma_rank) FROM Search_index WHERE page_id = :pageId AND lemma_id IN :lemmasList",
            nativeQuery = true)
    float getAbsRelevance(@Param("pageId") int pageId, @Param("lemmasList") Collection<Integer> lemmasList);
}
