package searchengine.util;

import lombok.experimental.UtilityClass;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.exceptions.SiteException;

import java.io.IOException;

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

    public Connection.Response getResponse(Connection connection) {
        Connection.Response response;
        try {
            response = connection.execute();
        } catch (IOException e) {
            throw new SiteException("Connection request failed while getting Response");
        }
        return response;
    }

    public Document getDocument(Connection connection) {
        Document document;
        try {
            document = connection.get();
        } catch (IOException e) {
            throw new SiteException("Connection request failed while getting Document");
        }
        return document;
    }
}
