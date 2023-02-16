package searchengine.util;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class PropertiesHolder {

    @Value("${connect.useragent}")
    private String useragent;

    @Value("${connect.referrer}")
    private String referrer;

    @Value("${snippet.border}")
    private int snippetBorder;

    @Value("${file.extensions}")
    private String fileExtensions;

    @Value("${selector.weight.title}")
    private float weightTitle;

    @Value("${selector.weight.body}")
    private float weightBody;

    @Value("${error.interrupted}")
    private String interruptedByUserMessage;

    @Value("${error.certificate}")
    private String certificateError;

    @Value("${error.unknown}")
    private String unknownError;
}
