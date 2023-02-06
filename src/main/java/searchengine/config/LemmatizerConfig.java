package searchengine.config;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class LemmatizerConfig {

    @Bean
    public LuceneMorphology getLuceneMorphology() throws IOException {
        return new RussianLuceneMorphology();
    }
}
