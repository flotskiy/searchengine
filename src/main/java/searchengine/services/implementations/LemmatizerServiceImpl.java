package searchengine.services.implementations;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.services.interfaces.LemmatizerService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LemmatizerServiceImpl implements LemmatizerService {

    private static final String SERVICE_PARTS_OF_SPEECH = ".*(ПРЕДЛ|СОЮЗ|ЧАСТ|МЕЖД)$";
    private static final Pattern PATTERN = Pattern.compile("ё", Pattern.CANON_EQ);

    private final LuceneMorphology ruLuceneMorphology;

    @Override
    public Map<String, Integer> getLemmasCountMap(String text) {
        Map<String, Integer> lemmasCountMap = new HashMap<>();
        for (String word : getWordsWithoutServicePartsOfSpeech(text)) {
            for (String wordNormalForm : ruLuceneMorphology.getNormalForms(word)) {
                wordNormalForm = PATTERN.matcher(wordNormalForm).replaceAll("е");
                lemmasCountMap.put(wordNormalForm, lemmasCountMap.getOrDefault(wordNormalForm, 0) + 1);
            }
        }
        return lemmasCountMap;
    }

    @Override
    public List<String> getLemmatizedList(List<String> list) {
        return list
                .stream()
                .map(s -> s = s.matches("[^(.+)?[а-яёА-ЯЁ]+(.+)?]") ? "в" : s)
                .map(String::toLowerCase)
                .map(this::getCyrillicWord)
                .map(s -> s = s.length() < 1 ? "в" : s)
                .map(s -> s = ruLuceneMorphology.getMorphInfo(s).toString().matches(SERVICE_PARTS_OF_SPEECH) ?
                        "" : ruLuceneMorphology.getNormalForms(s).get(0))
                .toList();
    }

    private List<String> getWordsWithoutServicePartsOfSpeech(String text) {
        return Arrays.stream((text).split("[^а-яёА-ЯЁ]+"))
                .filter(word -> word.length() != 0)
                .map(String::toLowerCase)
                .filter(word -> ruLuceneMorphology
                        .getMorphInfo(word)
                        .stream()
                        .noneMatch(baseFormWord -> baseFormWord.matches(SERVICE_PARTS_OF_SPEECH)))
                .toList();
    }

    private String getCyrillicWord(String word) {
        return word.replaceAll("[^а-яё]", "");
    }
}
