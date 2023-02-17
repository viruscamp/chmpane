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
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChmController {
    private Map<String, File> mappings = new HashMap<String, File>();

    private Map<File, CHMFile> mappingCached = new HashMap<File, CHMFile>();

    public void addMapping(String path, File file) {
        mappings.put(trimPath(path), file);
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
        mappings.remove(path);
    }

    public boolean hasMapping(String path) {
        return mappings.containsKey(path);
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
        File file = mappings.get(path);
        if (file == null) {
            throw new ResourceNotFoundException("no chm file mapping to " + path);
        }
        CHMFile chm;
        if (mappingCached.containsKey(file)) {
            chm = mappingCached.get(file);
        } else {
            synchronized(file) {
                if (mappingCached.containsKey(file)) {
                    chm = mappingCached.get(file);
                } else {
                    try {
                        chm = new CHMFile(file);
                        mappingCached.put(file, chm);
                    } catch (FileNotFoundException fnfe) {
                        throw new ResourceNotFoundException("chm file " + file + " not found");
                    } catch (IOException ioex) {
                        throw new RuntimeException(ioex);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
        return chm;
    }

    @RequestMapping(value = "/", method = RequestMethod.GET, produces = "text/html;charset=utf-8")
    @ResponseBody
    public String root() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\n");
        sb.append("<head><title>Chm List</title></head>\n");
        sb.append("<body><ul>\n");
        for (String path : mappings.keySet()) {
            sb.append("<li><a href='").append(path).append("/'>").append(path).append("</a></li>");
        }
        sb.append("</ul></body>");
        return sb.toString();
    }

    @RequestMapping(value = "/{path}/", method = RequestMethod.GET, produces = "text/html;charset=utf-8")
    @ResponseBody
    public String frames(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        SharpSystem sharpSystem = chm.getSharpSystem();
        String defaultTopic = sharpSystem.getProperty(SharpSystem.HhpOption.DefaultTopic);
        String contentsFileName = chm.getContentsFileName();
        String title = sharpSystem.getProperty(SharpSystem.HhpOption.Title);
        return "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Frameset//EN\" \n" +
                "    \"http://www.w3.org/TR/html4/frameset.dtd\">" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset='utf-8'>\n" +
                "    <title>" + (title != null ? title : (path + ".chm")) + "</title>\n" +
                "</head>\n" +
                ((contentsFileName == null) ?
                "<frameset cols='*'>\n" :
                "<frameset cols='20%,*'>\n" +
                "    <frame name='sitemap' src='contents.html' />\n") +
                "    <frame name='content' " +
                ((defaultTopic == null) ? "" : ("src='resources/" + defaultTopic + "'")) +
                "/>\n" +
                "</frameset>\n" +
                "</html>";
    }

    @RequestMapping(value = "/{path}/list", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    @ResponseBody
    public List<String> listJson(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        return chm.getResources();
    }

    @RequestMapping(value = "/{path}/list", method = RequestMethod.GET, produces = "text/html;charset=utf-8")
    @ResponseBody
    public String listHtml(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'></head><body><ul>\n");
        for (String filename : chm.getResources()) {
            sb.append("<li><a href='resources" + filename + "'>" + filename + "</a></li>\n");
        }
        sb.append("</ul></body></html>");
        return sb.toString();
    }

    @RequestMapping(value = "/{path}/contents", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    @ResponseBody
    public SiteMap contentsJson(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        SiteMap sitemap = chm.getContentsSiteMap();
        if (sitemap == null) {
            throw new ResourceNotFoundException("no contents sitemap in the chm file");
        }
        return sitemap;
    }

    @RequestMapping(value = "/{path}/index", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    @ResponseBody
    public SiteMap indexJson(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        SiteMap sitemap = chm.getIndexSiteMap();
        if (sitemap == null) {
            throw new ResourceNotFoundException("no index sitemap in the chm file");
        }
        return sitemap;
    }

    private String sitemMapCss = null;
    public String getSitemMapCss() {
        return sitemMapCss;
    }
    public void setSitemMapCss(String val) {
        sitemMapCss = val;
    }
    private String sitemMapJs = null;
    public String getSitemMapJs() {
        return sitemMapJs;
    }
    public void setSitemMapJs(String val) {
        sitemMapJs = val;
    }

    @RequestMapping(value = "/{path}/contents", method = RequestMethod.GET, produces = "text/html;charset=utf-8")
    @ResponseBody
    public String contentsHtml(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        SiteMap sitemap = chm.getContentsSiteMap();
        if (sitemap == null) {
            throw new ResourceNotFoundException("no contents sitemap in the chm file");
        }
        return sitemapHtml(sitemap);
    }

    @RequestMapping(value = "/{path}/index", method = RequestMethod.GET, produces = "text/html;charset=utf-8")
    @ResponseBody
    public String indexHtml(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        SiteMap sitemap = chm.getIndexSiteMap();
        if (sitemap == null) {
            throw new ResourceNotFoundException("no index sitemap in the chm file");
        }
        return sitemapHtml(sitemap);
    }

    public String sitemapHtml(SiteMap sitemap) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>\n");
        if (sitemMapCss != null && sitemMapCss.length() != 0) {
            sb.append("<link type='text/css' rel='stylesheet' href='").append(sitemMapCss).append("'>");
        }
        if (sitemMapJs != null && sitemMapJs.length() != 0) {
            sb.append("<script type='text/javascript' src='").append(sitemMapJs).append("'></script>");
        }
        sb.append("</head><body>\n");
        siteMapItemToHtml(sitemap.getRoot(), sb);
        sb.append("</body></html>");
        return sb.toString();
    }

    private void siteMapItemToHtml(SiteMap.Item item, StringBuilder sb) {
        String name = item.getName();
        if (name != null && !name.isEmpty()) {
            String local = item.getLocal();
            if (local != null && !local.isEmpty()) {
                sb.append("<li><a target='content' href='resources/").append(local).append("'>").append(name).append("</a>\n");
            } else {
                sb.append("<li>").append(name).append("\n");
            }
        }
        if (item.getChildren() != null) {
            sb.append("<ul>\n");
            for (SiteMap.Item subItem : item.getChildren()) {
                siteMapItemToHtml(subItem, sb);
            }
            sb.append("</ul>\n");
        }
        if (name != null && !name.isEmpty()) {
            sb.append("</li>\n");
        }
    }

    @RequestMapping(value = "/{path}/contents.hhc", method = RequestMethod.GET, produces = "text/html")
    public void contentsHhc(@PathVariable("path") String path, HttpServletResponse response) throws IOException {
        CHMFile chm = getChm(path);
        file(chm, chm.getContentsFileName(), response);
    }

    @RequestMapping(value = "/{path}/index.hhk", method = RequestMethod.GET, produces = "text/html")
    public void indexHhk(@PathVariable("path") String path, HttpServletResponse response) throws IOException {
        CHMFile chm = getChm(path);
        file(chm, chm.getIndexFileName(), response);
    }

    @RequestMapping(value = "/{path}/sharp-system", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    @ResponseBody
    public SharpSystem sharpSystem(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        return chm.getSharpSystem();
    }

    @RequestMapping(value = "/{path}/langs", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    @ResponseBody
    public Object langs(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        return chm.getLangs();
    }

    @RequestMapping("/{path}/resources/**")
    public void file(@PathVariable("path") String path, HttpServletRequest request,
                     HttpServletResponse response) throws IOException {
        CHMFile chm = getChm(path);

        String uri = (String)request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String)request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        AntPathMatcher apm = new AntPathMatcher();
        String filename = apm.extractPathWithinPattern(bestMatchPattern, uri);
        file(chm, filename, response);
    }

    private void file(CHMFile chm, String filename, HttpServletResponse response) throws IOException {
        filename = CHMFile.normalizeFilename(filename);
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
                return "application/javascript";
            else if (".hhc".equals(ext) || ".hhk".equals(ext) || ".html".equals(ext) || ".htm".equals(ext))
                return "text/html";
        }
        return "application/octet-stream";
    }

    private String getContentType1(String filename) {
        FileNameMap fileNameMap = URLConnection.getFileNameMap();
        String mimeType = fileNameMap.getContentTypeFor(filename);
        return mimeType;
    }
}
