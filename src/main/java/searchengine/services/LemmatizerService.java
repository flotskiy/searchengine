package searchengine.services;

import java.util.List;
import java.util.Map;

public interface LemmatizerService {

    Map<String, Integer> getLemmasCountMap(String text);

    List<String> getLemmatizedList(List<String> list);
}
