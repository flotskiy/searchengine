package searchengine.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.RepoAccessService;
import searchengine.util.PropertiesHolder;
import searchengine.util.StringUtil;

import javax.net.ssl.SSLHandshakeException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

@Service
@RequiredArgsConstructor
@Log4j2
public class RepoAccessServiceImpl implements RepoAccessService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PropertiesHolder properties;

    @Override
    public SiteEntity findSiteEntityByUrl(String url) {
        return siteRepository.findSiteEntityByUrl(url);
    }

    @Override
    public SiteEntity prepareSiteIndexing(Site site) {
        String homePage = StringUtil.getStartPage(site.getUrl());
        SiteEntity oldSiteEntity = siteRepository.findSiteEntityByUrl(homePage);
        if (oldSiteEntity != null) {
            oldSiteEntity.setStatus(Status.INDEXING);
            oldSiteEntity.setStatusTime(new Date());
            siteRepository.save(oldSiteEntity);
            siteRepository.deleteSiteEntityByUrl(homePage);
        }
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setStatusTime(new Date());
        siteEntity.setUrl(homePage);
        siteEntity.setName(site.getName());
        return siteRepository.save(siteEntity);
    }

    @Override
    public PageEntity createPageEntity(String path, int code, SiteEntity siteEntity) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(path);
        pageEntity.setCode(code);
        pageEntity.setSiteEntity(siteEntity);
        return pageEntity;
    }

    @Override
    public PageEntity deleteOldPageEntity(String path, SiteEntity siteEntity) {
        PageEntity pageEntityToDelete = pageRepository.findPageEntityByPathAndSiteEntity(path, siteEntity);
        if (pageEntityToDelete == null) {
            return null;
        }
        pageRepository.delete(pageEntityToDelete);
        return pageEntityToDelete;
    }

    @Override
    public List<SiteEntity> getAllSites() {
        return siteRepository.findAll();
    }

    @Override
    public boolean existsByStatus(Status status) {
        return siteRepository.existsByStatus(status);
    }

    @Override
    public void savePageContentAndSiteStatusTime(PageEntity pageEntity, String pageHtml, SiteEntity siteEntity) {
        pageEntity.setContent(pageHtml);
        pageRepository.save(pageEntity);
        siteEntity.setStatusTime(new Date());
        siteRepository.save(siteEntity);
    }

    @Override
    public float getAbsRelevance(int pageId, Collection<Integer> lemmaIds) {
        return indexRepository.getAbsRelevance(pageId, lemmaIds);
    }

    @Override
    public Set<Integer> findPagesIdsByLemmaIdIn(Collection<Integer> lemmaIds) {
        return indexRepository.findPagesIdsByLemmaIdIn(lemmaIds);
    }

    @Override
    public void markSiteAsIndexed(Site site) {
        String homePage = StringUtil.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(new Date());
        siteEntity.setStatus(Status.INDEXED);
        siteRepository.save(siteEntity);
    }

    @Override
    public void fixSiteIndexingError(Site site, Exception e) {
        String error = getErrorMessage(e);
        String homePage = StringUtil.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(new Date());
        siteEntity.setStatus(Status.FAILED);
        siteEntity.setLastError(error);
        siteRepository.save(siteEntity);
    }

    @Override
    public void fixSiteStatusAfterSinglePageIndexed(SiteEntity site) {
        site.setStatus(Status.INDEXED);
        siteRepository.save(site);
    }

    @Override
    public List<PageEntity> getAllPagesByIdIn(Collection<Integer> pageIdSet) {
        return pageRepository.findAllById(pageIdSet);
    }

    @Override
    public int countPageEntitiesBySiteEntity(SiteEntity siteEntity) {
        return pageRepository.countPageEntitiesBySiteEntity(siteEntity);
    }

    @Override
    public float get95perCentPagesLimit(int siteId) {
        return pageRepository.get95perCentPagesLimit(siteId);
    }

    @Override
    public void saveLemmaCollection(Collection<LemmaEntity> lemmaEntityCollection) {
        lemmaRepository.saveAll(lemmaEntityCollection);
    }

    @Override
    public void saveIndexCollection(Collection<IndexEntity> indexEntityCollection) {
        indexRepository.saveAll(indexEntityCollection);
    }

    @Override
    public int countLemmaEntitiesBySiteId(SiteEntity siteEntity) {
        return lemmaRepository.countLemmaEntitiesBySiteId(siteEntity);
    }

    @Override
    public List<LemmaEntity> findLemmaEntitiesByLemmaIn(Collection<String> list) {
        return lemmaRepository.findLemmaEntitiesByLemmaIn(list);
    }

    @Override
    public List<LemmaEntity> findLemmaEntitiesByLemmaInAndSiteId(Collection<String> list, SiteEntity siteEntity) {
        return lemmaRepository.findLemmaEntitiesByLemmaInAndSiteId(list, siteEntity);
    }

    @Override
    public void reduceByOneLemmaFrequencies(Collection<String> lemmas, int siteId) {
        lemmaRepository.reduceByOneLemmaFrequencies(siteId, lemmas);
    }

    @Override
    public void deleteLemmasWithLowFrequencies(int siteId) {
        lemmaRepository.deleteLemmasWithLowFrequencies(siteId);
    }

    private String getErrorMessage(Exception e) {
        log.info("Creating error message for: '{}'", e.toString());
        if (e instanceof CancellationException || e instanceof InterruptedException) {
            return properties.getInterruptedByUserMessage();
        } else if (e instanceof CertificateExpiredException || e instanceof SSLHandshakeException
                || e instanceof CertPathValidatorException) {
            return properties.getCertificateError();
        } else {
            return properties.getUnknownError() + " (" + e + ")";
        }
    }
}
