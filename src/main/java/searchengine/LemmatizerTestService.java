package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import searchengine.services.LemmatizerService;
import searchengine.services.LemmatizerServiceImpl;

import java.io.IOException;
import java.util.*;

public class LemmatizerTestService {

    public static void main(String[] args) throws IOException {

        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        LemmatizerService lemmatizerService = new LemmatizerServiceImpl(luceneMorphology);

        Map<String, Integer> testMap =
                lemmatizerService.getLemmasCountMap("Повторное появление леопарда в Осетии позволяет предположить, " +
                        "что леопард постоянно обитает в некоторых районах Северного Кавказа.");
        testMap.entrySet().forEach(System.out::println);
    }
}
