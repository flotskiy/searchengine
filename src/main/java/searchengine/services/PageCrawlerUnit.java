package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.services.implementations.IndexingServiceImpl;
import searchengine.util.JsoupUtil;
import searchengine.util.StringUtil;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;

@RequiredArgsConstructor
@Log4j2
public class PageCrawlerUnit extends RecursiveAction {

    private final transient IndexingServiceImpl service;
    private final transient SiteEntity siteEntity;
    private final String pagePath;

    @SneakyThrows
    @Override
    protected void compute() {
        log.info("NEW PageCrawlerUnit created for pagePath: {}", pagePath);
        try {
            Thread.sleep(500);
            handlePageData();
        } catch (UnsupportedMimeTypeException | ConnectException ignoredException) {
            log.warn("Exception '{}' ignored in PageCrawlerUnit while handling path: {}", ignoredException, pagePath);
        } catch (Exception exception) {
            log.warn("Exception '{}' in PageCrawlerUnit while handling path: {}. " +
                    "Indexing for site '{}' completed with error", exception, pagePath, siteEntity.getUrl());
            service.getStatusMap().put(siteEntity.getUrl(), Status.FAILED);
            throw exception;
        }
    }

    private void handlePageData() throws IOException {
        List<PageCrawlerUnit> forkJoinPoolPagesList = new ArrayList<>();
        String userAgent = service.getProperties().getUseragent();
        String referrer = service.getProperties().getReferrer();
        Connection connection = JsoupUtil.getConnection(pagePath, userAgent, referrer);
        int httpStatusCode = connection.execute().statusCode();
        if (httpStatusCode != 200) {
            connection = JsoupUtil.getConnection(StringUtil.cutSlash(pagePath), userAgent, referrer);
            httpStatusCode = connection.execute().statusCode();
        }

        String pathToSave = StringUtil.cutProtocolAndHost(pagePath, siteEntity.getUrl());
        String html = "";
        PageEntity pageEntity = new PageEntity(pathToSave, httpStatusCode, html, siteEntity);
        if (httpStatusCode != 200) {
            service.savePageContentAndSiteStatusTime(pageEntity, html, siteEntity);
        } else {
            Document document = connection.get();
            html = document.outerHtml();
            service.savePageContentAndSiteStatusTime(pageEntity, html, siteEntity);
            service.handleLemmasAndIndex(html, pageEntity, siteEntity);
            Elements anchors = document.select("body").select("a");
            handleAnchors(anchors, forkJoinPoolPagesList);
        }
        for (PageCrawlerUnit pageCrawlerUnit : forkJoinPoolPagesList) {
            pageCrawlerUnit.join();
        }
    }

    private void handleAnchors(Elements elements, List<PageCrawlerUnit> fjpList) {
        String fileExtensions = service.getProperties().getFileExtensions();
        for (Element anchor : elements) {
            String href = StringUtil.getHrefFromAnchor(anchor);
            if (StringUtil.isHrefValid(siteEntity.getUrl(), href, fileExtensions)
                    && !StringUtil.isPageAdded(service.getWebpagesPathSet(), href)) {
                service.getWebpagesPathSet().add(href);
                if (!service.getStatusMap().get(siteEntity.getUrl()).equals(Status.INDEXING)) {
                    return;
                }
                PageCrawlerUnit pageCrawlerUnit = new PageCrawlerUnit(service, siteEntity, href);
                fjpList.add(pageCrawlerUnit);
                pageCrawlerUnit.fork();
            }
        }
    }
}
