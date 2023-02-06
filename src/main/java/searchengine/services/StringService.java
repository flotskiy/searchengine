package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.exceptions.SiteException;

import java.net.MalformedURLException;
import java.net.URL;

@Service
public class StringService {

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
}
