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
    private final StringBuilder BUILDER = new StringBuilder();

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
        if (path.length() > 765) {
            throw new SiteException("Path too looooooong (length is " + path.length() + ")");
        }
        return path;
    }

    public String buildSnippet(List<String> textList, List<Integer> lemmasPositions, int snippetBorder) {
        BUILDER.setLength(0);
        int start = 0;
        int end = -1;

        Map<Integer, Integer> snippetsBorders = lemmasPositions.stream()
                .collect(TreeMap::new, (map, i) -> map.put(i - snippetBorder, i + snippetBorder), Map::putAll);
        for (Map.Entry<Integer, Integer> entry : snippetsBorders.entrySet()) {
            if (entry.getKey() <= end) {
                end = entry.getValue();
                continue;
            }
            buildString(textList, lemmasPositions, start, end);
            start = entry.getKey();
            if (start < 0) {
                start = 0;
            }
            end = entry.getValue();
            if (end >= textList.size()) {
                end = textList.size() - 1;
            }
            if (isLastEntry(entry, lemmasPositions, snippetBorder)) {
                buildString(textList, lemmasPositions, start, end);
            }
        }
        if (BUILDER.toString().isEmpty()) {
            end = textList.size() - 1;
            buildString(textList, lemmasPositions, start, end);
        }
        return BUILDER.toString();
    }

    public boolean isStringExists(String s) {
        return !(s == null || s.matches("\\s+") || s.isEmpty());
    }

    public String getPathToSave(String pageUrl, String startPage) {
        int start = startPage.length();
        String pathToSave = pageUrl.replace("www.", "");
        pathToSave = SLASH + pathToSave.substring(start);
        pathToSave = pathToSave.endsWith(SLASH) || pathToSave.contains(".") ? pathToSave : pathToSave + SLASH;
        return pathToSave;
    }

    public boolean isHrefValid(String homePage, String href, String fileExtensions) {
        return href.startsWith(homePage)
                && isHrefToPage(href, fileExtensions)
                && !href.equals(homePage)
                && !href.equals(homePage + SLASH);
    }

    public boolean isPageAdded(Set<String> webpages, String href) {
        href += href.endsWith(SLASH) ? "" : SLASH;
        return webpages.contains(href);
    }

    public String getHrefFromAnchor(Element anchor) {
        String href = anchor.absUrl("href").trim().replace("\u00A0", "");
        href = href.endsWith(SLASH) ? href : href + SLASH;
        return href.replace("//www.", "//");
    }

    private boolean isHrefToPage(String href, String fileExtensions) {
        if (href.matches(".*([#?\"@\\\\]).*")) {
            return false;
        }
        return !href.matches(".*\\.(" + fileExtensions + ")/?");
    }

    private boolean isLastEntry(Map.Entry<Integer, Integer> entry, List<Integer> lemmasPositions, int snippetBorder) {
        return (entry.getValue() - snippetBorder) == lemmasPositions.get(lemmasPositions.size() - 1);
    }

    private void buildString(List<String> textList, List<Integer> lemmasPositions, int start, int end) {
        for (int i = start; i <= end; i++) {
            if (lemmasPositions.contains(i)) {
                BUILDER.append("<b>").append(textList.get(i)).append("</b>").append(" ");
            } else {
                BUILDER.append(textList.get(i)).append(" ");
            }
            if (i == end) {
                BUILDER.deleteCharAt(BUILDER.length() - 1).append("&emsp;&emsp;");
            }
        }
    }
}
