package net.sf.chmpane.springmvc;

import cn.rui.chm.CHMFile;
import cn.rui.chm.SiteMap;
import org.apache.commons.io.IOUtils;
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

    private CHMFile getChm(String path) {
        return pathToChm.get(path);
    }

    @RequestMapping(value = "/{path}", method = RequestMethod.GET, produces = "text/html")
    @ResponseBody
    public String index(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        if (chm == null) {
            return null;
        }
        return "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                "<head>\n" +
                "    <meta charset='UTF-8'>\n" +
                "    <title>CHM</title>\n" +
                "</head>\n" +
                "<frameset cols='20%,*'>\n" +
                "    <frame name='sitemap' src='sitemap.html' />\n" +
                "    <frame name='content' />\n" +
                "</frameset>\n" +
                "</html>";
    }

    @RequestMapping(value = "/{path}/list.{ext}", method = RequestMethod.GET)
    @ResponseBody
    public Object list(@PathVariable("path") String path, @PathVariable("ext") String ext,
                                 HttpServletResponse response) throws IOException {
        if ("json".equals(ext)) {
            return listJson(path, response);
        } else if ("htm".equals(ext) || "html".equals(ext)) {
            return listHtml(path, response);
        }
        response.sendError(HttpStatus.NOT_FOUND.value(), "unknown ext: " + ext);
        return null;
    }

    @RequestMapping(value = "/{path}/list", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public List<String> listJson(@PathVariable("path") String path, HttpServletResponse response) throws IOException {
        CHMFile chm = getChm(path);
        if (chm == null) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "no chm file mapping to " + path);
            return null;
        }
        return chm.list();
    }

    @RequestMapping(value = "/{path}/list", method = RequestMethod.GET, produces = "text/html")
    @ResponseBody
    public String listHtml(@PathVariable("path") String path, HttpServletResponse response) throws IOException {
        CHMFile chm = getChm(path);
        if (chm == null) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "no chm file mapping to " + path);
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head></head><body><ul>\n");
        for (String filename : chm.list()) {
            sb.append("<li><a href='." + filename + "'>" + filename + "</a></li>\n");
        }
        sb.append("</ul></body></html>");
        return sb.toString();
    }

    @RequestMapping(value = "/{path}/sitemap.{ext}", method = RequestMethod.GET)
    @ResponseBody
    public Object siteMap(@PathVariable("path") String path, @PathVariable("ext") String ext,
                       HttpServletResponse response) throws IOException {
        if ("json".equals(ext)) {
            return siteMapJson(path, response);
        } else if ("htm".equals(ext) || "html".equals(ext)) {
            return siteMapHtml(path, response);
        } else if ("hhc".equals(ext)) {
            siteMapHhc(path, response);
            return null;
        }
        response.sendError(HttpStatus.NOT_FOUND.value(), "unknown ext: " + ext);
        return null;
    }

    @RequestMapping(value = "/{path}/sitemap", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public SiteMap siteMapJson(@PathVariable("path") String path, HttpServletResponse response) throws IOException {
        CHMFile chm = getChm(path);
        if (chm == null) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "no chm file mapping to " + path);
            return null;
        }
        SiteMap sitemap = chm.getSiteMap();
        if (sitemap == null) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "no sitemap in the chm file");
            return null;
        }
        return sitemap;
    }

    @RequestMapping(value = "/{path}/sitemap", method = RequestMethod.GET, produces = "text/html")
    @ResponseBody
    public String siteMapHtml(@PathVariable("path") String path, HttpServletResponse response) throws IOException {
        CHMFile chm = getChm(path);
        if (chm == null) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "no chm file mapping to " + path);
            return null;
        }
        SiteMap sitemap = chm.getSiteMap();
        if (sitemap == null) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "no sitemap in the chm file");
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head></head><body>\n");
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
        if (chm == null) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "no chm file mapping to " + path);
            return;
        }
        file(chm, chm.getSiteMapName(), response);
    }

    @RequestMapping("/{path}/**")
    public void file(@PathVariable("path") String path, HttpServletRequest request,
                     HttpServletResponse response) throws IOException {
        CHMFile chm = getChm(path);
        if (chm == null) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "no chm file mapping to " + path);
            return;
        }

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
            IOUtils.copy(is, os);
        } catch (FileNotFoundException ex) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "no file: " + filename + " in chm document.");
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ex) {
                }
            }
        }
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
}
