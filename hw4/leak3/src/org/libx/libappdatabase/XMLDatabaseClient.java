package org.libx.libappdatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.Hashtable;

import javax.xml.namespace.QName;

import org.w3c.dom.Node;
import org.w3c.dom.Element;

/*
 * Base Interface for XML database access.
 */
public abstract class XMLDatabaseClient
{
    /* Subclasses must implement this method. */
    public abstract void runQuery (String key, Query.Callback callback) throws Exception;

    public void runQuery (String key) throws Exception {
        runQuery(key, Query.Callback.EMPTY);
    }

    public List<String> runQuery (String key, final Object ... args) throws Exception {
        final ArrayList<String> resultsAsStrings = new ArrayList<String>();

        runQuery(key, new Query.Callback.Adapter() {
            public void before(Query.PreparedExpression xqp) throws Exception {
                for (int i = 0; i < args.length; i += 2) {
                    if (String.class.isInstance(args[i+1]))
                        xqp.bindString(new QName((String) args[i]), (String) args[i+1]);
                    else if (Node.class.isInstance(args[i+1]))
                        xqp.bindNode(new QName((String) args[i]), (Node) args[i+1]);
                    else
                        throw new Error("Unsupported argument type: " + args[i+1].getClass());
                }
            }

            public void after(Query.ResultSequence results) throws Exception {
                while (results.next()) {
                    resultsAsStrings.add(results.getItemAsString(null));
                }
            }
        });

        return resultsAsStrings;
    }

    public List<Element> runQueryAsElements (String key, final Object ... args) throws Exception {
        final ArrayList<Element> resultsAsElements = new ArrayList<Element>();

        runQuery(key, new Query.Callback.Adapter() {
            public void before(Query.PreparedExpression xqp) throws Exception {
                for (int i = 0; i < args.length; i += 2) {
                    if (String.class.isInstance(args[i+1]))
                        xqp.bindString(new QName((String) args[i]), (String) args[i+1]);
                    else if (Node.class.isInstance(args[i+1]))
                        xqp.bindNode(new QName((String) args[i]), (Node) args[i+1]);
                    else
                        throw new Error("Unsupported argument type: " + args[i+1].getClass());
                }
            }

            public void after(Query.ResultSequence results) throws Exception {
                while (results.next()) {
                    // only valid for queries that return only elements!
                    resultsAsElements.add((Element) results.getNode());
                }
            }
        });

        return resultsAsElements;
    }

    /*
     * Common case interface.
     *
     * Run a query with a set of variable parameters and return
     * first result as string.
     */
    public String runQueryAsString (String key, Object ... args) throws Exception {
        return runQuery(key, args).get(0);
    }

    static abstract class Query {
        public interface ResultSequence {
            public boolean next() throws Exception;
            public String getItemAsString(Properties props) throws Exception;
            public Node getNode() throws Exception;
            /* more as needed */
        }

        public interface PreparedExpression {
            public void bindString(QName qname, String svalue) throws Exception;
            public void bindNode(QName qname, Node node) throws Exception;
        }

        interface Callback {
            public void before(PreparedExpression xqp) throws Exception; 
            public void after(ResultSequence results) throws Exception; 

            static class Adapter implements Callback {
                protected Object data;
                public void before(PreparedExpression xqp) throws Exception { } 
                public void after(ResultSequence xqp) throws Exception { } 

                Object getData() { return data; }
                void setData(Object data) { this.data = data; }
            }

            static Callback EMPTY = new Adapter();
        }

        protected String queryString;
        protected Callback callback;

        Query(String queryString, Callback callback) {
            this.queryString = queryString;
            this.callback = callback;
        }

        Query(String query) { this(query, Callback.EMPTY); }

        abstract void run() throws Exception;
    }

    /*
     * Common code for managing queries.
     */
    protected Hashtable<String, String> queries = new Hashtable<String, String>();
    public static String query_path = "org/libx/libappdatabase/queries";

    public XMLDatabaseClient () {
        try {
            // get list of queries
            URL query_dir_url = this.getClass().getClassLoader().getResource(query_path);
            if (query_dir_url == null)
                throw new Error("queryDir not found - make sure that WEB-INF/classes" 
                            + query_path + " exists and contains the queries.  Using loader=" + this.getClass().getClassLoader());

            File query_dir = new File(query_dir_url.toURI());

            String[] key_list = query_dir.list(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".xq");
                }
            });

            for (int i = 0; i < key_list.length; i++) {
                String key = key_list[i];
                queries.put(key, streamToString(
                    this.getClass().getClassLoader().getResource(query_path + "/"+ key).openStream()
                ));
            }
            System.out.println("initialized query set: " + queries.keySet());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private String streamToString (InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
          sb.append(line + "\n");
        }
        is.close();
        return sb.toString();
    }

}
