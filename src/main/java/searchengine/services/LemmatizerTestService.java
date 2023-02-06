package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

public class LemmatizerTestService {

    public static void main(String[] args) throws IOException {

        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        LemmatizerService lemmatizerService = new LemmatizerServiceImpl(luceneMorphology);

        Map<String, Integer> testMap =
                lemmatizerService.getLemmasCountMap("Повторное появление леопарда в Осетии позволяет предположить, " +
                        "что леопард постоянно обитает в некоторых районах Северного Кавказа.");
        System.out.println("lemmatizerService.getLemmasCountMap():");
        testMap.entrySet().forEach(System.out::println);

        String inputText =
                "из над под э эх или и за от не под между к же ох ой ах эй ай " +
                        "Предлог — служебная часть речи, обозначающая отношение между объектом и субъектом, " +
                        "выражающая синтаксическую зависимость имен существительных, местоимений, числительных " +
                        "от других слов в словосочетаниях и предложениях. Предлоги, как и все служебные слова, " +
                        "не могут употребляться самостоятельно, они всегда относятся к какому-нибудь существительному " +
                        "(или слову, употребляемому в функции существительного).";
        List<String> textList = new ArrayList<>(Arrays.asList(inputText.split("\\s+")));
        List<String> textListLemmatized = lemmatizerService.getLemmatizedList(textList);
        Map<Integer, String> textMapLemmatized =
                textListLemmatized.stream().collect(HashMap::new, (map, s) -> map.put(map.size(), s), Map::putAll);
        System.out.println("\ntextMapLemmatized.forEach((k, v) -> System.out.println(k + \" - \" + v));");
        textMapLemmatized.forEach((k, v) -> System.out.println(k + " - " + v));
    }
}
