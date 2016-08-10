package org.libx.libappdatabase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;

import javax.xml.namespace.QName;

import org.basex.core.BaseXException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Implementation
 */
class BaseXSession extends XMLDatabaseClient {
    private BaseXSessionSupport session;

    BaseXSession () {
        try {
            // trigger static initializer of org.libx.editionbuilder.Config, which loads
            // system properties from install.properties
            verbose = org.libx.editionbuilder.Config.verbose;
            String basexHost = System.getProperty("lb.basex.host", "lb.basex.host not set - check your post-build.sh file");
            String basexPort = System.getProperty("lb.basex.port", "lb.basex.port not set - check your post-build.sh file");
            String basexUser = System.getProperty("lb.basex.user", "lb.basex.user not set - check your post-build.sh file");
            String basexPass = System.getProperty("lb.basex.pass", "lb.basex.pass not set - check your post-build.sh file");

            int iBasexPort = Integer.parseInt(basexPort);
            session = new BaseXSessionSupport(basexHost, iBasexPort, basexUser, basexPass);
        } catch (IOException e) {
            throw new Error("Cannot establish connection to database: " + e.getMessage());
        }
    }

    private class PreparedExpression extends HashMap<String, String> implements Query.PreparedExpression {

        @Override
        public void bindString(QName qname, String svalue) throws Exception {
            put(qname.getLocalPart(), "\"" + svalue + "\"");
        }

        @Override
        public void bindNode(QName qname, Node node) throws Exception {
            String nodeAsXml = Utils.xmlToString(node);
            nodeAsXml = nodeAsXml.replaceFirst("<\\?xml.*version.*encoding.*\\?>", "")
                // escape { }
                .replaceAll("\\{", Matcher.quoteReplacement("&#x7B;"))
                .replaceAll("\\}", Matcher.quoteReplacement("&#x7D;"));

            put(qname.getLocalPart(), nodeAsXml);
        }
    }

    public void runQuery (String key, Query.Callback callback) throws Exception {
        String queryString = queries.get(key);
        if (queryString == null)
            throw new Error("didn't find query: " + key);

        PreparedExpression pe = new PreparedExpression();
        callback.before(pe);

        if (verbose)
            System.out.println("Mappings:\n" + pe);

        /* substitute external variable declarations from query */
        for (Map.Entry<String, String> kv : pe.entrySet()) {
            // remove declaration
            queryString = queryString
                .replaceAll("declare\\s*variable\\s*\\$" + kv.getKey() + "\\b.*external;", "");
            // substitute value
            queryString = queryString
                .replaceAll("\\$" + kv.getKey() + "\\b", Matcher.quoteReplacement(kv.getValue()));
        }

        if (verbose) {
            System.out.println("Substituted Xquery:\n" + queryString);
        }

        try {
            final BaseXSessionSupport.Query query = session.query(queryString);

            try {
                callback.after(new Query.ResultSequence() {
                    private String xmlResult;

                    public boolean next() throws Exception {
                        boolean haveMore = query.more();
                        if (haveMore)
                            xmlResult = query.next();
                        return haveMore;
                    }
                    public String getItemAsString(Properties props) throws Exception {
                        return xmlResult;
                    }
                    public Node getNode() throws Exception {
                        Document result = Utils.parseXml("<?xml version='1.0' encoding='UTF-8'?>" + xmlResult);
                        return result.getDocumentElement();
                    }
                });
            } finally {
                query.close();
            }
        } catch (BaseXException ex) {
            ex.printStackTrace();
        }
    }

    static boolean verbose = true;
    public static void main(String av[]) throws Exception {
        String queryString = av[0];

        BaseXSession bxClient = new BaseXSession();
        List<Object> args = new ArrayList<Object>();
        for (int i = 1; i < av.length; i++) {
            args.add(av[i++]);  // name
            if ("-xml".equals(av[i])) {
                args.add(Utils.parseXml(av[++i]));
            } else {
                args.add(av[i]);  // string
            }
        }

        List<String> results = bxClient.runQuery(queryString, args.toArray());
        System.out.println(results);
    }
}
