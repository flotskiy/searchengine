package searchengine.util;

import lombok.experimental.UtilityClass;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

@UtilityClass
public class JsoupUtil {

    public Connection getConnection(String pagePath, String useragent, String referrer) {
        return Jsoup.connect(pagePath)
                .userAgent(useragent)
                .referrer(referrer)
                .ignoreHttpErrors(true);
    }

    public Document parse(String html) {
        return Jsoup.parse(html);
    }
}
