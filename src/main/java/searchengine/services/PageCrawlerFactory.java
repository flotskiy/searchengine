package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import searchengine.exceptions.SiteException;
import searchengine.repository.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Getter
public class PageCrawlerFactory {

    @Value("${connect.useragent}")
    private String useragent;
    @Value("${connect.referrer}")
    private String referrer;
    @Value("${file.extensions}")
    private String fileExtensions;

    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;

    @Setter
    private Set<String> webpagesPathSet;

    @Bean
    @Scope("prototype")
    public PageCrawler createPageCrawler() {
        return new PageCrawler(this);
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
}
