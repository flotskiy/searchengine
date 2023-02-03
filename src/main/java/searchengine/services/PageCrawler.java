package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.RecursiveAction;

@Component
@RequiredArgsConstructor
@Setter
public class PageCrawler extends RecursiveAction {

    private String pagePath;
    private transient SiteEntity siteEntity;

    private final transient PageCrawlerFactory factory;

    @SneakyThrows
    @Override
    protected void compute() {
        List<PageCrawler> forkJoinPoolPagesList = new ArrayList<>();
        try {
            System.out.println("\t\tCreated new PageCrawler for pagePath: " + pagePath);
            Thread.sleep(500);
            Connection connection = getConnection(pagePath);
            Connection.Response response = connection.execute();
            String startPage = factory.getStartPage(pagePath);
            String pathToSave = cutProtocolAndHost(pagePath, startPage);
            String html = "";
            int httpStatusCode = response.statusCode();

            PageEntity pageEntity = createPageEntity(pathToSave, httpStatusCode);
            if (httpStatusCode != 200) {
                savePageEntityAndSiteStatus(pageEntity, html);
            } else {
                Document document = connection.get();
                html = document.outerHtml();
                savePageEntityAndSiteStatus(pageEntity, html);
                handleLemmasAndIndex(html, pageEntity, siteEntity);
                Elements anchors = document.select("body").select("a");
                for (Element anchor : anchors) {
                    String href = anchor.absUrl("href");
                    href = href.endsWith("/") ? href : href + "/";
                    href = href.replace("//www.", "//");
                    if (isHrefValid(startPage, href)) {
                        System.out.println("\t\t\tNew PageCrawler creation for href: " + href);
                        factory.getWebpagesPathSet().add(href);
                        PageCrawler pageCrawler = factory.createPageCrawler();
                        pageCrawler.setPagePath(href);
                        pageCrawler.setSiteEntity(siteEntity);
                        forkJoinPoolPagesList.add(pageCrawler);
                        pageCrawler.fork();
                    } else {
                        System.out.println("\t\t\tNOT created new PageCrawler for href: " + href);
                    }
                }
            }
        } catch (ConnectException connectException) {
            connectException.printStackTrace();
        } catch (InterruptedException | IOException e) {
            System.out.println("Exception in PageCrawler: " + e);
            throw e;
        }
        for (PageCrawler pageCrawler : forkJoinPoolPagesList) {
            pageCrawler.join();
        }
    }

    private void handleLemmasAndIndex(String html, PageEntity page, SiteEntity site) {
        // todo
    }

    private PageEntity createPageEntity(String path, int code) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(path);
        pageEntity.setCode(code);
        pageEntity.setSiteEntity(siteEntity);
        return pageEntity;
    }

    private Connection getConnection(String pagePath) {
        return Jsoup.connect(pagePath)
                .userAgent(factory.getUseragent())
                .referrer(factory.getReferrer())
                .ignoreHttpErrors(true);
    }

    private boolean isHrefValid(String homePage, String href) {
        return href.startsWith(homePage)
                && isHrefToPage(href)
                && !isPageAdded(href)
                && !href.equals(homePage)
                && !href.equals(homePage + "/");
    }

    private boolean isHrefToPage(String href) {
        if (href.matches(".*(#|\\?).*")) {
            return false;
        }
        return !href.matches(".*\\.(" + factory.getFileExtensions() + ")/?");
    }

    private boolean isPageAdded(String pagePath) {
        pagePath += pagePath.endsWith("/") ? "" : "/";
        boolean exists = factory.getWebpagesPathSet().contains(pagePath);
        System.out.println("is added before path: " + pagePath + " - " + exists);
        return exists;
    }

    private String cutProtocolAndHost(String pagePath, String homePage) {
        String path = pagePath.substring(homePage.length());
        if (path.contains(".")) {
            path = path.substring(0, path.length() - 1);
        }
        path = path.startsWith("/") ? path : "/" + path;
        return path;
    }

    private void savePageEntityAndSiteStatus(PageEntity entity, String pageHtml) {
        entity.setContent(pageHtml);
        factory.getPageRepository().save(entity);
        siteEntity.setStatusTime(new Date());
        factory.getSiteRepository().save(siteEntity);
    }
}
