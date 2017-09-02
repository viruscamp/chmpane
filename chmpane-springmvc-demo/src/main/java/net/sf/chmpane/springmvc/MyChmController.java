package net.sf.chmpane.springmvc;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.PostConstruct;
import java.io.File;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("chm")
@Slf4j
public class MyChmController extends ChmController {

    @PostConstruct
    public void init() {
        if (chmConfig.getScanDir() != null) {
            for (String dir : chmConfig.getScanDir()) {
                // TODO scan for *.chm and add mappings
                log.info("scan dir {} for chm files", dir);
            }
        }
        if (chmConfig.getMappings() != null) {
            for (Map.Entry<String, String> e : chmConfig.getMappings().entrySet()) {
                try {
                    addMapping(e.getKey(), new File(e.getValue()));
                } catch (Exception ex) {
                    log.error(MessageFormat.format("chmController.addMapping fail: {0} {1}", e.getKey(), e.getValue()), ex);
                }
            }
        }
    }

    @Autowired
    private ChmConfig chmConfig;

    @Component
    @EnableConfigurationProperties
    @ConfigurationProperties(prefix = "chm")
    @Data
    public static class ChmConfig {
        private List<String> scanDir;
        private Map<String, String> mappings;
    }
}
