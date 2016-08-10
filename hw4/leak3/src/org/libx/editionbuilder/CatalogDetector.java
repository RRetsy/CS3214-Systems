package org.libx.editionbuilder;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;

import java.lang.reflect.Field;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Comment;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Nodes;
import nu.xom.XPathContext;

import org.exolab.castor.xml.Unmarshaller;

import org.libx.xml.Aleph;
import org.libx.xml.Bookmarklet;
import org.libx.xml.Evergreen;
import org.libx.xml.Horizon;
import org.libx.xml.Millenium;
import org.libx.xml.Polaris;
import org.libx.xml.Primo;
import org.libx.xml.Searchoption;
import org.libx.xml.Sirsi;
import org.libx.xml.Talisprism;
import org.libx.xml.Voyager7;
import org.libx.xml.Voyager;
import org.libx.xml.Vubis;
import org.libx.xml.Worldcat;

import org.w3c.dom.NodeList;

import org.xml.sax.XMLReader;

import org.xml.sax.helpers.XMLReaderFactory;

import org.zkoss.zk.ui.Component;

import org.zkoss.zk.ui.event.Event;

import org.zkoss.zul.Label;
import org.zkoss.zul.Timer;

/**
 * Code to automatically detect catalogs. 
 *
 * @author Godmar Back, Tilottama Gaat
 */
public class CatalogDetector
{
    static Vector<Plugin> pluginVector = new Vector<Plugin>();
    static XPathContext htmlContext = new XPathContext("html", "http://www.w3.org/1999/xhtml");

    public interface CatalogFoundCallback {
        /* called whenever some catalogs have been found */ 
        public void foundCatalogs(CatalogDetector.CatalogProbe.Result [] cat);
        /* called when catalog detection has finished */
        public void done(int totalcatalogsfound);
    }

    /**
     * ProbeContext provides useful methods to a probe or plugin.
     * (The multiprobe implementation passes this on to the plugins
     * via the examine method.)
     */
    interface ProbeContext {
        /**
         * Start a new catalog probe for a given url within the
         * same ProbeContext.
         */
        public void startProbeThread(CatalogProbe probe, String url);

        /**
         * Add a probe result.
         */
        public void addResult(CatalogProbe.Result r);

        /* add more as needed, possibly derived from ProbeTimer implementation ... */
    }

    public interface CatalogProbe {
        /**
         * Instances of CatalogProbe.Result will contain all necessary information
         * to import a catalog.  This includes a Castor beans of a org.libx.xml.* type,
         * such as Millenium, Sirsi, etc. that can be simply added to the model.
         *
         * However, adding a catalog may also require adding search options.
         */
        static class Result {
            private String msg;         // informative message for user that allows them
                                        // to decide whether to import this result
            private Object result;      // castor bean containing a catalog object, or null
            private Set<Searchoption> neededoptions;    // search options used by this catalog
            private Suppressor supp;    // if given, interface to determine if this result suppresses others

            Result(String msg) {
                this(msg, null, Collections.<Searchoption>emptySet());
            }
            Result(String msg, Object result) {
                this(msg, result, Collections.<Searchoption>emptySet());
            }
            Result(String msg, Object result, Set<Searchoption> neededoptions) {
                this.msg = msg;
                this.result = result;
                this.neededoptions = neededoptions;
            }
            Set<Searchoption> getNeededOptions() {
                return this.neededoptions;
            }
            Object getResult() {
                return this.result;
            }
            // an HTML message
            String getMessage() {
                return this.msg;
            }
            void setSuppressor(Suppressor supp) {
                this.supp = supp;
            }
            Suppressor getSuppressor() {
                return this.supp;
            }

            static interface Suppressor {
                public boolean suppresses(Result other);
            }
        }

        /**
         * Suppress any bookmarklets that have the given URL within their
         * URL.
         *
         * Useful for proper catalogs when suppressing also-detected bookmarklets.
         */
        static class BookmarkletSuppressor implements Result.Suppressor {
            private String url;
            BookmarkletSuppressor(String url) {
                this.url = url;
            }

            public boolean suppresses(Result other) {
                if (!(other.getResult() instanceof Bookmarklet))
                    return false;

                try {
                    String bookmarkUrl = (String) Utils.getBeanProperty(other.getResult(), "url");
                    if (bookmarkUrl.indexOf(url) != -1) {
                        if (Config.verbose) {
                            Utils.printLog("suppressing bookmarklet at %s", bookmarkUrl);
                        }
                        return true;
                    }
                } catch (Exception e) {
                    Utils.logUnexpectedException(e);
                }

                return false;
            }
        }

        /**
         * Probe a given URL, add found resources to ProbeContext
         */
        public void probe(ProbeContext context, String url) throws Exception;
    }

    /**
     * URL from which to retrieve OCLC profiles.
     */
    public static String worldcatProfileURLFormat 
    = "http://worldcat.org/webservices/registry/content/Institutions/%s";

    /**
     * Retrieve a list of opacBaseUrls from OCLC's institution repository,
     * based on the institution id. 
     *
     * Starts retrieval in separate thread and returns a future to the result.
     */
    static Future<ArrayList<String>> getOpacBaseListFromOCLCInstitutionRepository(final String institutionId) {
        // many entries contain a "0" institution id, which is not valid.
        // don't probe those.
        if ("0".equals(institutionId))
            return null;

        FutureTask<ArrayList<String>> findOpac = new FutureTask<ArrayList<String>>(new Callable<ArrayList<String>>() {
            ArrayList<String> findOpacBase(String institutionId) {
                ArrayList<String> l = new ArrayList<String>();
                try {
                    /* TBD: rewrite using XOM to remove org.w3c.dom classes, resulting in single XML API use */
                    DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                    URLConnection conn = new URL(String.format(worldcatProfileURLFormat, institutionId)).openConnection();
                    conn.connect();
                    org.w3c.dom.Document doc = db.parse(conn.getInputStream());
                    NodeList n = doc.getElementsByTagName("baseOpacUrl");
                    for (int i = 0; i < n.getLength(); i++) {
                        String opacBase = n.item(i).getTextContent();
                        opacBase = opacBase.replaceFirst("https://", "");
                        opacBase = opacBase.replaceFirst("http://", "");
                        int slash = opacBase.indexOf("/");
                        if (slash != -1) {
                            opacBase = opacBase.substring(0, slash);
                        }
                        opacBase = opacBase.trim();
                        if (!"".equals(opacBase))
                            l.add(opacBase);
                    }
                } catch (Exception ex) {
                    Utils.printLog("findOpacBase failed for %s", institutionId);
                    Utils.logUnexpectedException(ex);
                }
                return l;
            }

            @Override
            public ArrayList<String> call() {
                /* to guard against multiple invocations, synchronize on uniqueId */
                String uniqueId = institutionId.intern();
                synchronized (uniqueId) {
                    ArrayList<String> l = opacBaseList.get(uniqueId);
                    if (l == null) {
                        l = findOpacBase(uniqueId);
                        if (l.size() > 0) {
                            opacBaseList.put(uniqueId, l);
                            if (Config.verbose) {
                                Utils.printLog("OCLC: %s baseopacurl(s): %s", uniqueId, l);
                            }
                        }
                    }
                    return l;
                }
            }
        });
        new Thread(findOpac).start();
        return findOpac;
    }

    //* cached mappings from OCLC institution id to opac list
    static Hashtable<String, ArrayList<String>> opacBaseList = new Hashtable<String,ArrayList<String>>();

    /**
     * Clear cached mappings from OCLC institution id to opac list.
     * Management function.
     */
    public static void clearOpacBaseListTable() {
        opacBaseList.clear();
    }

    /**
     * Clear cached mappings from OCLC institution id to opac list.
     * Management function.
     */
    public static void dumpOpacBaseListTable() {
        System.out.println(opacBaseList.size() + " entries cached, dumping cache");
        for (Map.Entry<String, ArrayList<String>> e : opacBaseList.entrySet()) 
            System.out.println(e.getKey() + ": " + e.getValue());
    }

    static CatalogProbe databaseProbe = new CatalogProbe() {
        public void probe(final ProbeContext context, String searchterm) {
            // System.out.println("Probing database for: " + searchterm);
            try {
                DbUtils.doSqlQueryStatement("SELECT editionInfo.editionId, name, xml, type, shortDesc" 
                        + " FROM editionInfo, catalogInfo" 
                        + " WHERE editionInfo.editionId = catalogInfo.editionId"
                        + " AND (url like ? OR name like ?) LIMIT 0, 10",
                        new DbUtils.ResultSetAction() {
                            public void execute(ResultSet srs) throws SQLException {
                                String editionId = srs.getString(1);
                                String name = srs.getString(2);
                                String xml = srs.getString(3);
                                String type = srs.getString(4);
                                String editionDesc = srs.getString(5);
                                prepareCatalogForImport(context, name, xml, type, editionId, editionDesc);
                            }
                        }, "%" + searchterm + "%", "%" + searchterm + "%"); 
            } catch (Exception e) {
                // exception during catalog probe - don't propagate to user, just log
                Utils.logUnexpectedException(e);
            }
        }

        /**
         * Prepare a catalog from catalog database for import.
         * Retrieves any additional searchoptions from source edition if needed.
         */
        private void prepareCatalogForImport(ProbeContext context, String name, String xml, String type, String editionId, String editionDesc) {
            try {
                Object catalog = Unmarshaller.unmarshal(
                        Class.forName("org.libx.xml." 
                                + Character.toUpperCase(type.charAt(0))
                                + type.substring(1)),
                                new StringReader(xml));

                Set<Searchoption> additionalOptionsNeeded = new HashSet<Searchoption>();
                String options = (String)Utils.getBeanProperty(catalog, "options");
                String [] codes = options.split(";");

                // legacy editions that are not live may also have entries in catalog
                // database.  Use the highest rev number for them
                int revNumber = EditionCache.getLiveRevisionNumber(editionId);
                if (revNumber == -1) {
                    List<Integer> revisions = Model.getRevisions(editionId);
                    if (revisions.size() == 0) {
                        Utils.printLog("Warning: could not prepare edition %s for import - no revisions found", 
                                editionId);
                        return;
                    }
                    revNumber = revisions.get(revisions.size() - 1);
                }
                Model sourceModel = new Model(editionId, revNumber, Model.Autosave.FALSE);
                for (String code : codes) {
                    for (Searchoption so : sourceModel.getEdition().getSearchoptions().getSearchoption()) {
                        if (so.getValue().equals(code)) {
                            additionalOptionsNeeded.add(so);
                            break;
                        }
                    }
                }
                // TBD later, import image if catalog has one.
                Utils.clearBeanProperty(catalog, "image", String.class);
                CatalogProbe.Result r = new CatalogProbe.Result("Found catalog '" 
                        + name + "' in database (from edition '" + editionDesc + "'/" 
                        + editionId + ")", 
                        catalog, additionalOptionsNeeded);
                context.addResult(r);
            } catch (org.exolab.castor.xml.XMLException me) {
                Utils.printLog("Warning: exception while unmarshaling: %s\n%s\n", xml, me);
            } catch (ClassNotFoundException ce) {
                Utils.printLog("Warning: catalog type not found for: %s\n%s\n", xml, ce);
            } catch (Exception otherexc) {
                Utils.logUnexpectedException(otherexc);
            }
        }
    };

    public static String testSearchTerm = "here+is+where+the+users+search+terms+would+be";

    /** 
     * For OpenSearch in general, see 
     * http://www.opensearch.org/Specifications/OpenSearch/1.1/Draft_3#The_.22Url.22_element
     *
     * For Referrer extension (we replace {referrer:source?}), see
     * http://www.opensearch.org/Specifications/OpenSearch/Extensions/Referrer/1.0/Draft_1#The_.22source.22_parameter
     */
    public static String OPENSEARCHDESCRIPTION_REFERRER_SOURCE = "libx.org";

    /*
     * Interpret an Open Search Description 
     * See http://opensearch.org for full spec.
     */
    static class OpenSearchProbe implements CatalogProbe {
        private String defaultTitle;    // title to use in produced bookmarklet if not <ShortName> is found.
        OpenSearchProbe(String defaultTitle) {
            this.defaultTitle = defaultTitle;
        }

        /* url here refers to an XML file containing an OpenSearchDescription
         * see http://addison.vt.edu/screens/opensearch.xml for an example
         */
        public void probe(ProbeContext pcontext, String url) throws Exception {
            URL u = new URL(url);

            URLConnection uc = u.openConnection();
            XMLReader saxparser = XMLReaderFactory.createXMLReader(
                    "org.apache.xerces.parsers.SAXParser"
            );
            Builder parser = new Builder(saxparser);
            try {
                Document doc = parser.build(uc.getInputStream());

                XPathContext context = new XPathContext("a9", "http://a9.com/-/spec/opensearch/1.1/");
                String urltemplate = null;
                Nodes urlNode = doc.query("/a9:OpenSearchDescription/a9:Url", context);

                // find Url that returns text/html type
                for (int i = 0; i < urlNode.size(); i++) {
                    Element e = (Element)urlNode.get(i);
                    if ("text/html".equals(e.getAttribute("type").getValue())) {
                        urltemplate = e.getAttribute("template").getValue();
                        break;
                    }
                }
                if (urltemplate == null)
                    throw new Exception("opensearch description does not contain <Url> with type='text/html'");

                Bookmarklet b = new Bookmarklet();
                b.setOptions("Y");
                b.setUrl(urltemplate.replace("{searchTerms}", "%Y")
                        .replace("{referrer:source?}", OPENSEARCHDESCRIPTION_REFERRER_SOURCE)
                        .replace("{startPage?}", "")
                        .replace("{startIndex?}", "")
                        .replace("{count?}", "")
                        .replace("{language?}", "")
                        .replace(" ", "%20"));

                String shortName = defaultTitle;
                Nodes shortNameNode = doc.query("/a9:OpenSearchDescription/a9:ShortName", context);
                if (shortNameNode.size() > 0) {
                    String s = shortNameNode.get(0).getValue().trim();
                    if (!"".equals(s)) {
                        shortName = s;
                    } else {
                        Nodes descNode = doc.query("/a9:OpenSearchDescription/a9:Description", context);
                        if (descNode.size() > 0) {
                            shortName = descNode.get(0).getValue().trim();
                        }
                    }
                }
                b.setName(shortName);

                Nodes imageNode = doc.query("/a9:OpenSearchDescription/a9:Image", context);
                if (imageNode.size() > 0)
                    b.setImage(imageNode.get(0).getValue());

                pcontext.addResult(new CatalogProbe.Result("Found OpenSearchDescription <a target=\"_new\" href=\"" 
                        + url + "\">'" + b.getName() + "'</a>"
                        + makeImageLinkFromCatalogBean(b)
                        + " <a target=\"_new\" href=\"" + b.getUrl().replace("%Y", testSearchTerm) 
                        + "\">(Click to test)</a>", b));
            } catch (Exception npe) {
                // if anything went wrong, ignore this entry, but log for examination
                Utils.printLog("error parsing OpenSearchDescription at: %s", uc.getURL().toString());
                Utils.logUnexpectedException(npe);
            }
        }
    };

    /*
     * Create <img> from catalog bean if catalog has .image property
     *
     * @return valid html or ""
     */ 
    private static String makeImageLinkFromCatalogBean(Object catalog) {
        String imagelink = "";
        try {
            String image = (String)Utils.getBeanProperty(catalog, "image");
            if (image != null) {
                imagelink = " <img height=\"16px\" src=\"" + image + "\" />";
            }
        } catch (Exception e) {
            Utils.logUnexpectedException(e);
        }
        return imagelink;
    }

    /* break URL for display (long strings with no spaces tend to 
     * cause overflows/distortions in the layout) */
    static int breakIfMoreChars = 30;
    static String breakURL(String url) {
        if (url.length() < breakIfMoreChars) {
            return url;
        }
        StringBuffer nurl = new StringBuffer();
        int cnt = 0;
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            nurl.append(c);
            if ("/?&".indexOf(c) != -1 && cnt++ == 3) {
                nurl.append(' ');
                cnt = 0;
            }
        }
        return nurl.toString();
    }

    private static void reportDetectedCatalog(ProbeContext context, String cattype, String title, String url, Object catalog) {
        reportDetectedCatalog(context, cattype, title, url, catalog, Collections.<Searchoption>emptySet());
    }

    private static void reportDetectedCatalog(ProbeContext context, String cattype, String title, String url, Object catalog, Set<Searchoption> additionalOptions) {
        url = breakURL(url);
        CatalogProbe.Result result = new CatalogProbe.Result("Found " + cattype + " catalog '" + title + "' at " 
                                + url + makeImageLinkFromCatalogBean(catalog), catalog, additionalOptions);
        try {
            String catalogUrl = (String) Utils.getBeanProperty(catalog, "url");
            result.setSuppressor(new CatalogProbe.BookmarkletSuppressor(catalogUrl));
        } catch (Exception e) {
            Utils.logUnexpectedException(e);
        }

        context.addResult(result);
    }

    /* Plugins must implement "examine" */
    interface Plugin {
        public void examine(ProbeContext context, Document doc, String startURL, String pageContent, URLConnection conn) throws Exception;
    }

    // detect Polaris
    // A Polaris catalog redirects to 
    // http://catalog.onlib.org/polaris/Search/default.aspx?ctx=1.1033.0.0.4
    //
    static Pattern polarisUrlConfiguration = Pattern.compile("(.*)/polaris/Search/default.aspx\\?ctx=(.+)", Pattern.CASE_INSENSITIVE);
    static Plugin polarisPlugin = new Plugin() {
        {
            pluginVector.insertElementAt(this, 0);
        }

        public void examine(ProbeContext context, Document doc, String url, String slurpedUrl, URLConnection conn) throws Exception {  
            URL headerUrl = conn.getURL();

            Matcher m = polarisUrlConfiguration.matcher(headerUrl.toString());
            if (m.find()) {
                String catalogUrl = m.group(1);
                String ctx = m.group(2);

                Polaris p = new Polaris();
                CatalogsTabController.setDefaults(p);
                p.setUrl(catalogUrl);
                p.setCtx(ctx);
                String title = getTitleFromDoc(doc);
                p.setName(title);     

                String image = getShortcutIconFromDoc(doc);
                if (image != null)
                    p.setImage(image);

                reportDetectedCatalog(context, "Polaris", title, catalogUrl, p);
            }
        }
    };

    // detect Evergreen
    // An evergreen catalog redirects to 
    // http://islandpines.roblib.upei.ca/opac/en-US/skin/roblib/xml/index.xml?ol=UPEI
    static Pattern everGreenUrlConfiguration = Pattern.compile("(.*)/opac/([^/]*)/skin/([^/]*)/xml/index.xml(\\?ol=(.+))?");
    static Plugin evergreenPlugin = new Plugin() {
        {
            pluginVector.insertElementAt(this, 0);
        }

        public void examine(ProbeContext context, Document doc, String url, String slurpedUrl, URLConnection conn) throws Exception {  
            URL headerUrl = conn.getURL();

            Matcher m = everGreenUrlConfiguration.matcher(headerUrl.toString());
            if (m.find()) {
                String catalogUrl = m.group(1);
                String locale = m.group(2);
                String skin = m.group(3);
                String scope = null;
                if (m.groupCount() >= 5)
                    scope = m.group(5);

                Evergreen e = new Evergreen();
                CatalogsTabController.setDefaults(e);
                e.setUrl(catalogUrl);
                String title = getTitleFromDoc(doc);
                e.setName(title);     
                e.setLocale(locale);
                e.setSkin(skin);
                if (scope != null)
                    e.setScope(scope);

                String image = getShortcutIconFromDoc(doc);
                if (image != null)
                    e.setImage(image);

                reportDetectedCatalog(context, "Evergreen", title, catalogUrl, e);
            }
        }
    };

    // capture path to Sirsi system, which can be /uhtbin/cgisirsi or /uhtbin/cgisirsi.exe
    static Pattern sirsiUrlConfiguration = Pattern.compile("(.*)(/uhtbin/cgisirsi[^/]*)/");
    static String sirsiPath = "%s/x/0/0/5";
    static Plugin sirsiPlugin = new Plugin() {

        {
            pluginVector.insertElementAt(this,0);
        }

        public void examine(ProbeContext context, Document doc, String url, String slurpedUrl, URLConnection conn) throws Exception {  
            URL headerUrl = conn.getURL();

            // see sirsi.evms.edu for example: these place a link to "WebCat" on
            // their home page.  Schedule the link target for examination.
            Nodes nodes = doc.query("//html:a[contains(@href,'webcat')]", htmlContext);
            if (nodes.size() > 0) {
                Element webcatLink = (Element)nodes.get(0);
                String u = computeEffectiveUrl(doc, webcatLink.getAttribute("href").getValue());
                scheduleForProbe(u);
                return;
            }

            Matcher m = sirsiUrlConfiguration.matcher(headerUrl.toString());
            if (m.find()) {
                Sirsi s = new Sirsi();
                CatalogsTabController.setDefaults(s);
                s.setUrl(m.group(1));
                String title = getTitleFromDoc(doc);
                s.setName(title);     
                s.setPath(String.format(sirsiPath, m.group(2)));
                String image = getShortcutIconFromDoc(doc);
                if (image != null)
                    s.setImage(image);
                reportDetectedCatalog(context, "Sirsi", title, url, s);
            }
        }
    };

    // http://catalog.volusialibrary.org/vcplvw/StartBody.csp?SearchMethod=Find_1&Profile=VCPL&OpacLanguage=eng&EncodedRequest=*D3*8D*40*2Fpy*C2*7D*5C*DBW*F3*C8*F3*CE*CB&BorrowerId=&SearchT1=&RowRepeat=0&CSPCHD=00010002000132pbdT4k003559915182
    static Pattern vubisUrlPattern = Pattern.compile("(http://.*)(/\\w*)/StartBody.csp\\?(.*)");
    static Pattern vubisSearchMethodPattern = Pattern.compile("SearchMethod=([^&]+)(&|$)");
    static Pattern vubisOpacLanguagePattern = Pattern.compile("OpacLanguage=([^&]+)(&|$)");
    static Pattern vubisProfilePattern = Pattern.compile("Profile=([^&]+)(&|$)");
    static Plugin vubisPlugin = new Plugin() {
        {
            pluginVector.insertElementAt(this,0);
        }
        public void examine(ProbeContext context, Document doc, final String originalUrl, String pageContent, URLConnection conn) throws Exception {
            String url = conn.getURL().toString();
            Matcher m = vubisUrlPattern.matcher(url);
            if (!m.find())
                return;

            String host = m.group(1);
            String path = m.group(2);
            String rest = m.group(3);
            Vubis v = new Vubis();
            CatalogsTabController.setDefaults(v);
            v.setUrl(host);
            v.setName(getTitleFromDoc(doc).replace("Search ", ""));
            v.setPath(path);
            m = vubisSearchMethodPattern.matcher(rest);
            if (m.find())
                v.setSearchmethod(m.group(1));

            m = vubisOpacLanguagePattern.matcher(rest);
            if (m.find())
                v.setOpaclanguage(m.group(1));

            m = vubisProfilePattern.matcher(rest);
            if (m.find())
                v.setProfile(m.group(1));

            reportDetectedCatalog(context, "Vubis", v.getName(), v.getUrl() + v.getPath(), v);
        }
    };

    static Plugin worldCatPlugin = new Plugin() {
        {
            pluginVector.insertElementAt(this,0);
        }

        public void examine(ProbeContext context, Document doc, final String originalUrl, String pageContent, URLConnection conn) throws Exception {
            /*
             * Worldcat appears to be like this:
             */
            Nodes worldcatBody = doc.query("//html:body[@id='worldcat']", htmlContext);
            if (worldcatBody.size() < 1)
                return;

            Nodes forms = doc.query("//html:form[.//html:input[@type='hidden' and @name='qt']]", htmlContext);
            for (int i = 0; i < forms.size(); i++) {
                Element form = (Element)forms.get(i);
                Nodes qt = form.query(".//html:input[@type = 'hidden' and @name = 'qt']", htmlContext);
                String qtValue = ((Element)qt.get(0)).getAttribute("value").getValue();

                Nodes submitButtons = form.query(".//html:input[@type = 'submit']", htmlContext);
                // submitLabel is typically "Search" or "Search something"
                String submitLabel = ((Element)submitButtons.get(0)).getAttribute("value").getValue();
                submitLabel = submitLabel.replace("Search", "Search " + doc.getBaseURI().replace("http://", ""));
                Worldcat wc = new Worldcat();
                CatalogsTabController.setDefaults(wc);
                wc.setName(submitLabel);
                wc.setUrl(originalUrl);
                wc.setQt(qtValue);
                String image = getShortcutIconFromDoc(doc);
                if (image != null)
                    wc.setImage(image);
                reportDetectedCatalog(context, "Worldcat Local", wc.getName(), originalUrl, wc);
            }
        }
    };

    /**
     * SFX Version 3 - we create an appropriate %JOIN-based bookmarklet
     */
    static Plugin sfxV3List = new Plugin() {
        {
            pluginVector.insertElementAt(this, 0);
        }

        public void examine(ProbeContext context, Document doc, final String originalUrl, String pageContent, URLConnection conn) throws Exception {
            /*
             * Example: http://sfx.biblio.polymtl.ca:3210/sfxlcl3/az
             */
            Nodes forms = doc.query("//html:script[contains(@src, 'sfxlcl3/js/azlist_ver3')]", 
                                    htmlContext);
            if (forms.size() < 1)
                return;

            // TBD: Option values could also be found in these input elements:
            // <input class="radio" name="param_textSearchType_value" 
            // id="contains" value="contains" type="radio">
            // Local option labels may be found using <label for=...>
            Set<Searchoption> additionalOptions = new HashSet<Searchoption>();
            String [] optionCodes = new String [] {
                "contains", "startsWith", "exactMatch"
            };
            String [] optionLabels = new String [] {
                "Contains", "Starts With", "Exact Title"
            };
            String param_pattern_value = "%JOIN{ }";
            String param_textSearchType_value = "%JOIN{ }";
            for (int i = 0; i < optionCodes.length; i++) {
                Searchoption so = new Searchoption();
                so.setLabel(optionLabels[i]);
                so.setValue(optionCodes[i]);
                additionalOptions.add(so);
                param_pattern_value += "{" + optionCodes[i] + "|%" + optionCodes[i] + "}";
                param_textSearchType_value += "{" + optionCodes[i] + "|" + optionCodes[i] + "}";
            }
            String options = Utils.Strings.join(Arrays.<String>asList(optionCodes), ";");

            String url = conn.getURL().toString() 
                + "?param_jumpToPage_value=&param_pattern_value=" + param_pattern_value
                + "&param_textSearchType_value=" + param_textSearchType_value
                + "&param_chinese_checkbox_value=0";

            Bookmarklet b = new Bookmarklet();
            b.setOptions(options);
            b.setUrl(url);
            b.setName(getTitleFromDoc(doc));
            String image = getShortcutIconFromDoc(doc);
            if (image != null)
                b.setImage(image);

            context.addResult(
                new CatalogProbe.Result("Found SFX A-Z List Version 3 '" + b.getName() + "' at " + originalUrl + makeImageLinkFromCatalogBean(b), b, additionalOptions)
            );
        }
    };

    /**
     * Summon - we create an appropriate %JOIN-based bookmarklet
     */
    static Plugin summonPlugin = new Plugin() {
        {
            pluginVector.insertElementAt(this,0);
        }

        public void examine(ProbeContext context, Document doc, final String originalUrl, String pageContent, URLConnection conn) throws Exception {
            /*
             * Example: http://umkc.summon.serialssolutions.com
             */
            String verifyUrl = conn.getURL().toString();
            if (!(verifyUrl.endsWith("summon.serialssolutions.com")))
                return;

            Set<Searchoption> additionalOptions = new HashSet<Searchoption>();
            String [] optionCodes = new String [] {
                "Y", "a", "t", "jt", "v", "isu"
            };
            String[] optionLabels = new String[] {
                "Keyword", "Author", "Title", "Journal Title", "Volume", "Issue"
            };
            for (int i=0;i<optionCodes.length;i++) {
                Searchoption so = new Searchoption();
                so.setLabel(optionLabels[i]);
                so.setValue(optionCodes[i]);
                additionalOptions.add(so);
            }

            String options = Utils.Strings.join(Arrays.<String>asList(optionCodes), ";");
            String url = verifyUrl + "/search?" + "%JOIN{&}{Y|s.q=%Y}{a|s.st.AuthorCombined=%a}{t|s.st.title=%t}{jt|s.st.publicationtitle=%jt}{v|s.st.volume=%v}{isu|s.st.issue=%isu}";
            Bookmarklet b = new Bookmarklet();
            b.setOptions(options);
            b.setUrl(url);
            b.setName(getTitleFromDoc(doc));
            String image = getShortcutIconFromDoc(doc);
            if (image != null)
                b.setImage(image);

            reportDetectedCatalog(context, "Summon", b.getName(), originalUrl, b, additionalOptions);
        }
    };

    static Plugin vufindPlugin = new Plugin() {
        HashMap<String, String> option2Code = new HashMap<String,String>();
        {
            pluginVector.insertElementAt(this,0);
            option2Code.put("all", "Y");
            option2Code.put("title", "t");
            option2Code.put("author", "a");
            option2Code.put("subject", "d");
            option2Code.put("isn", "i");
        }

        public void examine(ProbeContext context, Document doc, final String originalUrl, String pageContent, URLConnection conn) throws Exception {
            /* Need to create
                http://plus.mnpals.net/vufind/Search/Home?type=%JOIN{}{Y|all}{t|title}{a|author}{d|subject}{i|isn}{is|isn}&library=GAC&lookfor=%JOIN{}{Y|%Y}{t|%t}{a|%a}{d|%d}{i|%i}{is|%is}&sort=&submit=Find
            */
            Nodes vufindIcon = doc.query("//html:link[@rel = 'icon' and starts-with(@href, '/vufind/')]", htmlContext);
            if (vufindIcon.size() < 1)
                return;

            Nodes forms = doc.query("//html:form[@action='/vufind/Search/Home']", htmlContext);
            System.out.println("vufind forms found " + forms.size());
            if (forms.size() < 1)
                return;

            Element form = (Element)forms.get(0);
            Element libraryComp = (Element)form.query(".//html:input[@name='library']", htmlContext).get(0);
            Element sortComp = (Element)form.query(".//html:input[@name='sort']", htmlContext).get(0);
            Nodes selectType = form.query(".//html:select[@name='type']/html:option", htmlContext);

            String options = "";
            String typeComp = "type=%JOIN{}";
            String lookforComp = "lookfor=%JOIN{}";

            if (selectType.size() == 0) {
                options = "Y;a;t;d;i";
                typeComp += "{Y|all}{t|title}{a|author}{d|subject}{i|isn}{is|isn}";
                lookforComp += "{Y|%Y}{t|%t}{a|%a}{d|%d}{i|%i}{is|%is}";
            } else {
                for (int i = 0; i < selectType.size(); i++) {
                    Element opt = (Element)selectType.get(i);
                    String value = opt.getAttribute("value").getValue();
                    String code = option2Code.get(value);
                    if (code != null) {
                        if (!"".equals(options))
                            options += ";" + code;
                        else
                            options = code;

                        typeComp += "{" + code + "|" + value + "}";
                        lookforComp += "{" + code + "|%" + code + "}";

                        // ISSN special case
                        if (value.equals("isn")) {
                            typeComp += "{is|isn}";
                            lookforComp += "{is|%is}";
                        }
                    }
                }
            }

            Attribute action = form.getAttribute("action");
            String actionTargetURL = getActionTarget(doc, action);
            String url = String.format("%s?%s&library=%s&%s&sort=%s&submit=Find",
                    actionTargetURL,
                    typeComp,
                    libraryComp.getAttribute("value").getValue(),
                    lookforComp,
                    sortComp.getAttribute("value").getValue());

            /* if an opensearchdescription is included, its title is often a good choice for the catalog title */
            String title = getTitleFromDoc(doc);
            Nodes osd = doc.query("//html:link[@rel='search' and @type='application/opensearchdescription+xml']", htmlContext);
            if (osd.size() >= 0) {
                Element osd0 = (Element)osd.get(0);
                title = osd0.getAttribute("title").getValue();
            }

            Bookmarklet b = new Bookmarklet();
            b.setOptions(options);
            b.setUrl(url);
            b.setName(title);
            String image = getShortcutIconFromDoc(doc);
            if (image != null)
                b.setImage(image);

            reportDetectedCatalog(context, "VuFind", b.getName(), originalUrl, b);
        }
    };

    static Plugin endecaPlugin = new Plugin() {
        HashMap<String, String> option2Code = new HashMap<String,String>();
        {
            pluginVector.insertElementAt(this,0);
            option2Code.put("Call Number", "c");
            option2Code.put("ISBN", "i");
            option2Code.put("Number", "i");
            option2Code.put("Subject", "d");
            option2Code.put("Author", "a");
            option2Code.put("JTitle", "jt");
            option2Code.put("Keyword", "Y");
            option2Code.put("Title", "t");
        }

        public void examine(ProbeContext context, Document doc, final String originalUrl, String pageContent, URLConnection conn) throws Exception {
            /*
             * Endeca appears to be like this:
             * Form #1
             *  .action=null
             *  .method=[nu.xom.Attribute: method="get"]
             *  localname=input .type=text .class=catalogSearchBarFormElement .id=box .name=Ntt .value=
             *  localname=input .type=hidden .id=filter .name=N .value=27
             *  localname=input .type=hidden .id=dym .name=Nty .value=1
             *  localname=input .type=hidden .name=S .value=21KUK4V6LC4DVCHDIHK3BRMBC23YB5CD8X3T6K33J1M1M9NRAS
             *  localname=input .type=submit .class=catalogSearchBarFormElement3 .value=Search
             *
             * For which we'd create something like this:
             * <bookmarklet options="Y;d;jt;c;a;i;t" url="http://fiu.catalog.fcla.edu/fi.jsp?N=27&Nty=1&Ntt=%term1&Ntk=%SWITCH{%type1}{t:Title}{Y:Keyword}{jt:JTitle}{a:Author}{d:Subject}{i:Number}{c:Call+Number}" name="Florida International University Library">
             */
            Nodes forms = doc.query("//html:form[.//html:input[@name='Ntt'] " 
                                          + "and .//html:input[@name='Nty'] "
                                          + "and .//html:input[@name='N']]", htmlContext);
            if (forms.size() < 1)
                return;

            Element form = (Element)forms.get(0);
            Element inputNtt = (Element)form.query(".//html:input[@name='Ntt']", htmlContext).get(0);
            Element inputN = (Element)form.query(".//html:input[@name='N']", htmlContext).get(0);
            Element inputNty = (Element)form.query(".//html:input[@name='Nty']", htmlContext).get(0);
            Nodes selectNtk = form.query(".//html:select[@name='Ntk']/html:option", htmlContext);

            String options = "";
            String switchStr = "";

            if (selectNtk.size() == 0) {
                options = "Y;d;jt;c;a;i;t";
                switchStr = "{t:Title}{Y:Keyword}{jt:JTitle}{a:Author}{d:Subject}{i:Number}{is|Number}{c:Call+Number}";
            }
            for (int i = 0; i < selectNtk.size(); i++) {
                Element opt = (Element)selectNtk.get(i);
                String value = opt.getAttribute("value").getValue();
                String code = option2Code.get(value);
                if (code != null) {
                    if (!"".equals(options))
                        options += ";" + code;
                    else
                        options = code;
                    switchStr += "{" + code + ":" + value.replaceAll(" ", "+") + "}";
                }
            }

            String url = String.format("%s?N=%s&Nty=%s&Ntt=%%term1&Ntk=%%SWITCH{%%type1}"+switchStr,
                    doc.getBaseURI(),
                    inputN.getAttribute("value").getValue(),
                    inputNty.getAttribute("value").getValue(),
                    inputNtt.getAttribute("value").getValue());

            Bookmarklet b = new Bookmarklet();
            b.setOptions(options);
            b.setUrl(url);
            b.setName(getTitleFromDoc(doc));
            String image = getShortcutIconFromDoc(doc);
            if (image != null)
                b.setImage(image);

            reportDetectedCatalog(context, "Endeca", b.getName(), originalUrl, b);
        }
    };

    /* Helper interface to check if a given URL matches some filter. */
    interface UrlFilter {
        /* Return true if 'url' passes filter test. */
        public boolean accept(String url);
    }

    // XXX should capture base URL here
    static Pattern voyagerPattern = Pattern.compile("/cgi-bin/Pwebrecon\\.cgi");
    static Plugin voyagerPlugin = new Plugin() {

        {
            pluginVector.insertElementAt(this,0);
        }

        public void examine(ProbeContext context, Document doc, final String originalUrl, String slurpedUrl, URLConnection conn) throws Exception {

            /* 
             * Note: only looking at main URL does not detect http://madcat.library.wisc.edu/
             * even though http://madcat.library.wisc.edu/cgi-bin/Pwebrecon.cgi
             * has a functional Voyager.  This can only be fixed by probing directly at second URL.
             * We should do this only if no catalogs were found at all.
             */
            boolean foundwebrecon = false;

            URL headerUrl = conn.getURL();
            String title = getTitleFromDoc(doc);

            UrlFilter voyagerCheckAction = new UrlFilter() {
                public boolean accept(String url) {
                    if (url.startsWith(originalUrl + "cgi-bin/Pwebrecon.cgi"))
                        return true;

                    Matcher mat = voyagerPattern.matcher(url);
                    return mat.find();
                }
            };

            /**
             * Checks if the Url starts with the given pattern
             */
            if (voyagerCheckAction.accept(headerUrl.toString())) {
                foundwebrecon = true;
            }

            /**
             * If Voyagers are not found in the previous case, then the form actions are inspected for the pattern.
             */
            if (!foundwebrecon) {
                foundwebrecon = findFirstMatch(doc, voyagerCheckAction) != null;
            }

            /**
             * Sometimes, Voyager pages contain hyperlinks to other Voyager pages rather than redirects and form actions.
             * Full URL is captured here.
             */
            if (!foundwebrecon) {
                Nodes voyagerLinks = doc.query("//html:a[contains(@href,'Pwebrecon.cgi')]", htmlContext);
                for (int i = 0; i < voyagerLinks.size(); i++) {
                    Element voyagerLink = (Element)voyagerLinks.get(i);
                    String voyagerUrl = computeEffectiveUrl(doc, voyagerLink.getAttribute("href").getValue());
                    scheduleForProbe(voyagerUrl);
                }
            }    

            if (foundwebrecon) {
                Voyager v = new Voyager();
                CatalogsTabController.setDefaults(v);
                v.setUrl(originalUrl);  // XXX may not be original URL
                v.setName(title);
                String image = getShortcutIconFromDoc(doc);
                if (image != null)
                    v.setImage(image);
                reportDetectedCatalog(context, "Voyager", title, originalUrl, v);
            }
        }
    };

    static ThreadLocal<Primo> basicPrimoCatalog = new ThreadLocal<Primo>();
    /**
     * Support for Primo.
     * Two plugins are used for primo autodetection - primoPlugin and advancedPrimoPlugin
     * primoPlugin is used to support primo basic search. This plugin also finds if any links
     * to a primo system are present on a given page. These target links are then scheduled for probe.
     * Information from a primo basic search page is collected and stored in a thread local variable.
     * Then, the advanced search page is scheduled for probe. The advancedPrimoPlugin retrieves basic search
     * information from the thread local variable and combines it with advanced search information.
     */
    static Plugin primoPlugin = new Plugin() {
        HashMap<String, String> option2Code = new HashMap<String,String>();
        {
            pluginVector.insertElementAt(this, 0);
            option2Code.put("any", "Y");
            option2Code.put("creator", "a");
            option2Code.put("title", "t");
            option2Code.put("sub", "d");
            option2Code.put("lsr01", "c");
        }
        Pattern primoPathPattern = Pattern.compile("(/.*)/action/search.do\\?fn=go",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Pattern primoUrlPattern = Pattern.compile("(http://[^/]*)/");
        Pattern primoVidPattern = Pattern.compile("vid=([^&]+)(&|$)", Pattern.CASE_INSENSITIVE);

        public void examine(ProbeContext context, Document doc, final String originalUrl, String slurpedUrl, URLConnection conn) throws Exception {

            Nodes n = doc.query("//html:form[contains(@action,'/action/search.do?fn=go')]", htmlContext);
            if (n.size() == 0) {

                // Check if page contains a link to a primo system.  For
                // example, http://bibliotheque.uqac.ca/ does
                // their home page.  Schedule the link target for examination.
                Nodes primoLinks = doc.query("//html:a[contains(@href,'primo_library/libweb/action/search')]", htmlContext);
                if (primoLinks.size() > 0) {
                    Element primoLink = (Element)primoLinks.get(0);
                    String primoUrl = computeEffectiveUrl(doc, primoLink.getAttribute("href").getValue());
                    System.out.println("found primo link on: " + conn.getURL() + " from " + originalUrl);
                    scheduleForProbe(primoUrl);
                }

                return;
            }

            Element primoForm = (Element)n.get(0);
            Primo v = new Primo();
            CatalogsTabController.setDefaults(v);

            // determine Path
            Matcher m = primoPathPattern.matcher(primoForm.getAttributeValue("action"));
            if (m.find()) {
                v.setPath(m.group(1));
            }

            // determine URL
            m = primoUrlPattern.matcher(doc.getBaseURI());
            if (!m.find()) {
                return;
            }
            v.setUrl(m.group(1));

            // determine VID
            n = primoForm.query("//html:input[@name='vid']", htmlContext);
            if (n.size() > 0) {
                v.setVid(((Element)n.get(0)).getAttributeValue("value"));
            } else {
                m = primoVidPattern.matcher(doc.getBaseURI());
                if (m.find())
                    v.setVid(m.group(1));
            }
            String options = "";

            //Set various fields.
            n = primoForm.query("//html:select[contains(@class, 'EXLSimpleSearchSelect')]", htmlContext);
            for (int i = 0; i < Math.min(3, n.size()); i++) {
                Element e = (Element)n.get(i);
                switch (i) {
                case 0:
                    // material
                    v.setMaterialvar(e.getAttributeValue("name"));
                    v.setDefaultmaterial(getValueAttribute(e, "./html:option[@selected != '']", "all_items"));
                    // collect material choices.
                    // These attributes aren't currently used by the JS implementation
                    Nodes mm = e.query("./html:option", htmlContext);
                    StringBuilder mc = new StringBuilder();
                    for (int j = 0; j < mm.size(); j++) {
                        Element eo = (Element)mm.get(j);
                        String key = eo.getAttributeValue("value");
                        String label = eo.getValue().trim();
                        mc.append("(" + key + ";" + label + ")");
                    }
                    v.setMaterialchoices(mc.toString());
                    break;
                case 1:
                    // search mode has two types - exact and contains. Default is set to "contains".
                    v.setSearchmodevar(e.getAttributeValue("name"));
                    v.setDefaultsearchmode(getValueAttribute(e, "./html:option[@selected != '']", "contains"));
                    break;
                case 2:
                    // author, subject, etc. are the various types of search choices
                    v.setSearchvar(e.getAttributeValue("name"));
                    v.setDefaultsearch(getValueAttribute(e, "./html:option[@selected != '']", "any"));
                    // collect search choices. These are also not used by the JS implementation
                    // A hash map is used. Primo options are set to the corresponding options in hash map
                    mm = e.query("./html:option", htmlContext);
                    mc = new StringBuilder();
                    for (int j = 0; j < mm.size(); j++) {
                        Element eo = (Element)mm.get(j);
                        String key = eo.getAttributeValue("value");
                        String label = eo.getValue().trim();
                        String code = option2Code.get(label);
                        if (code != null) {
                            if (!"".equals(options))
                                options += ";" + code;
                            else
                                options = code;
                        }
                        mc.append("(" + key + ";" + label + ")");
                    }
                    v.setSearchchoices(mc.toString());
                    break;
                }
                if ("".equals(options)) {
                    options = "Y;a;t;d;c";
                }
                v.setOptions(options);
            }
            // scps determines a list of search location limitations for the scopes of a search
            //blank means no limitations or all libraries in a consortia installation
            v.setScps(getValueAttribute(doc,
                "//html:select[@name='scp.scps']/html:option[@selected != '']", ""));

            String title = getTitleFromDoc(doc);
            v.setName(title);
            String image = getShortcutIconFromDoc(doc);
            if (image != null)
                v.setImage(image);

            String advancedUrl = doc.getBaseURI().replace("Basic", "Advanced");
            // primo advanced search page is scheduled for probe
            scheduleForProbe(advancedUrl);
            // information of a primo basic search page is stored in a thread local variable
            basicPrimoCatalog.set(v);
        }

        /* Evaluate optionXPathExpr relative to 'e' and return value attribute
         * of first node, if any. Else return defaultValue.
         */
        String getValueAttribute(Element e, String optionXPathExpr, String defaultValue) {
            return getValueAttribute(e.query(optionXPathExpr, htmlContext), defaultValue);
        }

        String getValueAttribute(Document d, String optionXPathExpr, String defaultValue) {
            return getValueAttribute(d.query(optionXPathExpr, htmlContext), defaultValue);
        }

        /* Return first's node 'value' attribute if options is non-empty, else
         * return 'defaultValue'
         */
        private String getValueAttribute(Nodes options, String defaultValue) {
            if (options.size() > 0) {
                defaultValue = ((Element)options.get(0)).getAttributeValue("value");
            }
            return defaultValue;
        }
    };

    static Plugin advancedPrimoPlugin = new Plugin() {
        HashMap<String, String> option2Code = new HashMap<String,String>();
        {
            pluginVector.insertElementAt(this,0);
            option2Code.put("isbn", "i");
        }

        public void examine(org.libx.editionbuilder.CatalogDetector.ProbeContext context, Document doc, String startURL, String pageContent, URLConnection conn) throws Exception {
            // information of a primo basic search page is retrieved from a thread local variable
            Primo v = basicPrimoCatalog.get();
            if (v == null)
                return;

            Nodes n = doc.query("//html:form[contains(@action,'/action/search.do?fn=go')]", htmlContext);
            if (n.size() == 0)
                return;

            Element primoForm = (Element)n.get(0);
            n = primoForm.query("//html:select[contains(@class, 'EXLSelectTag')]", htmlContext);

            String options = v.getOptions();
            Set<Searchoption> additionalOptions = new HashSet<Searchoption>();
            String [] optionCodes = new String [] {
                "usertag", "publisher"
            };
            String [] optionLabels = new String [] {
                "Usertag", "Publisher"
            };
            for (int i = 0; i < optionCodes.length; i++) {
                Searchoption so = new Searchoption();
                so.setLabel(optionLabels[i]);
                so.setValue(optionCodes[i]);
                additionalOptions.add(so);
            }
            options = options + ";" + Utils.Strings.join(Arrays.<String>asList(optionCodes), ";");

            for (int i = 0; i < Math.min(7, n.size()); i++) {
                Element e = (Element)n.get(i);
                switch (i) {
                case 0:
                    // advanced search variable1
                    v.setAdvsearchvar1(e.getAttributeValue("name"));
                    // collect advanced search choices choices. These are also not used by the JS implementation.
                    // A hash map is used. Primo options are set to the corresponding options in the hash map.
                    Nodes mm = e.query("./html:option", htmlContext);
                    StringBuilder mc = new StringBuilder();
                    for (int j = 0; j < mm.size(); j++) {
                        Element eo = (Element)mm.get(j);
                        String key = eo.getAttributeValue("value");
                        String label = eo.getValue().trim();
                        String code = option2Code.get(label);
                        if (code != null) {
                            if (!"".equals(options))
                                options += ";" + code;
                            else
                                options = code;
                        }
                        mc.append("(" + key + ";" + label + ")");
                    }
                    v.setAdvsearchchoices(mc.toString());
                    break;
                case 1:
                    // advanced search mode1. Default value is the same as that of basic search mode i.e. "contains".
                    v.setAdvsearchmode1(e.getAttributeValue("name"));
                    break;
                case 2:
                    // advanced search variable2
                    v.setAdvsearchvar2(e.getAttributeValue("name"));
                    break;
                case 3:
                    // advanced search mode2. Default value is the same as that of basic search mode i.e "contains". 
                    v.setAdvsearchmode2(e.getAttributeValue("name"));
                    break;
                case 4:
                    // publication date. This attributes isn't currently used by the JS implementation
                    v.setPublicationvar(e.getAttributeValue("name"));
                    v.setDefaultpubdate(getValueAttribute(e, "./html:option[@selected != '']", "all_items"));
                    // collect different ranges of publication dates such as Last year, Last 2 years etc, Last 5 years etc.
                    // These attributes aren't currently used by the JS implementation
                    Nodes nn = e.query("./html:option", htmlContext);
                    StringBuilder nc = new StringBuilder();
                    for (int j = 0; j < nn.size(); j++) {
                        Element eo = (Element)nn.get(j);
                        String key = eo.getAttributeValue("value");
                        String label = eo.getValue().trim();                 
                        nc.append("(" + key + ";" + label + ")");
                    }
                    v.setPublicationdates(nc.toString());
                    break;
                case 5:
                    // setting default advanced material choice to "all_items"
                    v.setAdvmaterialvar(e.getAttributeValue("name"));
                    v.setDefadvmaterial(getValueAttribute(e, "./html:option[@selected != '']", "all_items"));
                    break;
                case 6:
                    // setting language to "all_items" which corresponds to "Any Language"
                    v.setLangvar(e.getAttributeValue("name"));
                    v.setDefaultlanguage(getValueAttribute(e, "./html:option[@selected != '']", "all_items"));
                    // collect different language choices such as English, French, German etc.
                    // These attributes aren't currently used by the JS implementation
                    Nodes no = e.query("./html:option", htmlContext);
                    StringBuilder st = new StringBuilder();
                    for (int j = 0; j < no.size(); j++) {
                        Element eo = (Element)no.get(j);
                        String key = eo.getAttributeValue("value");
                        String label = eo.getValue().trim();                 
                        st.append("(" + key + ";" + label + ")");
                    }
                    v.setLanguagechoices(st.toString());
                    break;
                }

                if ("".equals(options)) {
                    options = "Y;a;t;d;c;i";
                }
                v.setOptions(options);
            }
            reportDetectedCatalog(context, "Primo", v.getName(), startURL, v, additionalOptions);
            basicPrimoCatalog.set(null);
        }

        String getValueAttribute(Element e, String optionXPathExpr, String defaultValue) {
            return getValueAttribute(e.query(optionXPathExpr, htmlContext), defaultValue);
        }

        /* Return first's node 'value' attribute if options is non-empty, else
         * return 'defaultValue'
         */
        private String getValueAttribute(Nodes options, String defaultValue) {
            if (options.size() > 0) {
                defaultValue = ((Element)options.get(0)).getAttributeValue("value");
            }
            return defaultValue;
        }
    };

    static Plugin talisPrismPlugin = new Plugin() {
        {
            pluginVector.insertElementAt(this, 0);
        }
        Pattern talisApplicationHostName = Pattern.compile("^\\s*Application Host Name:\\s*(\\S+)\\s*$", 
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Pattern talisContext = Pattern.compile("^\\s*Context:\\s*(\\S+)\\s*$", 
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        public void examine(ProbeContext context, Document doc, final String originalUrl, String slurpedUrl, URLConnection conn) throws Exception {
            Nodes talisVersion = doc.query("//comment()[contains(.,'TalisJB v2')]", htmlContext);
            if (talisVersion.size() == 0)
                return;

            Nodes talisIfStatus = doc.query("//comment()[contains(.,'INTERFACE STATUS')]", htmlContext);
            if (talisIfStatus.size() == 0) {
                Utils.printLog("%s has talisVersion, but not INTERFACE STATUS", originalUrl);
                return;
            }

            Talisprism v = new Talisprism();
            CatalogsTabController.setDefaults(v);

            String ifStatus = ((Comment)talisIfStatus.get(0)).getValue();
            Matcher m = talisApplicationHostName.matcher(ifStatus);
            if (m.find()) {
                v.setUrl("http://" + m.group(1));
            }
            m = talisContext.matcher(ifStatus);
            if (m.find()) {
                v.setPath(m.group(1));
            }

            v.setLocation(getValueAttribute(doc, 
                    "//html:select[@name='searchLocations']/html:option[@selected != '']", 
                    getValueAttribute(doc, "//html:input[@type = 'hidden' and @name = 'searchLocation']", 
                        "talislms")));

            v.setCollections(getValueAttribute(doc, 
                    "//html:select[@name='searchCollections']/html:option[@selected != '']" , "1"));

            v.setSites(getValueAttribute(doc, 
                    "//html:select[@name='searchSites']/html:option[@selected != '']" , "-1"));

            String title = getTitleFromDoc(doc);
            v.setName(title);
            String image = getShortcutIconFromDoc(doc);
            if (image != null)
                v.setImage(image);
            reportDetectedCatalog(context, "Talis Prism", title, originalUrl, v);
        }

        String getValueAttribute(Document doc, String optionXPathExpr, String defaultValue) {
            Nodes options = doc.query(optionXPathExpr, htmlContext);
            if (options.size() > 0) {
                defaultValue = ((Element)options.get(0)).getAttributeValue("value");
            }
            return defaultValue;
        }
    };

    static Plugin voyager7Plugin = new Plugin() {
        {
            pluginVector.insertElementAt(this, 0);
        }

        public void examine(ProbeContext context, Document doc, final String originalUrl, String slurpedUrl, URLConnection conn) throws Exception {

            String v7Url = null;
            /*
             * http://mauicc.lib.hawaii.edu:7008/vwebv/searchBasic?sk=maui
             *
             * 'sk' is translated into a 'limitTo' - finding correct limitTo must still
             * be implemented.
             */
            CookieManager cookieMan = (CookieManager)CookieHandler.getDefault();
            List<HttpCookie> cookies = cookieMan.getCookieStore().get(conn.getURL().toURI());
            boolean hasV7Cookie = cookies.size() > 0 && "/vwebv".equals(cookies.get(0).getPath());
            if (hasV7Cookie)
                v7Url = computeEffectiveUrl(doc, "/");

            Element form = findFirstMatch(doc, new UrlFilter() {
                public boolean accept(String url) {
                    if (url.startsWith(originalUrl + "/vwebv/search"))
                        return true;
                    return false;
                }         
            });

            if (form != null)
                v7Url = computeEffectiveUrl(doc, "/");

            if (v7Url == null)
                return;

            String title = getTitleFromDoc(doc);
            Voyager7 v = new Voyager7();
            CatalogsTabController.setDefaults(v);
            v.setUrl(v7Url);
            v.setName(title);
            String image = getShortcutIconFromDoc(doc);
            if (image != null)
                v.setImage(image);
            reportDetectedCatalog(context, "Voyager 7", title, originalUrl, v);
        }
    };

    static Plugin alephPlugin = new Plugin() {
        {
            pluginVector.insertElementAt(this,0);
        }

        // this checking function is called for both URLs and bodies.
        protected String checkURL(String text) {
            String localbase=null;

            Matcher m2 = Pattern.compile(".*local_base=([^&\\\"]*).*", 
                    Pattern.CASE_INSENSITIVE).matcher(text);
            if (m2.find()) {
                localbase = m2.group(1);
            } 

            return localbase;
        }


        public void examine(ProbeContext context, Document doc, final String originalUrl, String pageContent, URLConnection conn) throws Exception {
            boolean foundAleph = false;
            String localbase = null;

            final URL headerUrl = conn.getURL();

            UrlFilter alephUrlCheckAction = new UrlFilter() {
              
                Pattern p = Pattern.compile("("+Pattern.quote(originalUrl)+":*(\\d*)/F/)");

                public boolean accept(String url) {

                    boolean ans = false;

                    String prefix = url+"/F/";
                    ans = url.startsWith(prefix);

                    if(!ans) {
                        Matcher match = p.matcher(url);
                        if(match.find()) {
                            ans = true;
                        }
                    }
                    return ans;                    
                }         
            };

            localbase = checkURL(headerUrl.toString());
            String title = getTitleFromDoc(doc); 

            
            if(alephUrlCheckAction.accept(headerUrl.toString())) {
                foundAleph = true;
            }

            if(!foundAleph) {
                foundAleph = findFirstMatch(doc, alephUrlCheckAction) != null;
            }

            if(!foundAleph) {    
                foundAleph = alephUrlCheckAction.accept(pageContent);
            }

            if(foundAleph) {
                Aleph a = new Aleph();
                CatalogsTabController.setDefaults(a);
                a.setUrl(originalUrl);
                a.setLocalbase(localbase);
                a.setName(title);
                String image = getShortcutIconFromDoc(doc);
                if (image != null)
                    a.setImage(image);
                reportDetectedCatalog(context, "Aleph", title, originalUrl, a);
            }
        }
    };

    //Millenium Plugin
    static Plugin milleniumPlugin = new Plugin() {   
        {
            pluginVector.insertElementAt(this,0);
        }  

        public void examine(ProbeContext context, Document doc, String url, String slurpedUrl, URLConnection conn) throws Exception {      
            // Server: III 100
            String server = conn.getHeaderField("Server");
            String title = getTitleFromDoc(doc);
            if (server != null && server.startsWith("III")) {
                Millenium m = new Millenium();
                CatalogsTabController.setDefaults(m);
                m.setUrl(url);
                m.setName(title);

                String image = getShortcutIconFromDoc(doc);
                if (image != null)
                    m.setImage(image);

                reportDetectedCatalog(context, "III Millennium", title, url, m);
            }
        }
    };

    static Plugin horizonPlugin = new Plugin() {
        {
            pluginVector.insertElementAt(this,0);
        } 

        public void examine(ProbeContext context, Document doc, String url, String pageContent, URLConnection conn) throws Exception {
            boolean foundHorizon = false;

            String title="";
            String server = conn.getHeaderField("Server");

            title = getTitleFromDoc(doc);

            // Server must be "Jetty"
            if (server == null || !server.startsWith("Jetty"))
                return;

            final String pattern = url+"/ipac20/ipac.jsp";

            UrlFilter horizonCheckAction = new UrlFilter() {

                public boolean accept(String url) {
                    return url.startsWith(pattern);

                }         
            };


            foundHorizon = findFirstMatch(doc, horizonCheckAction) != null;

            if (foundHorizon) {
                Horizon h = new Horizon();
                CatalogsTabController.setDefaults(h);
                h.setUrl(url);
                h.setName(title);
                String image = getShortcutIconFromDoc(doc);
                if (image != null)
                    h.setImage(image);

                reportDetectedCatalog(context, "Horizon iPac",title, url, h);
            } 
        }
    };

    /**
     * Follow frameset and iframe tags.
     */
    static Plugin frameSetPlugin = new Plugin() {   
        {
            pluginVector.add(this);
        }
        public void examine(ProbeContext context, Document doc, String url, String slurpedUrl, URLConnection conn) throws Exception {
            Nodes frames = doc.query("//html:frameset//html:frame", htmlContext);
            followFrames(doc, frames);
            frames = doc.query("//html:iframe", htmlContext);
            followFrames(doc, frames);
        }
        private void followFrames(Document doc, Nodes frames) {
            for (int i = 0; i < frames.size(); i++) {
                Element frame = (Element)frames.get(i);
                String src = frame.getAttributeValue("src");
                if (src != null) {
                    scheduleForProbe(computeEffectiveUrl(doc, src));
                }
            }
        }
    };

    static Plugin bookmarkletPlugin = new Plugin() {
        {
            pluginVector.add(this);
        }

        class FormField extends HashMap<String, Attribute> {
            private Attribute type;
            private Attribute name;
            private Attribute value;

            FormField(Attribute type, Attribute name, Attribute value) {
                put("type", this.type = type);
                put("name", this.name =name);
                put("value", this.value = value);
            }

            FormField(Element e) {
                for (int i = 0; i < e.getAttributeCount(); i++) {
                   Attribute attr = e.getAttribute(i);
                   put(attr.getLocalName(), attr);
                }
                this.type = get("type");
                this.name = get("name");
                this.value = get("value");
            }
        }

        private void createAndAddBookmarklet(ProbeContext context, Document doc, FormField submitField, List<FormField> formFieldList, Attribute action, String url, boolean formUsesPost) throws Exception {

            try {

                Bookmarklet b = new Bookmarklet();
                CatalogsTabController.setDefaults(b);    
                
                String submitFieldVal = "";
                if (submitField.value != null) {
                    submitFieldVal = submitField.value.getValue();
                } else if (submitField.get("alt") != null) {
                    submitFieldVal = submitField.get("alt").getValue();
                }

      
                String options = "Y";

                String act="";
                act = getActionTarget(doc,action);

                if(!act.startsWith("http://")) {
                    if(act.startsWith("/"))
                        act=url+act;
                    else 
                        act=url+"/"+act;
                } 
                
                // TBD: Why do you strip trailing slashes?  They are significant.
                if(act.endsWith("/"))
                    act = act.substring(0,act.length()-1);
                
                String namePrefix = act.substring(7);
                
                //'7' is because http:// is prepended to each url
                int index = namePrefix.indexOf('/'); 
                
                if(0 <  index && index < namePrefix.length())
                    namePrefix = namePrefix.substring(0,index);
                else
                    namePrefix = act.substring(7);
                
                String name = namePrefix;
                if (!"".equals(submitFieldVal))
                    name += " using '" + submitFieldVal + "'";
                else
                    name += "_" + System.identityHashCode(submitFieldVal);

                String argtemplate = "";

                /**
                 * Constructing the form data set, based on the following guidelines specified by the W3C standard. 
                 * 
                 * A form data set is a sequence of control-name/current-value pairs constructed from successful controls.
                 * A successful control is "valid" for submission. Every successful control has its control name 
                 * paired with its current value as part of the submitted form data set. 
                 * The control names/values are listed in the order they appear in the document. 
                 * The name is separated from the value by `=' and name/value pairs are separated from each other by `&'.
                 */
                for(FormField formField : formFieldList) {
                    if(formField.name == null)
                        formField.name = new Attribute("input","");
                    if(formField.value == null)
                        formField.value = new Attribute("input","");

                    argtemplate += formField.name.getValue()+"="+formField.value.getValue()+"&";
                }

                argtemplate = argtemplate.substring(0,argtemplate.length() - 1);

                if (submitField.name != null && submitField.value != null) {

                    String[] arr = submitField.value.getValue().split(" ");
                    String urlSuffix = "";
                    for (String str : arr) {
                        urlSuffix +=str+"+";
                    }
                    argtemplate += "&"+submitField.name.getValue()+"="+urlSuffix.substring(0,urlSuffix.length() - 1);
                }

                /* if actions contains a jsession id, remove id */
                act = act.replaceFirst(";jsessionid=([0-9ABCDEF]){32}", "");

                b.setName(name);
                if (formUsesPost) {
                    b.setUrl(act);
                    
                    b.setPostdata(argtemplate);
                } else {
                    /**
                     * The w3c standard specifies that the form action and the form data set are separated 
                     * by the separator "?". But some actions contain a ? already, that is they append certain
                     * data already to the url. 
                     */
                    if (act.contains("?")) {     
                        b.setUrl(act+"&"+argtemplate);
                    } else {
                        b.setUrl(act+"?"+argtemplate);
                    }
                }
                b.setOptions(options);
                String image = getShortcutIconFromDoc(doc);
                if (image != null)
                    b.setImage(image);

                context.addResult(new CatalogProbe.Result("Found Bookmarklet catalog '" + name + "' at " + url + makeImageLinkFromCatalogBean(b), b));
            } catch (Exception exc) {
                Utils.logUnexpectedException(exc);
            }
        }

        private Pattern startsWithScheme = Pattern.compile("^([a-z0-9]+):", Pattern.CASE_INSENSITIVE);
        public void examine(ProbeContext context, Document doc, String url, String slurpedUrl, URLConnection conn) throws Exception {
            Nodes forms = doc.query("//html:form", htmlContext);

            for (int i = 0; i < forms.size(); i++) {
                boolean formUsesPost = false;
                List<FormField> formFieldList = new ArrayList<FormField>();
                List<FormField> submitFieldList = new ArrayList<FormField>();
                List<FormField> imageFieldList = new ArrayList<FormField>();

                Element form = (Element)forms.get(i);

                Attribute method = form.getAttribute("method");
                Attribute action = form.getAttribute("action");

                /**
                 * skips all the forms, which have "POST" methods
                 */
                if (method != null && ("post".equalsIgnoreCase(method.getValue()))) {
                    formUsesPost = true;
                }

                /* Suppress any targets that contain a URL scheme that is not http: or https: */
                if (action != null) {
                    Matcher m = startsWithScheme.matcher(action.getValue());
                    if (m.find() && !m.group(1).equals("http") && !m.group(1).equals("https")) {
                            continue;
                    }
                }

                /**
                 * If other catalogs(Millenium, Sirsi, et al) are previously found, then detection of bookmarklets 
                 * corresponding to the form actions, relative URIs which point to the same site, are suppressed.
                 *
                 * We also suppress absolute URLs if they lead back to the same site.
                 * We suppress both if the URL probed is contained in the action URL
                 * and the other way around.
                 * For instance, the Horizon at webcatalog.mdpls.org has url
                 * http://webcatalog.mdpls.org/ipac20/ipac.jsp?profile=sp-dial&lang=eng
                 * and contains links to http://webcatalog.mdpls.org/ipac20/ipac.jsp 
                 *
                 * (This may be too conservative since we don't check that they in fact lead
                 * to the same path.)
                 */
                /*
                if (action == null || !action.getValue().startsWith("http://")
                        || action.getValue().indexOf(url) != -1
                        || url.indexOf(action.getValue()) != -1) {
                    if (Config.verbose) {
                        Utils.printLog("skipping %s because resources were already found on that page", url);
                    }
                    continue;
                }
                */

                boolean foundNonHiddenField=false;
                
                Nodes controls = form.query(".//html:input", htmlContext);
                Nodes selectElements = form.query(".//html:select",htmlContext);
                Nodes submitButtonElements = form.query(".//html:button",htmlContext);
                
                for (int j = 0; j < submitButtonElements.size(); j++) {
                    Element buttonElement = (Element)submitButtonElements.get(j);
                    
                    /*
                     * Buttons can have types such as button/reset/submit. For our purposes,  
                     * only buttons with type - submit need to be considered, as reset and button are not 
                     * considered successful controls.  http://www.w3.org/TR/html401/interact/forms.html#edef-BUTTON
                     * Tested with pubmed.gov and http://cullman.booksys.net/opac/cullman/
                     */
                    if(buttonElement.getAttribute("type").getValue().equals("submit")) {
                        FormField field = new FormField(buttonElement.getAttribute("type"),buttonElement.getAttribute("name"),buttonElement.getAttribute("value"));
                        submitFieldList.add(field); 
                    }
                }
                
                for (int j = 0; j < selectElements.size(); j++) {
                    Element selectElement = (Element)selectElements.get(j);
                    
                    Elements options = selectElement.getChildElements();
                    
                    Attribute att = new Attribute("input","");
                    
                    /* Some sites use empty option to force the user to choose an option, as in
                     * <option value="">(Please select an option)</option>
                     * other sites use empty values as the default, e.g. www.britannica.com does
                     * <option value="">Britannica Online</option>
                     *
                     * Allow empty options for now; revisit this decision if necessary
                     */
                    boolean allowEmptyOption = true;
                    String optionValue = "";

                    for (int k = 0; k < options.size(); k++) {
                        Element option = options.get(k);
                        String val = option.getAttributeValue("value");

                        if ("selected".equalsIgnoreCase(option.getAttributeValue("selected"))) {
                            optionValue = val;
                            break;
                        }

                        if (val != null && (!val.equals("") || allowEmptyOption)) {
                            if (optionValue == null)
                                optionValue = val;
                        }
                    }
                    att.setValue(optionValue);
                    
                    FormField field = new FormField(selectElement.getAttribute("type"),selectElement.getAttribute("name"),att);
                    formFieldList.add(field);
                }
                
                // TBD <button> elements, see http://www.w3.org/TR/html4/interact/forms.html#h-17.5
                // test with http://cullman.booksys.net/opac/cullman/
                try {
                    HashMap<String, FormField> radioControls = new HashMap<String, FormField>();
                nextcontrol:
                    for (int j = 0; j < controls.size(); j++) {

                        Element control = (Element)controls.get(j);

                        FormField field = new FormField(control);

                        if (field.type == null)
                            continue;

                        String fieldType = field.type.getValue();

                        // ignore input type=reset
                        if ("reset".equalsIgnoreCase(fieldType)) {
                            continue nextcontrol;
                        }

                        if ("submit".equalsIgnoreCase(fieldType)) {
                            submitFieldList.add(field);
                        } else if ("radio".equalsIgnoreCase(fieldType)) {
                            /* Multiple radio controls may share the same "name". In this case, only one field
                             * should be generated.  The value must correspond to whichever is "on", if any.
                             * A radio field is "on" if the 'checked' attribute is present.  If none is
                             * checked, we use the first one we encounter.
                             */
                            if (!radioControls.containsKey(field.name.getValue()) || control.getAttribute("checked") != null) {
                                radioControls.put(field.name.getValue(), field);
                            }
                        } else if ("hidden".equalsIgnoreCase(fieldType)) {
                            // some hidden fields are used by frameworks for certain purposes, and 
                            // they should be omitted
                            String excludeHiddenFields [] = new String[] {
                                "__VIEWSTATE"   // see http://www.w3schools.com/ASPNET/aspnet_viewstate.asp
                            };
                            for (String exclude : excludeHiddenFields) {
                                if (exclude.equals(field.name.getValue()))
                                    continue nextcontrol;
                            }
                            formFieldList.add(field);
                        } else if ("image".equalsIgnoreCase(fieldType)) {
                            imageFieldList.add(field);
                        } else if ("checkbox".equalsIgnoreCase(fieldType)) {
                            // checkboxes are only successful if the 'checked' attribute is present, see
                            // http://www.w3.org/TR/html4/interact/forms.html#checkbox
                            if (control.getAttribute("checked") != null) {
                                formFieldList.add(field);
                            }
                        } else {
                            boolean interpretAsText = "".equals(fieldType) || "text".equals(fieldType);

                            /**
                             * If a previous non-hidden text field has been found, 
                             * then all other non hidden text fields are skipped. 
                             */
                            if (foundNonHiddenField && interpretAsText)
                                continue;

                            /**
                             * This handles the case when the first non-hidden text field is found. 
                             * If the value attribute of this field is null, then a new dummy attribute is created,
                             * which is set to contain the value "%Y"(which represents the option Keyword -
                             * the default option being provided by us)
                             * 
                             * A successful control is "valid" for submission. Every successful control has its control name 
                             * paired with its current value as part of the submitted form data set. 
                             * A successful control must be defined within a FORM element and must have a control name.
                             * We need this text element to be a successful control, in order to mimic the browser. 
                             * A control with a null name attribute cannot be a successful control. 
                             * Hence we create a new object of the class attribute and assign it the value and
                             * the local name "input".
                             */
                            if (interpretAsText) {
                                if(field.value == null)
                                    field.value = new Attribute("input","%Y");

                                field.value.setValue("%Y");
                                foundNonHiddenField = true;
                            }

                            formFieldList.add(field);
                        }
                    } 

                    for (FormField radioBoxField : radioControls.values())
                        formFieldList.add(radioBoxField);

                    /**
                     * Skipping all the forms who do not have text-boxes.
                     */
                    if(!foundNonHiddenField)
                        continue;
                    
                    createBookmarkletForEachSubmitField(context, url, formUsesPost,
                            formFieldList, submitFieldList, action, doc);

                    createBookmarkletForEachSubmitField(context, url, formUsesPost,
                            formFieldList, imageFieldList, action, doc);

                } catch (Exception exc) {
                    Utils.logUnexpectedException(exc);
                }
            } 
        }

        private void createBookmarkletForEachSubmitField(ProbeContext context, String url, boolean formUsesPost,
                List<FormField> formFieldList, List<FormField> submitFieldList,
                Attribute action,
                Document doc) {

            try {
                for(FormField submitField : submitFieldList) {
                    createAndAddBookmarklet(context, doc, submitField, formFieldList, action, url, formUsesPost);
                }
            } catch(Exception exc) {
                Utils.logUnexpectedException(exc);
            }
        }
    };

    /**
     * multiProbeURLList contains the URLs that are left to be explored by a
     * given probe thread executing multiProbe's examine method.
     * If a plugin encounters other URLs to be explored, it will schedule URLs
     * for exploration via scheduleForProbe.
     */
    static ThreadLocal<Queue<String>> multiProbeURLList = new ThreadLocal<Queue<String>> () {
        protected Queue<String> initialValue() {
            return new LinkedList<String>();
        }
    };

    /**
     * List of URLs we have already probed; ensure that each URL is only probed once.
     */
    static ThreadLocal<Set<String>> probedURLSet = new ThreadLocal<Set<String>> () {
        protected Set<String> initialValue() {
            return new HashSet<String>();
        }
    };

    static void scheduleForProbe(String url) {
        Set<String> alreadyProbed = probedURLSet.get();
        if (!alreadyProbed.contains(url)) {
            alreadyProbed.add(url);

            // limit total amount to be probed to 20 
            Queue<String> queue = multiProbeURLList.get();
            if (alreadyProbed.size() < 20 && queue.size() < 20)
                queue.offer(url);
        }
    }

    /**
     * Handles refresh of the pages first, and then initiates the examine actions of all the plugins for the 
     * various catalogs, serially. Then collates all the results and returns them.
     */
    static CatalogProbe multiProbe = new CatalogProbe() {

        @SuppressWarnings("unused")
        void dumpCookieStore(CookieStore cookieStore) {
            System.out.println("Dumping cookie store:");
            for (URI uri : cookieStore.getURIs()) {
                System.out.println("  for URI: " + uri);
                for (HttpCookie cookie : cookieStore.get(uri)) {
                    System.out.println("    " + cookie);
                }
            }
        }

        public void probe(ProbeContext context, String url) throws Exception {
            if (!url.startsWith("http://"))
                url = "http://" + url;

            String pageContent = null;
            Document doc = null;
            URLConnection conn = null;

            CookieManager cookieMan = new CookieManager(null, CookiePolicy.ACCEPT_ALL) {
                /*
                 * To work around http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6644726
                 * we change any cookie that's for port :80 to be without explicit port.
                 */
                private URI removePort80(URI uri) {
                    if (uri.getPort() == 80) {
                        try {
                            uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), -1, 
                                          uri.getPath(), uri.getQuery(), uri.getFragment());
                        } catch (URISyntaxException uriExc) {
                            Utils.logUnexpectedException(uriExc);
                        }
                    }
                    return uri;
                }

                /** Retrieve a cookie */
                public Map<String,List<String>> get(URI uri, Map<String,List<String>> requestHeaders) throws IOException {
                    uri = removePort80(uri);
 
                    Map<String, List<String>> cookieMap = super.get(uri, requestHeaders);
                    if (Config.verbose)
                        Utils.printLog("using cookies %s %s", uri.toString(), cookieMap.toString());
                    return cookieMap;
                }

                /** Store a cookie */
                public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException  {
                    if (Config.verbose)
                        Utils.printLog("putting cookie %s %s", uri.toString(), responseHeaders.toString());
                    uri = removePort80(uri);

                    // as work-around for http://bugs.sun.com/view_bug.do?bug_id=6692802
                    // remove the HttpOnly flag which JDK 1.6 cannot parse
                    // must clone entire header maps since it is unmodifiable
                    responseHeaders = new HashMap<String, List<String>>(responseHeaders);
                    for (Map.Entry<String, List<String>> header : responseHeaders.entrySet()) {
                        if (header.getKey() != null && header.getKey().startsWith("Set-Cookie")) {
                            List<String> cookies = new ArrayList<String>(header.getValue());
                            for (int i = 0; i < cookies.size(); i++) {
                                String cookie = cookies.get(i);
                                cookie = cookie.replace("; HttpOnly", "");
                                cookies.set(i, cookie);
                            }
                            header.setValue(cookies);
                        }
                    }
                    super.put(uri, responseHeaders);
                }
            };

            CookieHandler.setDefault(cookieMan);

            // schedule initial URL for probe
            scheduleForProbe(url);

            // process URL list until empty
            while (multiProbeURLList.get().size() > 0) {
                url = multiProbeURLList.get().poll();
                String startUrl = url;

                if (Config.verbose)
                    Utils.printLog("probing %s", startUrl);

                /**
                 * The following logic handles multiple redirects until the page 
                 * no longer points to any redirects or countRedirects exceeds 10.
                 */
                int countRedirects = 0;
                while (countRedirects < 10) {
                    URL u = new URL(url);
                    conn = u.openConnection();

                    String userAgent = System.getProperty("http.agent", null);
                    if (userAgent != null)
                        conn.setRequestProperty("User-Agent", userAgent);

                    HttpURLConnection hconn = (HttpURLConnection)conn;
                    hconn.setInstanceFollowRedirects(true);     // is default, but can't hurt
                    conn.connect();

                    // conn.connect() does connect to server content and hence does not 
                    // follow 302 redirects.  We must call either getInputStream, 
                    // getResponseHeaders, or getContent() to follow such redirects.
                    try {
                        conn.getContent(); 
                    } catch (UnknownServiceException use) {
                        // if no content type is given, simply ignore and continue (!?)
                        ;
                    } catch (FileNotFoundException fne) {
                        String urlShown = url;
                        if (urlShown.length() > 40) {
                            urlShown = url.substring(0, 40);
                        }
                        context.addResult(
                            new CatalogProbe.Result(urlShown + " does not exist or is unreachable.")
                        );
                    }

                    // See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4620571
                    // JDK does not follow 302 redirects that change protocol, e.g., go
                    // from http to https (like some Sirsi systems do, such as virgo.lib.virginia.edu)
                    // 
                    // If you see:
                    // javax.net.ssl.SSLKeyException: RSA premaster secret error
                    // check http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6382135
                    // and make sure /usr/java/jdk1.6.0/jre/lib/ext or equivalent is in java.ext.dirs
                    //
                    String loc = conn.getHeaderField("Location");
                    if (hconn.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP && loc != null) {
                        if (Config.verbose)
                            Utils.printLog("following Location redirect to %s", loc);

                        url = loc;
                        continue;
                    }

                    pageContent = Utils.slurpURL(conn);
                    doc = getParsedDocument(pageContent);
                    // System.out.println("PAGECONTENT_BEGIN\n" + pageContent + "\nPAGECONTENT_END");

                    url = Utils.makeSafeURL(conn.getURL());

                    doc.setBaseURI(url);
                    String redirecto = CatalogDetector.handleRefresh(doc, url, pageContent);
                    if (redirecto.equals(NO_REDIRECT))
                        break;

                    if (Config.verbose)
                        Utils.printLog("following meta refresh/onload redirect to %s", redirecto);

                    countRedirects++;
                    url = redirecto;
                }

                // startUrl is URL where we started
                // pageContent contains content of page
                // doc contains parsed document
                // conn contains URLConnection (including headers)
                // conn.getURL() is the URL where redirection ended up.
     
                /**
                 * Initiating the examine actions of each plugin and collating the results so obtained. 
                 */
                for(Plugin plugin : pluginVector) {
                    try {   
                        plugin.examine(context, doc, startUrl, pageContent, conn);
                    } catch (Throwable t) {
                        Utils.logUnexpectedException(t);
                    }
                }
            }
        }
    };

    /*
     * We do not handle catalog detection synchronously.
     * Instead, the detection is handled in a background thread.
     * However, the background thread cannot directly access
     * any components.  Therefore, we must set a timer that
     * retrieves the result from the probe after a given amount
     * of time.
     */
    public static class ProbeTimer extends Timer implements ProbeContext {
        final CatalogFoundCallback cb;
        final Label status;
        final String url;
        // results, totalcatalogsfound, and threadsleft are protected by monitor 'ProbeTimer.this'
        List<CatalogProbe.Result> results = new ArrayList<CatalogProbe.Result>();
        int threadsleft;    // for cmdline mode
        int stepsleft;      // number of steps left in GUI mode
        int totalcatalogsfound;

        String msg;
        private void setStatus() {
            int secleft = stepsleft * this.getDelay() / 1000;
            status.setValue("probing " + breakURL(this.url)
                    + " ("
                    + (secleft > 0 ? "time left " + secleft + " sec, " : "")
                    + totalcatalogsfound + " resource" 
                    + (totalcatalogsfound != 1 ? "s" : "") + " found)");
        }

        // url is assumed to be a http: URL but without the http:// prefix
        ProbeTimer(String url, Label status, CatalogFoundCallback cb, int delay, int stepsleft) {
            super(delay);
            this.cb = cb;
            this.url = url;
            this.status = status;
            this.stepsleft = stepsleft;
            this.totalcatalogsfound = 0;
            Utils.printLog("probing %s", url);
            status.setValue("probing " + breakURL(url) + "... ");

            // test that url's host part resolves to a valid host name
            try {
                InetAddress.getByName(url.replaceFirst("/.*$", "").replaceFirst(":\\d+$", ""));
            } catch (UnknownHostException ue) {
                status.setValue(url + " is not a valid host");
                return;
            }
            startProbeThreads();

            // Timer objects need to be added 
            // to some component on the page in order to work.
            ((Component)status.getParent()).appendChild(this);
            this.setRepeats(true);
            start();
        }

        /*
         * When the timer expires, retrieve the results and display them
         * on the page.
         */
        public void onTimer(Event e) {
            synchronized (this) {
                // if all probe threads have exited, short cut and abort
                if (threadsleft == 0)
                    stepsleft = 0;

                if (results.size() > 0) {
                    CatalogProbe.Result[] r = new CatalogProbe.Result[results.size()]; 
                    results.toArray(r);
                    for (CatalogProbe.Result _r : r) {
                        if (_r.getResult() != null)
                            this.totalcatalogsfound++;
                    }
                    results.clear();
                    cb.foundCatalogs(r);
                }
                setStatus();
            }

            // drop timer after number of steps
            if (stepsleft-- == 0) {
                stop();
                ((Component)status.getParent()).removeChild(this);
                cb.done(this.totalcatalogsfound);
            }
        }

        public synchronized void addResult(CatalogProbe.Result r) {
            // check if this result is suppressed by any already obtained result
            for (CatalogProbe.Result existing : results) {
                CatalogProbe.Result.Suppressor exSupp = existing.getSuppressor();
                if (exSupp != null && exSupp.suppresses(r))
                    return;
            }

            // check if this result suppresses any already obtained result
            CatalogProbe.Result.Suppressor rSupp = r.getSuppressor();
            if (rSupp != null) {
                Iterator <CatalogProbe.Result> it = results.iterator();
                while (it.hasNext()) {
                    if (rSupp.suppresses(it.next()))
                        it.remove();
                }
            }

            // we could implement this using suppressors, too.
            try {
                for (CatalogProbe.Result existing : results) {
                    if (r.getResult().getClass() == existing.getResult().getClass()
                            && Utils.getBeanProperty(r.getResult(), "url") != null
                            && Utils.getBeanProperty(r.getResult(), "url").equals(Utils.getBeanProperty(existing.getResult(), "url")))
                        return;
                }
            } catch (Exception e) {
                Utils.logUnexpectedException(e);
            }
            results.add(r);
        }

        public class ProbeThread implements Runnable {
            private final CatalogProbe probe;
            private String urltoprobe;
            ProbeThread(CatalogProbe probe, String urltoprobe) {
                this.probe = probe;
                this.urltoprobe = urltoprobe;
            }

            public void run() {
                try {
                    probe.probe(ProbeTimer.this, urltoprobe);
                } catch (Exception ue) {
                    // like to know (at least for debugging)
                    Utils.logUnexpectedException(ue);
                } finally {
                    synchronized (ProbeTimer.this) {
                        threadsleft--;
                        ProbeTimer.this.notify();
                    }
                }
            }
        }

        /*
         * Start an additional CatalogProbe for 'url', adding to the number of already
         * running probes.
         */
        public void startProbeThread(CatalogProbe probe, String url)
        {
            synchronized (this) {
                new Thread(new ProbeThread(probe, url)).start();
                threadsleft++;
            }
        }

        private void startProbeThreads()
        {
            msg = "no catalog detected at " + url;
            Thread [] threads = new Thread [] {
                    new Thread(new ProbeThread(multiProbe, url)),
                    new Thread(new ProbeThread(databaseProbe, url)),
            };
            synchronized (this) {
                for (Thread t : threads)
                    t.start();
                threadsleft = threads.length;
            }
        }

        /* for offline testing */
        ProbeTimer(String url, CatalogFoundCallback cb) {
            this.status = null;
            this.cb = cb;
            this.url = url;
            long start = System.currentTimeMillis();
            startProbeThreads();

            // now pick up all results
            synchronized (this) {
                do {
                    // report accumulated results to the callback as they come in
                    // exiting probe threads decrement threadsleft and signal ProbeTimer.this
                    if (results.size() == 0 && threadsleft > 0) {
                        try {
                            wait(detectCatalogTimeout);
                        } catch (InterruptedException _) { }
                    }

                    if (results.size() > 0) {
                        CatalogProbe.Result[] r = new CatalogProbe.Result[results.size()]; 
                        msg = "found " + r.length + " catalog";
                        if (r.length > 1)
                            msg += "s";

                        results.toArray(r);
                        for (CatalogProbe.Result _r : r) {
                            if (_r.getResult() != null)
                                this.totalcatalogsfound++;
                        }
                        cb.foundCatalogs(r);
                        results.clear();
                        System.out.println(msg);
                    }

                    long now = System.currentTimeMillis();
                    if ((now - start) > detectCatalogTimeout) {
                        System.out.println("ProbeTimer timeout");
                        break;
                    }
                } while (threadsleft > 0);
                cb.done(totalcatalogsfound);
            }
        }
    }

    static Plugin openSearchPlugin = new Plugin() {
        {
            pluginVector.add(this);
        }
        /* OpenSearchDescriptions can be found by looking for special <link> tags, 
         * like this one:
         * <link rel="search" type="application/opensearchdescription+xml" 
         *       title="Keyword Search Addison from your brower's Search Bar" 
         *       href="http://addison.vt.edu/screens/opensearch.xml" />
         */
        public void examine(ProbeContext context, Document doc, String url, String slurpedUrl, URLConnection conn) throws Exception {
            Nodes links = doc.query("//html:link[@rel='search' and @type='application/opensearchdescription+xml']", htmlContext);
            for (int i = 0; i < links.size(); i++) {
                Element link = (Element)links.get(i);
                String title = link.getAttribute("title").getValue();
                String targeturl = computeEffectiveUrl(doc, link.getAttribute("href").getValue());
                context.startProbeThread(new OpenSearchProbe(title), targeturl);
            }
            return;
        }           
    };

    public static void detectCatalog(final String url, final Label status, 
            final CatalogFoundCallback cb) 
    {
        status.setValue("");        

        @SuppressWarnings("unused")
        ProbeTimer t = new ProbeTimer(url, status, cb, detectCatalogTimestep, detectCatalogTimeout/detectCatalogTimestep);
    }
    /** How long to try for catalog detection to complete, in milliseconds */
    public static int detectCatalogTimeout = 60000;
    /** How long to pause between each step. */
    public static int detectCatalogTimestep = 2000;

    private static class Test {
        String url;
        int expectedNrCatalogs;
        Test(String url, int expectedNrCatalogs) {
            this.url = url;
            this.expectedNrCatalogs = expectedNrCatalogs;
        }
    }
    public static void main(final String []av) {

        Test[] testUrls = {
                new Test("http://udprism01.ucd.ie/TalisPrism/", 1), // Talisprism
                new Test("http://lclcat.lancashire.gov.uk/TalisPrism/", 1), // Talisprism
                new Test("http://talis-prism.derby.ac.uk/TalisPrism/", 1), // Talisprism
                new Test("http://prism.moray.gov.uk/TalisPrism/", 1), // Talisprism
                new Test("http://voyager.open.ac.uk", 1),    // Voyager 7
                new Test("http://voyager.lib.umb.edu", 1),   // Voyager 7
                new Test("http://librarycatalog.uco.edu", 1), // Voyager 7
                new Test("catalog.onlib.org", 1),            // Polaris
                new Test("pals.polarislibrary.com", 1),      // Polaris
                new Test("www.library.emory.edu", 2),        // Sirsi
                new Test("library.deanza.edu", 2),           // Sirsi
                new Test("virgo.lib.virginia.edu", 2),       // Sirsi
                new Test("sirsi.evms.edu", 1),               // Sirsi
                new Test("opac.ntu.edu.sg", 1),              // Sirsi
                new Test("addison.vt.edu", 14),              // III
                new Test("fiu.catalog.fcla.edu", 1),         // Endeca
                new Test("http://sfx.biblio.polymtl.ca:3210/sfxlcl3/az", 1), // SFX List V3
                new Test("library.mit.edu", 3),              // Aleph
                new Test("lms01.harvard.edu", 3),            // Aleph
                new Test("melvyl.cdlib.org", 5),             // Aleph
                new Test("fi.aleph.fcla.edu", 1),            // Aleph
                new Test("islandpines.roblib.upei.ca", 2),   // Evergreen
                new Test("catalogue.bclibrary.ca", 2),       // Evergreen
                new Test("catalog.library.ucla.edu", 2),     // Voyager
                new Test("search.live.com", 2),              // OpenSearchDescription
                new Test("http://islandpines.roblib.upei.ca/opac/en-US/skin/roblib/xml/index.xml", 1), // OpenSearchDescription
                new Test("http://catalog.volusialibrary.org/vcplvw/Vubis.csp", 1), // Vubis
                new Test("http://catalogue.kfpl.ca/kfplvw/Vubis.csp", 1), // Vubis
                new Test("http://opac.kenosha.lib.wi.us/kenoshavw/Vubis.csp", 1), // Vubis
                new Test("http://vubis.lewisandclarklibrary.org/lclvw/Vubis.csp", 1), // Vubis
                new Test("http://services.nflibrary.ca/niagvw/Vubis.csp", 1), // Vubis  (times out)
                new Test("hip.library.utah.edu", 3),         // Horizon
                new Test("www.google.com", 7),               // Bookmarklet
                new Test("www.britannica.com", 6),           // Bookmarklet
                new Test("www.worldcat.org", 9),             // Worldcat
                new Test("www.yahoo.com", 1),                       // Bookmarklet
                new Test("en.wikipedia.org/wiki/Main_Page", 3),     // Bookmarklet
                new Test("http://nplencore.library.nashville.org/iii/encore/app", 1), // III Encore
                new Test("www.westervillelibrary.org", 5), // III Encore
                new Test("http://library.scottsdaleaz.gov", 3), // III Encore
                new Test("http://prime2.oit.umn.edu:1701/primo_library/libweb/action/search.do?vid=TWINCITIES&reset_config=true", 1), // Primo 2.0
                new Test("http://www.library.emory.edu:32888/DB=primo1", 1), // Primo 2.0
                new Test("http://bibliotheque.uqac.ca/", 2), // Link to Primo
                new Test("su8bj7jh4j.search.serialssolutions.com", 3),   //Bookmarklet
                new Test("umkc.summon.serialssolutions.com", 1)    //Bookmarklet
        };

        if(av[0].equals("-t")) {
            for (Test test : testUrls){
                initiateProbeTimer(test.url, test.expectedNrCatalogs);
            }
        }

        else {
            initiateProbeTimer(av[0], 0);
        } 
    }

    private static void initiateProbeTimer(String url, final int expectedNrCatalogs) {
        Config.verbose = true;
        System.out.println("************");
        System.out.println("Url "+url);
        final long start = System.currentTimeMillis();

        @SuppressWarnings("unused")
        ProbeTimer t = new ProbeTimer(url, new CatalogFoundCallback() {
            public void foundCatalogs(CatalogProbe.Result [] list) {
                long now = System.currentTimeMillis();
                System.out.println(String.format("took %.3f seconds to find: ", (now-start)/1000.0));
                for (CatalogProbe.Result r : list) {
                    System.out.print(r.getMessage());
                    Object cat = r.getResult();
                    if (cat != null) {
                        System.out.print(" " + cat);
                        System.out.print(" [");
                        try {
                            for (Field f : cat.getClass().getDeclaredFields()) {
                                f.setAccessible(true);
                                if (f.get(cat) != null)
                                    System.out.print(f.getName() + "=" + f.get(cat) + ", ");
                            }
                        } catch (IllegalAccessException _) { }
                        System.out.print("]");
                    }
                    System.out.println();
                }
            }

            public void done(int totalcatalogsfound) {
                System.out.println("total numbers of catalogs found: " + totalcatalogsfound);
                System.out.println("************");
                if (expectedNrCatalogs != 0) {
                    if (expectedNrCatalogs == totalcatalogsfound) {
                        System.out.println("**SUCCESS.**");
                    } else {
                        System.out.println("**FAILURE: expected " + expectedNrCatalogs + " catalogs");
                    }
                }
            }
        });
    }

    /**
     * Given a form.action attribute, compute the effective target of the
     * form
     */
    private static String getActionTarget(Document doc, Attribute action) {
        String actionTargetURL = null;

        // http://www.ietf.org/rfc/rfc2396.txt says that an empty URI
        // is a reference to the same document.
        if (action == null || "".equals(action.getValue())) {
            actionTargetURL = doc.getBaseURI();
        } else {
            actionTargetURL = computeEffectiveUrl(doc, action.getValue());
            actionTargetURL = actionTargetURL.replaceFirst(":80$", "");
        }
        return actionTargetURL;
    }

    /**
     * This method extracts all the form elements of a page, and applies the url filter
     * to the 'action' element which represents that target URL of the form.
     *
     * Returns the first form that matches or null otherwise.
     */
    private static Element findFirstMatch(Document doc, UrlFilter checkUrlAction) throws Exception {
        try {
            Nodes forms = doc.query("//html:form", htmlContext);

            for (int i = 0; i < forms.size(); i++) {
                Element form = (Element)forms.get(i);

                Attribute action = form.getAttribute("action");
                String actionTargetURL = getActionTarget(doc, action);
                
                if (checkUrlAction.accept(actionTargetURL)) {   
                    return form;
                } 
            }

        } catch (Exception exc) {
            Utils.logUnexpectedException(exc);
        }
        return null;
    }

    /**
     * This method converts the contents of the page(slurpedUrl) into an InputStream,
     * to serve as an input to parser.build
     * This method returns the parsed document corresponding to the slurpedUrl
     * 
     * @param slurpedUrl
     * @return
     *
     */
    private static Document getParsedDocument(String slurpedUrl){
        Document doc=null;
        try {
            XMLReader tagsoup = XMLReaderFactory.createXMLReader("org.ccil.cowan.tagsoup.Parser");

            Builder parser = new Builder(tagsoup);

            ByteArrayInputStream bais = new ByteArrayInputStream(slurpedUrl.getBytes());
            doc = parser.build(bais);
        }catch(Exception exc) {
            exc.printStackTrace();
        }
        return doc;
    }

    /*
     * Returns the url of the page, to which the current page (represented by slurpedUrl) 
     * will be directed.
     * This method checks the occurrence of the REFRESH tag or body onLoad. 
     */
    private static Pattern metaRefreshContent = Pattern.compile("(\\d+);\\s*URL=(.*)", Pattern.CASE_INSENSITIVE);
    // window.location or document.location
    private static Pattern windowLocationRedirect = Pattern.compile("^\\s*(window\\.)?location\\s*=\\s*[\"\'](.*)[\"\']", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static Pattern windowLocationHrefRedirect = Pattern.compile("^\\s*window\\.location\\.href?\\s*=\\s*[\"\'](.*)[\"\']", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    /* Work-around for systems that try to be clever and nest the <meta>
     * in a <noscript> like so (such as some III Encore)
    <noscript>^M
        <meta http-equiv="refresh" content="0; URL=NoJavascript.html">^M
    </noscript>^M
    */
    private static Pattern hiddenRedirect = Pattern.compile("<noscript>\\s*<meta\\s+http-equiv=\"refresh\"\\s+content=\"0; URL=(.*)\">\\s*</noscript>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static String NO_REDIRECT = "no_redirect";

    private static String handleRefresh(Document doc, String url, String pageContent) {
        String redirectto = NO_REDIRECT;

        Nodes bodies = doc.query("//html:body", htmlContext);

        /* 
         * Note that there are many ways in which body's onload event can be
         * used for redirection.  
         * For instance, onload="window.location='http://newurl/'"
         * or onload="document.location='http://newurl/'"
         * or onload="window.location.replace('http://newurl/'"
         * or onload = "location = '/goto/http://primofe1.library.emory.edu:80/ ...
         * but many aleph libraries do also
         * function doLoad() {
         *     ... redirect here ...
         * }
         * and then body's onload read "javascript:doLoad()" (which we cannot handle)
         *
         * Example: opac.ntu.edu.sg
         */ 
        if (bodies.size() > 0) {
            Element body = (Element)bodies.get(0);
            Attribute onload = body.getAttribute("onload");
            if (onload != null) {
                Matcher m = windowLocationRedirect.matcher(onload.getValue());
                if (m.find()) {
                    redirectto = m.group(2);
                    return computeEffectiveUrl(doc, redirectto);
                }
            }
        }

        Nodes meta = doc.query("//html:meta[@http-equiv=\"refresh\"]", htmlContext);
        if (meta.size() == 0)
            meta = doc.query("//html:meta[@http-equiv=\"REFRESH\"]", htmlContext);

        try {
            Matcher matchHiddenRedirect = hiddenRedirect.matcher(pageContent);
            boolean hasHiddenRedirect = matchHiddenRedirect.find();

            for (int i = 0; i < meta.size(); i++) {

                Element metaTag = (Element)meta.get(i);

                Matcher mat=null;

                if(metaTag !=null && metaTag.getAttribute("content") != null) { 
                    mat = metaRefreshContent.matcher(metaTag.getAttribute("content").getValue());

                    if (mat.find()) {
                        String str = mat.group(2);

                        // handle <meta> hidden in <noscript>s
                        if (hasHiddenRedirect && str.equals(matchHiddenRedirect.group(1))) {
                            continue;
                        }

                        // the value of content can be any URL that's interpreted relative 
                        // to current document's base URI
                        str = computeEffectiveUrl(doc, str);
                        boolean isHttpOrHttps = str.startsWith("http://") || str.startsWith("https://");

                        int timeout = Integer.parseInt(mat.group(1));
                        if(isHttpOrHttps && timeout < 30) {
                            redirectto = str;
                        }
                    }
                }
            }
        } catch(Exception exc) {
            Utils.logUnexpectedException(exc);
        }

        /*
         * Voyager 7.0 uses an inlined script tag like this:
        <script language="javascript">
            window.location.href="http://voyager.lib.umb.edu/vwebv/searchBasic?sk=en_US"
        </script>

        or simply:

        <script type="text/javascript">
        <!--
        window.location = "http://librarycatalog.uco.edu/vwebv/searchBasic"
        //-->
        </script>
        */
        Nodes scripts = doc.query("//html:script[@language=\"javascript\" or @type=\"text/javascript\"]", htmlContext);
        for (int i = 0; i < scripts.size(); i++) {
            Element script = (Element)scripts.get(i);
            String scriptBody = script.getValue();
            Matcher m = windowLocationHrefRedirect.matcher(scriptBody);
            if (m.find()) {
                redirectto = m.group(1);
                return computeEffectiveUrl(doc, redirectto);
            }

            m = windowLocationRedirect.matcher(scriptBody);
            if (m.find()) {
                redirectto = m.group(2);
                return computeEffectiveUrl(doc, redirectto);
            }
        }

        return redirectto; 
    }

    private static String getTitleFromDoc(Document doc) {
        Nodes titleNodes = doc.query("//html:title", htmlContext);
        if (titleNodes.size() > 0)
            return titleNodes.get(0).getValue().trim();

        // since the document title is often used as a default for the catalog name,
        // use this fallback:
        return "[insert name here]";
    }

    /**
     * Retrieve "shortcut icon" for current document, if any.
     * See http://en.wikipedia.org/wiki/Favicon
     */
    private static String getShortcutIconFromDoc(Document doc) {
        Nodes shortCutIcons = doc.query("//html:link[@rel='shortcut icon' and starts-with(@type, 'image/')]", htmlContext);
        if (shortCutIcons.size() < 1)
            return null;
        Element shortCutIcon = (Element)shortCutIcons.get(0);
        String imageUrl = shortCutIcon.getAttribute("href").getValue();
        return computeEffectiveUrl(doc, imageUrl);
    }

    /*
     * Given an 'href' reference in a document, compute the effective URL.
     */
    private static String computeEffectiveUrl(Document doc, String href) {

        // absolute URL
        if (href.startsWith("http://") || href.startsWith("https://"))
            return href;

        String baseUrl = doc.getBaseURI();
        int firstSlashAfterUrl = -1;
        if (baseUrl.startsWith("http://")) {
            firstSlashAfterUrl = baseUrl.indexOf('/', "http://".length());
        } else
        if (baseUrl.startsWith("https://")) {
            firstSlashAfterUrl = baseUrl.indexOf('/', "https://".length());
        }

        if (href.startsWith("/")) {
            // remove everything after http://host/ 
            if (firstSlashAfterUrl != -1)
                baseUrl = baseUrl.substring(0, firstSlashAfterUrl); 
        } else {
            // remove 'basename' component
            if (firstSlashAfterUrl != -1)
                baseUrl = baseUrl.replaceFirst("/[^/]+$", "/");
            else
                baseUrl = baseUrl + "/";
        }
        return baseUrl + href;
    }
}
