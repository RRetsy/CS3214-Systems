package org.libx.autodetect;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.ValidityException;
import nu.xom.XPathContext;

import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import org.xml.sax.helpers.XMLReaderFactory;

public class Detector {
    public static boolean debug = false;
    private static XPathContext htmlContext = new XPathContext("html", "http://www.w3.org/1999/xhtml");

    public static class Element extends HashMap<String, String> {
        private nu.xom.Element domElement;

        public Element(nu.xom.Element e) {
            this.domElement = e;
            for (int i = 0; i < e.getAttributeCount(); i++) {
                Attribute attr = e.getAttribute(i);
                put(attr.getLocalName(), attr.getValue());
            }
        }
        public Collection<String> getKeys() {
            return keySet();
        }
        public String getValue() {
            return domElement.getValue();
        }
        public String toString() {
            return domElement.toString();
        }
    }

    /* Fact */
    public static class Page {
        private String url;
        private Document document;
        private URLConnection urlconnection;
        private Map<String, List<String>> headers;

        public Map<String, List<String>> getHeaders() { return this.headers; }
        public Collection<String> getHeaderKeys() { return this.headers.keySet(); }
        public String getUrl() { return this.url; }

        /**
         * Get this page's document, lazily.
         */
        public Document getDocument() { 
            if (this.document == null)
                retrieveDocument();
            return this.document; 
        }

        private void retrieveDocument() {
            try {
                String pageContent = Utils.slurpURL(this.urlconnection);
                XMLReader xmlreader;
                String contentType = this.urlconnection.getHeaderField("Content-Type");
                if (contentType.startsWith("text/xml"))
                    xmlreader = XMLReaderFactory.createXMLReader("org.apache.xerces.parsers.SAXParser");
                else
                    xmlreader = XMLReaderFactory.createXMLReader("org.ccil.cowan.tagsoup.Parser");
                Builder parser = new Builder(xmlreader);
                ByteArrayInputStream bais = new ByteArrayInputStream(pageContent.getBytes());
                this.document = parser.build(bais);
            } catch (ValidityException saxe) {
                System.out.println(saxe);
            } catch (ParsingException paxe) {
                System.out.println(paxe);
            } catch (SAXException saxe) {
                System.out.println(saxe);
            } catch (IOException ioe) {
                System.out.println(ioe);
            } 
        }

        public Page(String url) {
            try {
                URL u = new URL(url);
                URLConnection uc = u.openConnection();
                /* Note: sun.net.www.protocol.http.HttpURLConnection postpones
                 * retrieving the data (and following 302 redirects) until after 
                 * getInputStream() is called for the first time.
                 * getHeadersFields, getContent are two functions that call
                 * getInputStream() indirectly.
                 */
                uc.connect();
                this.headers = uc.getHeaderFields();
                /* 302 redirects, if any, have now been followed.
                 * uc.getURL() retrieves the URL we ended up at
                 */
                this.url = uc.getURL().toString();
                this.urlconnection = uc;
            } catch (MalformedURLException mfe) {
                System.out.println(mfe);
            } catch (IOException ioe) {
                System.out.println(ioe);
            }
        }

        // TBD cache queries
        public Collection<Node> query(String xpath, String nsprefix, String nsurl) {
            XPathContext nscontext = new XPathContext(nsprefix, nsurl);
            List<Node> nodes = new ArrayList<Node>();
            Nodes tags = getDocument().query(xpath, nscontext);
            for (int i = 0; i < tags.size(); i++) {
                nu.xom.Node node = (nu.xom.Node)tags.get(i);
                nodes.add(node);
            }
            return nodes;
        }

        public Map<String, List<Element>> getElements() {
            return elements;
        }
        private Map<String, List<Element>> elements = new HashMap<String, List<Element>>() {
            public List<Element> get(Object tag) {
                if (!containsKey(tag)) {
                    if (debug)
                        System.out.println("filling in tag: " + tag);

                    List<Element> elist = new ArrayList<Element>();
                    Nodes tags = getDocument().query("//html:" + tag, htmlContext);
                    for (int i = 0; i < tags.size(); i++) {
                        nu.xom.Element e = (nu.xom.Element)tags.get(i);
                        elist.add(new Element(e));
                    }
                    super.put((String)tag, elist);
                }
                return super.get(tag);
            }
        };
    }

    public static class Meta {
        public final static String REFRESH_AFTER_0 = "0;URL=(.*)";
    }

    private DroolsDriver.Host host;

    public Detector(DroolsDriver.Host host) throws Exception {
        this.host = host;
    }

    void probe(String url) {
        host.startNewSession();
        host.addFact(new Page(url));
        host.fireAllRules();
        host.dumpWorkingMemory(Facts.ofInterest.class);
    }

    public static void main(String []av) throws Exception {
        List<String> args = new ArrayList<String>(Arrays.asList(av));
        DroolsDriver driver = new DroolsDriver(args);

        Detector d = new Detector(driver.host);
        for (String arg : args) {
            d.probe(arg);
        }
    }
}
