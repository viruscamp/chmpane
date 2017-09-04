package net.sf.chmpane.springmvc;

import cn.rui.chm.CHMFile;
import cn.rui.chm.SharpSystem;
import cn.rui.chm.SiteMap;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChmController {
    private Map<String, CHMFile> pathToChm = new HashMap<String, CHMFile>();

    public void addMapping(String path, File file) throws IOException {
        CHMFile chm = new CHMFile(file);
        pathToChm.put(trimPath(path), chm);
    }

    private String trimPath(String path) {
        path = path.trim();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    public void removeMapping(String path) {
        pathToChm.remove(path);
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException() {
            super();
        }
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    private CHMFile getChm(String path) {
        CHMFile chm = pathToChm.get(path);
        if (chm == null) {
            throw new ResourceNotFoundException("no chm file mapping to " + path);
        }
        return chm;
    }

    @RequestMapping(value = "/{path}", method = RequestMethod.GET, produces = "text/html;charset=utf-8")
    @ResponseBody
    public String index(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        SharpSystem sharpSystem = chm.getSharpSystem();
        String defaultTopic = sharpSystem.getProperties().get(SharpSystem.HhpOption.DefaultTopic);
        return "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Frameset//EN\" \n" +
                "    \"http://www.w3.org/TR/html4/frameset.dtd\">" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset='utf-8'>\n" +
                "    <title>CHM</title>\n" +
                "</head>\n" +
                "<frameset cols='20%,*'>\n" +
                "    <frame name='sitemap' src='sitemap.html' />\n" +
                "    <frame name='content' " +
                ((defaultTopic == null) ? "" : ("src='" + defaultTopic + "'")) +
                "/>\n" +
                "</frameset>\n" +
                "</html>";
    }

    @RequestMapping(value = "/{path}/list", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    @ResponseBody
    public List<String> listJson(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        return chm.list();
    }

    @RequestMapping(value = "/{path}/list", method = RequestMethod.GET, produces = "text/html;charset=utf-8")
    @ResponseBody
    public String listHtml(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'></head><body><ul>\n");
        for (String filename : chm.list()) {
            sb.append("<li><a href='." + filename + "'>" + filename + "</a></li>\n");
        }
        sb.append("</ul></body></html>");
        return sb.toString();
    }

    private SiteMap getSiteMap(CHMFile chm) throws IOException {
        SiteMap sitemap = chm.getContentsSiteMap();
        if (sitemap == null) {
            throw new ResourceNotFoundException("no sitemap in the chm file");
        }
        return sitemap;
    }

    @RequestMapping(value = "/{path}/sitemap", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    @ResponseBody
    public SiteMap siteMapJson(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        SiteMap sitemap = getSiteMap(chm);
        return sitemap;
    }

    @RequestMapping(value = "/{path}/sitemap", method = RequestMethod.GET, produces = "text/html;charset=utf-8")
    @ResponseBody
    public String siteMapHtml(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        SiteMap sitemap = getSiteMap(chm);
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'></head><body>\n");
        siteMapItemToHtml(sitemap.getRoot(), sb);
        sb.append("</body></html>");
        return sb.toString();
    }

    private void siteMapItemToHtml(SiteMap.Item item, StringBuilder sb) {
        if (item.getName() != null) {
            if (item.getLocal() != null) {
                sb.append("<li><a target='content' href='").append(item.getLocal()).append("'>").append(item.getName()).append("</a>\n");
            } else {
                sb.append("<li>").append(item.getName()).append("\n");
            }
        }
        if (item.getItems() != null) {
            sb.append("<ul>\n");
            for (SiteMap.Item subItem : item.getItems()) {
                siteMapItemToHtml(subItem, sb);
            }
            sb.append("</ul>\n");
        }
        if (item.getName() != null) {
            sb.append("</li>\n");
        }
    }

    @RequestMapping(value = "/{path}/sitemap.hhc", method = RequestMethod.GET)
    public void siteMapHhc(@PathVariable("path") String path, HttpServletResponse response) throws IOException {
        CHMFile chm = getChm(path);
        file(chm, chm.getContentsSiteMapName(), response);
    }

    @RequestMapping(value = "/{path}/sharp-system", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    @ResponseBody
    public SharpSystem sharpSystem(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        return chm.getSharpSystem();
    }

    @RequestMapping("/{path}/**")
    public void file(@PathVariable("path") String path, HttpServletRequest request,
                     HttpServletResponse response) throws IOException {
        CHMFile chm = getChm(path);

        String uri = (String)request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String)request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        AntPathMatcher apm = new AntPathMatcher();
        String filename = apm.extractPathWithinPattern(bestMatchPattern, uri);
        if (!filename.startsWith("/")) {
            filename = "/" + filename;
        }

        file(chm, filename, response);
    }

    private void file(CHMFile chm, String filename, HttpServletResponse response) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            response.setContentType(getContentType(filename));
            is = chm.getResourceAsStream(filename);
            os = response.getOutputStream();
            copyLarge(is, os);
        } catch (FileNotFoundException ex) {
            throw new ResourceNotFoundException("no file: " + filename + " in chm document.");
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ex) {
                }
            }
        }
    }

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    public static long copyLarge(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    private String getContentType(String filename) {
        int i = filename.lastIndexOf('.');
        if (i >= 0) {
            String ext = filename.substring(i).toLowerCase();
            if (".gif".equals(ext))
                return "image/gif";
            else if (".jpeg".equals(ext) || ".jpg".equals(ext))
                return "image/jpeg";
            else if (".png".equals(ext))
                return "image/png";
            else if (".css".equals(ext))
                return "text/css";
            else if (".js".equals(ext))
                return "application/x-javascript";
        }
        return "text/html";
    }

    /*
    private String getContentType(String filename) {
        FileNameMap fileNameMap = URLConnection.getFileNameMap();
        String mimeType = fileNameMap.getContentTypeFor(filename);
        return mimeType;
    }
    */
}
