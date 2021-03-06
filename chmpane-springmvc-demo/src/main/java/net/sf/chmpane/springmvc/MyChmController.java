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
import java.io.FileFilter;
import java.text.MessageFormat;
import java.util.Map;

@Controller
@RequestMapping("chm")
@Slf4j
public class MyChmController extends ChmController {

    private static final String CHM_EXT = ".chm";

    @PostConstruct
    public void init() {
        //setSitemMapCss("sitemap.css");
        //setSitemMapJs("sitemap.js");
        String scanDir = chmConfig.getScanDir();
        if (scanDir != null && scanDir.length() != 0) {
            log.info("scan dir {} for chm files", scanDir);
            File dir = new File(scanDir);
            File[] chmfiles = dir.listFiles(new FileFilter() {
                public boolean accept(File f) {
                    return f.isFile() && f.getName().toLowerCase().endsWith(CHM_EXT);
                }
            });
            for (File chmfile : chmfiles) {
                String name = chmfile.getName();
                name = name.substring(0, name.length() - CHM_EXT.length());
                try {
                    addMapping(name, chmfile);
                } catch (Exception ex) {
                    log.error(MessageFormat.format("chmController.addMapping fail: {0} {1}", name, chmfile.getAbsolutePath()), ex);
                }
            }
        }
        if (chmConfig.getMappings() != null) {
            for (Map.Entry<String, String> e : chmConfig.getMappings().entrySet()) {
                String name = e.getKey();
                String filename = e.getValue();
                if (hasMapping(name)) {
                    log.warn(MessageFormat.format("chmController duplicate chm path mapping: {0} found, will be override by {1}", name, filename));
                }
                try {
                    addMapping(name, new File(filename));
                } catch (Exception ex) {
                    log.error(MessageFormat.format("chmController.addMapping fail: {0} {1}", name, filename), ex);
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
        private String scanDir;
        private Map<String, String> mappings;
    }
}
