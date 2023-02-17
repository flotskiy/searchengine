package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.exceptions.SiteException;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.services.implementations.IndexingServiceImpl;
import searchengine.util.JsoupUtil;
import searchengine.util.StringUtil;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveAction;

@RequiredArgsConstructor
@Log4j2
public class PageCrawlerUnit extends RecursiveAction {

    private final transient IndexingServiceImpl service;
    private final transient SiteEntity siteEntity;
    private final String pagePath;

    @SneakyThrows
    @Override
    protected void compute() throws SiteException {
        log.info("NEW PageCrawlerUnit created for pagePath: {}", pagePath);
        try {
            handlePageData();
        } catch (UnsupportedMimeTypeException | ConnectException ignoredException) {
            log.warn(ignoredException);
        } catch (CancellationException | InterruptedException | IOException cancelEx) {
            log.warn("Exception '{}' in PageCrawlerUnit while handling path: {}. " +
                    "Indexing for site '{}' completed with error", cancelEx, pagePath, siteEntity.getUrl());
            throw cancelEx;
        } catch (Exception anotherException) {
            log.warn(anotherException);
        }
    }

    private void handlePageData() throws InterruptedException, IOException {
        Thread.sleep(500);
        List<PageCrawlerUnit> forkJoinPoolPagesList = new ArrayList<>();

        String userAgent = service.getProperties().getUseragent();
        String referrer = service.getProperties().getReferrer();
        Connection connection = JsoupUtil.getConnection(pagePath, userAgent, referrer);
        int httpStatusCode = connection.execute().statusCode();
        String pathToSave = StringUtil.cutProtocolAndHost(pagePath, siteEntity.getUrl());
        PageEntity pageEntity = service.createPageEntity(pathToSave, httpStatusCode, siteEntity);

        String html = "";
        if (httpStatusCode != 200) {
            service.savePageContentAndSiteStatus(pageEntity, html, siteEntity);
        } else {
            Document document = connection.get();
            html = document.outerHtml();
            service.savePageContentAndSiteStatus(pageEntity, html, siteEntity);
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
                PageCrawlerUnit pageCrawlerUnit = new PageCrawlerUnit(service, siteEntity, href);
                fjpList.add(pageCrawlerUnit);
                pageCrawlerUnit.fork();
            }
        }
    }
}
