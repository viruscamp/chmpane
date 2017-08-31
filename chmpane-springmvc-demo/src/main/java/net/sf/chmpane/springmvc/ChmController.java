package net.sf.chmpane.springmvc;

import cn.rui.chm.CHMFile;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Controller
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

    private String getSiteMap(String path) throws IOException {
        CHMFile chm = getChm(path);
        if (chm == null) {
            return null;
        }
        return chm.getSiteMap();
    }

    @RequestMapping(value = "/{path}", method = RequestMethod.GET, produces = "text/html")
    @ResponseBody
    public String index(@PathVariable("path") String path) throws IOException {
        CHMFile chm = getChm(path);
        if (chm == null) {
            return null;
        }
        return "<!DOCTYPE html>\n" +
                "<html lang='en'>\n" +
                "<head>\n" +
                "    <meta charset='UTF-8'>\n" +
                "    <title>CHM</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "<frameset cols='20%,*'>\n" +
                "    　　<frame name='sitemap' src='sitemap' />\n" +
                "    　　<frame name='content' />\n" +
                "</frameset>\n" +
                "</body>\n" +
                "</html>";
    }

    @RequestMapping(value = "/{path}/sitemap", method = RequestMethod.GET, produces = "text/html")
    @ResponseBody
    public String siteMap(@PathVariable("path") String path) throws IOException {
        return getSiteMap(path);
    }

    @RequestMapping("/{path}/**")
    public void file(@PathVariable("path") String path, HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        String uri = (String)request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String filename = uri.substring(path.length() + 2);
        CHMFile chm = getChm(path);
        if (chm == null) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "no chm file mapping to " + path);
            return;
        }
        InputStream is = null;
        OutputStream os = null;
        try {
            response.setContentType(getContentType(filename));
            is = chm.getResourceAsStream(filename);
            os = response.getOutputStream();
            IOUtils.copy(is, os);
        } catch (FileNotFoundException ex) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "no file: " + filename + " within chm file mapping to " + path);
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
