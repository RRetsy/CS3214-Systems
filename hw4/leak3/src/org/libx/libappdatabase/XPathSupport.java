package org.libx.libappdatabase;

import javax.xml.XMLConstants;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.namespace.NamespaceContext;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*; // ORGANIZE with Eclipse

/**
 * Convenience wrappers for XPath support
 */
public class XPathSupport
{
    static NamespaceContext context = new NamespaceContext() {
        private Map<String, String> prefix2URI = new HashMap<String, String>();
        {
            prefix2URI.put("libx", "http://libx.org/xml/libx2");
            prefix2URI.put("atom", "http://www.w3.org/2005/Atom");
        }

        @Override
        public String getNamespaceURI(String prefix) {
            String uri = prefix2URI.get(prefix);
            if (debug)
                System.out.println("resolving prefix: " + prefix + " to " + uri);
            return uri != null ? uri : XMLConstants.NULL_NS_URI;
        }

        @Override
        public String getPrefix(String namespace) {
            for (Map.Entry<String, String> e : prefix2URI.entrySet()) {
                if (e.getValue().equals(namespace))
                    return e.getKey();
            }
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespace) {
            List<String> p = new ArrayList<String>();
            for (Map.Entry<String, String> e : prefix2URI.entrySet()) {
                if (e.getValue().equals(namespace))
                    p.add(e.getKey());
            }
            return p.iterator();
        }
    };

    static XPathFactory factory = XPathFactory.newInstance();
    static XPath xpath = factory.newXPath();
    static {
        xpath.setNamespaceContext(context);
    }

    static synchronized String evalString(String xpathExpr, Node node) {
        if (debug)
            System.out.println("Running: xpath: " + xpathExpr + "\non XML: " + Utils.xmlToString(node));
        try {
            XPathExpression expr = xpath.compile(xpathExpr);
            Object result = expr.evaluate(node, XPathConstants.STRING);
            return (String)result;
        } catch (XPathExpressionException xpee) {
            throw new Error ("An xpath expression exception: " + xpee);
        }
    }

    public static boolean debug = false;
    static synchronized NodeList evalNodeSet(String xpathExpr, Node node) {
        if (debug)
            System.out.println("XPathSupport.evalNodeSet: xpath: " + xpathExpr + "\non XML: " + Utils.xmlToString(node));
        try {
            XPathExpression expr = xpath.compile(xpathExpr);
            Object result = expr.evaluate(node, XPathConstants.NODESET);
            return (NodeList)result;
        } catch (XPathExpressionException xpee) {
            throw new Error ("An xpath expression exception: " + xpee);
        }
    }

    static synchronized Node evalNode(String xpathExpr, Node node) {
        if (debug)
            System.out.println("XPathSupport.evalNodeSet: xpath: " + xpathExpr + "\non XML: " + Utils.xmlToString(node));
        NodeList result = evalNodeSet(xpathExpr, node);
        if (result.getLength() > 1)
            throw new Error ("More than one node for:" + xpathExpr);
        else if (result.getLength() == 1)
            return result.item(0);
        else
            return null;
    }

    public static void main(String []av) throws Exception { 
        XMLDatabaseClient.Query.Callback.Adapter query = new XMLDatabaseClient.Query.Callback.Adapter() {
            @Override
            public void after(XMLDatabaseClient.Query.ResultSequence results) throws Exception {
                if (results.next())
                    setData(Utils.parseXml(results.getItemAsString(null)));
            }
        };
        new BaseXClient.XQJQuery("doc('libx2_feed')", query).run();
        if ("-s".equals(av[0]))
            System.out.println(evalString(av[1], (Node) query.getData()));
        else if ("-n".equals(av[0])) {
            NodeList nodes = evalNodeSet(av[1], (Node) query.getData());
            if (nodes.getLength() == 0)
                System.out.println("returned 0 nodes.");
            for (int i = 0; i < nodes.getLength(); i++)
                System.out.println(Utils.xmlToString(nodes.item(i)));
        } else 
            System.out.println("Use either -s or -n");
    }
}
