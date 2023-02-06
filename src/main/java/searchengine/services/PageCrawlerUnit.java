package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.jsoup.Connection;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import searchengine.exceptions.SiteException;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveAction;

@Component
@RequiredArgsConstructor
@Setter
public class PageCrawlerUnit extends RecursiveAction {

    private String pagePath;
    private transient SiteEntity siteEntity;

    private final transient PageCrawlerService service;

    @SneakyThrows
    @Override
    protected void compute() throws SiteException {
        try {
            List<PageCrawlerUnit> forkJoinPoolPagesList = new ArrayList<>();
            System.out.println("\tCreated new PageCrawler for pagePath: " + pagePath);
            Thread.sleep(500);
            Connection connection = service.getConnection(pagePath);
            Connection.Response response = connection.execute();
            String startPage = siteEntity.getUrl();
            String pathToSave = service.getStringService().cutProtocolAndHost(pagePath, startPage);
            String html = "";
            int httpStatusCode = response.statusCode();

            PageEntity pageEntity = service.createPageEntity(pathToSave, httpStatusCode, siteEntity);
            if (httpStatusCode != 200) {
                service.savePageEntityAndSiteStatus(pageEntity, html, siteEntity);
            } else {
                Document document = connection.get();
                html = document.outerHtml();
                service.savePageEntityAndSiteStatus(pageEntity, html, siteEntity);
                service.handleLemmasAndIndex(html, pageEntity, siteEntity);
                Elements anchors = document.select("body").select("a");
                for (Element anchor : anchors) {
                    String href = anchor.absUrl("href");
                    href = href.endsWith("/") ? href : href + "/";
                    href = href.replace("//www.", "//");
                    if (service.isHrefValid(startPage, href)) {
                        System.out.println("\t\t\tNew PageCrawler creation for href: " + href);
                        service.getWebpagesPathSet().add(href);
                        PageCrawlerUnit pageCrawlerUnit = service.createPageCrawler();
                        pageCrawlerUnit.setPagePath(href);
                        pageCrawlerUnit.setSiteEntity(siteEntity);
                        forkJoinPoolPagesList.add(pageCrawlerUnit);
                        pageCrawlerUnit.fork();
                    } else {
                        System.out.println("\t\tNOT created new PageCrawler for href: " + href);
                    }
                }
            }
            for (PageCrawlerUnit pageCrawlerUnit : forkJoinPoolPagesList) {
                pageCrawlerUnit.join();
            }
        } catch (UnsupportedMimeTypeException unsupportedMimeTypeException) {
            unsupportedMimeTypeException.printStackTrace();
        } catch (CancellationException | InterruptedException | IOException cancelEx) {
            System.out.println("\n\t\tException in PageCrawler: " + cancelEx + ", path: " + pagePath);
            throw cancelEx;
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
