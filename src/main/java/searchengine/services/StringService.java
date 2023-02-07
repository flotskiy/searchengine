package searchengine.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.exceptions.SiteException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class StringService {

    @Value("${snippet.border}")
    private int snippetBorder;

    public String cutSlash(String siteNameWithSlash) {
        return siteNameWithSlash.substring(0, siteNameWithSlash.length() - 1);
    }

    public String getStartPage(String path) {
        URL url;
        try {
            url = new URL(path);
        } catch (MalformedURLException e) {
            throw new SiteException("Site url is wrong");
        }
        String domain = url.getHost();
        domain = domain.startsWith("www.") ? domain.substring(4) : domain;
        return url.getProtocol() + "://" + domain + "/";
    }

    public String cutProtocolAndHost(String pagePath, String homePage) {
        String path = pagePath.substring(homePage.length());
        if (path.contains(".")) {
            path = path.substring(0, path.length() - 1);
        }
        path = path.startsWith("/") ? path : "/" + path;
        return path;
    }

    public String buildSnippet(List<String> textList, List<Integer> lemmasPositions) {
        StringBuilder builder = new StringBuilder();
        int start = 0;
        int end = -1;

        Map<Integer, Integer> snippetsBorders = lemmasPositions.stream()
                .collect(TreeMap::new, (map, i) -> map.put(i - snippetBorder, i + snippetBorder), Map::putAll);
        for (Map.Entry<Integer, Integer> entry : snippetsBorders.entrySet()) {
            if (entry.getKey() <= end) {
                end = entry.getValue();
                continue;
            }
            buildStringBuilder(builder, textList, lemmasPositions, start, end);
            start = entry.getKey();
            if (start < 0) {
                start = 0;
            }
            end = entry.getValue();
            if (end >= textList.size()) {
                end = textList.size() - 1;
            }
            if (isLastEntry(entry, lemmasPositions)) {
                buildStringBuilder(builder, textList, lemmasPositions, start, end);
            }
        }
        if (builder.toString().isEmpty()) {
            buildStringBuilder(builder, textList, lemmasPositions, start, end);
        }
        return builder.toString();
    }

    private boolean isLastEntry(Map.Entry<Integer, Integer> entry, List<Integer> lemmasPositions) {
        return (entry.getValue() - snippetBorder) == lemmasPositions.get(lemmasPositions.size() - 1);
    }

    private void buildStringBuilder(
            StringBuilder builder, List<String> textList, List<Integer> lemmasPositions, int start, int end
    ) {
        for (int i = start; i <= end; i++) {
            if (i == start) {
                builder.append("... ");
            }
            if (lemmasPositions.contains(i)) {
                builder.append("<b>").append(textList.get(i)).append("</b>").append(" ");
            } else {
                builder.append(textList.get(i)).append(" ");
            }
            if (i == end) {
                builder.deleteCharAt(builder.length() - 1).append(" ...&emsp;&emsp;");
            }
        }
    }

    public boolean isStringExists(String s) {
        return !(s == null || s.matches("\\s+") || s.isEmpty());
    }
}
