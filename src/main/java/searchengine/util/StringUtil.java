package searchengine.util;

import lombok.experimental.UtilityClass;
import org.jsoup.nodes.Element;
import searchengine.exceptions.SiteException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@UtilityClass
public class StringUtil {

    private final String SLASH = "/";

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
        return url.getProtocol() + "://" + domain + SLASH;
    }

    public String cutProtocolAndHost(String pagePath, String homePage) {
        String path = pagePath.substring(homePage.length());
        if (path.contains(".")) {
            path = path.substring(0, path.length() - 1);
        }
        path = path.startsWith(SLASH) ? path : SLASH + path;
        return path;
    }

    public String buildSnippet(List<String> textList, List<Integer> lemmasPositions, int snippetBorder) {
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
            if (isLastEntry(entry, lemmasPositions, snippetBorder)) {
                buildStringBuilder(builder, textList, lemmasPositions, start, end);
            }
        }
        if (builder.toString().isEmpty()) {
            buildStringBuilder(builder, textList, lemmasPositions, start, end);
        }
        return builder.toString();
    }

    public boolean isStringExists(String s) {
        return !(s == null || s.matches("\\s+") || s.isEmpty());
    }

    public String getPathToSave(String pageUrl, String startPage) {
        String pathToSave = StringUtil.cutProtocolAndHost(pageUrl, startPage);
        return pathToSave.contains(".") ? pathToSave : pathToSave + SLASH;
    }

    public boolean isHrefValid(String homePage, String href, String fileExtensions) {
        return href.startsWith(homePage)
                && isHrefToPage(href, fileExtensions)
                && !href.equals(homePage)
                && !href.equals(homePage + "/");
    }

    public boolean isPageAdded(Set<String> webpages, String href) {
        href += href.endsWith("/") ? "" : "/";
        return webpages.contains(href);
    }

    public String getHrefFromAnchor(Element anchor) {
        String href = anchor.absUrl("href");
        href = href.endsWith("/") ? href : href + "/";
        return href.replace("//www.", "//");
    }

    private boolean isHrefToPage(String href, String fileExtensions) {
        if (href.matches(".*(#|\\?).*")) {
            return false;
        }
        return !href.matches(".*\\.(" + fileExtensions + ")/?");
    }

    private boolean isLastEntry(Map.Entry<Integer, Integer> entry, List<Integer> lemmasPositions, int snippetBorder) {
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
}
