package org.libx.libappdatabase;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class PublishServlet extends HttpServlet {
    public void init(ServletConfig config) throws ServletException {
        InitializeBaseX.initialize(config.getServletContext());
    }

    public void doGet (HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        /*
         * This is what the various methods of HttpServletRequest return:
         *
         * getPathInfo=/base/more/path/elements
         * getPathTranslated=/opt/tomcat6libx/webapps/gbed/base/more/path/elements
         * getQueryString=uri=http://tjwebb.cs.vt.edu/libx2/feed/3
         * getContextPath=/gbed
         * getServletPath=/feeds
         * getRequestURI=/gbed/feeds/base/more/path/elements
         * getRequestURL=http://top.cs.vt.edu:8080/gbed/feeds/base/more/path/elements
         */

        StringBuffer baseURL = req.getRequestURL();
        baseURL.setLength(baseURL.lastIndexOf(
                    req.getContextPath() + req.getServletPath() + req.getPathInfo()));

        String [] path = req.getPathInfo().split("/");
        String documentName = path[1];
        if (path.length > 3) {
            String correctBase = baseURL + req.getContextPath() + req.getServletPath();
            PrintWriter out = resp.getWriter();
            out.println("Illegal URL - use: " 
                    + correctBase + "/name_of_feed"
                    + " or "
                    + correctBase + "/name_of_feed/id_of_entry"
                    );
            return;
        }

        String entryId = null;
        if (path.length == 3)
            entryId = path[2];

        PrintWriter out = null;
        try {
            XMLDataModel m = new XMLDataModel();
            String contentType = "application/atom+xml;charset=\"utf-8\"";

            /* 
             * http://zinfandel.levkowetz.com/html/draft-ietf-atompub-typeparam-00#section-3
             *
             * The value 'entry' indicates that the media type identifies an Atom
             * Entry Document.  The root element of the document MUST be atom:entry.
             *
             * The value 'feed' indicates that the media type identifies an Atom
             * Feed Document.  The root element of the document MUST be atom:feed.
             *
             * If not specified, the type is assumed to be unspecified, requiring
             * Atom processors to examine the root element to determine the type of
             * Atom document.
             */
            if (entryId == null)
                contentType += ";type=feed";
            else
                contentType += ";type=entry";

            resp.setContentType(contentType);

            out = resp.getWriter();
            out.print("<?xml version='1.0'?>\n");
            String reqUrl = req.getRequestURL().toString();
            if (reqUrl.endsWith("/"))
                reqUrl = reqUrl.replaceFirst("/+$", "");

            if (entryId == null)
                out.print(m.publishFeed(documentName, reqUrl));
            else
                out.print(m.publishFeedEntry(documentName, reqUrl, entryId));
        }
        catch (Exception ex) {
            if (out == null)
                out = resp.getWriter();
            out.println("Error publishing feed: ");
            ex.printStackTrace(out);
            ex.printStackTrace();
        }
    }
}
