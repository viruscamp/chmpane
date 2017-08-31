package net.sf.chmpane.springmvc;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Map;

@SpringBootApplication
@EnableWebMvc
@Slf4j
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Autowired
    private ChmController chmController;

    @PostConstruct
    public void init() throws IOException {
        if (chmConfig.getMappings() != null) {
            for (Map.Entry<String, String> e : chmConfig.getMappings().entrySet()) {
                try {
                    chmController.addMapping(e.getKey(), new File(e.getValue()));
                } catch (Exception ex) {
                    log.error(MessageFormat.format("chmController.addMapping fail: {0} {1}", e.getKey(), e.getValue()), ex);
                }
            }
        }
    }

    @Autowired
    private ChmConfig chmConfig;

    @Component
    @ConfigurationProperties(prefix = "chm")
    @Data
    public static class ChmConfig {
        private Map<String, String> mappings;
    }
}
