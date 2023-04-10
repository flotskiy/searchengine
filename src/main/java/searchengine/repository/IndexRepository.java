package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.IndexEntity;

import java.util.Collection;
import java.util.Set;

public interface IndexRepository extends JpaRepository<IndexEntity, Long> {

    @Query(value = "SELECT SUM(lemma_rank) FROM Search_index WHERE page_id = :pageId AND lemma_id IN :lemmaIds",
            nativeQuery = true)
    float getAbsRelevance(@Param("pageId") int pageId, @Param("lemmaIds") Collection<Integer> lemmaIds);

    @Query(value = "SELECT page_id FROM Search_index WHERE lemma_id IN :lemmaIds", nativeQuery = true)
    Set<Integer> findPagesIdByLemmaIdIn(@Param("lemmaIds") Collection<Integer> lemmaIds);
}
