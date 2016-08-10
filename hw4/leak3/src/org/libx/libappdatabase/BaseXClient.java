package org.libx.libappdatabase;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.xml.namespace.QName;
import javax.xml.xquery.XQConnection;
import javax.xml.xquery.XQDataSource;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQPreparedExpression;
import javax.xml.xquery.XQQueryException;
import javax.xml.xquery.XQResultSequence;

import org.w3c.dom.Node;

/**
 * Implement XML Database access via BaseX's XQJ interface
 */
public class BaseXClient extends XMLDatabaseClient {
    private static XQConnection xqc;
    static {
        try {
            xqc = ((XQDataSource)Class.forName("org.basex.api.xqj.BXQDataSource").
            newInstance()).getConnection();
        } catch (Exception e) {
            e.printStackTrace(System.err);
            throw new Error(e.getMessage());
        }
    }

    @Override
    public void runQuery (String key, Query.Callback callback) throws Exception {
        String queryString = queries.get(key);
        if (queryString == null)
            throw new Error("didn't find query: " + key);

        Query q = new XQJQuery(queries.get(key), callback);
        q.run();
    }

    static class XQJQuery extends XMLDatabaseClient.Query {
        XQJQuery(String queryString, Callback callback) {
            super(queryString, callback);
        }

        @Override
        void run() throws Exception {
            XQPreparedExpression xqp = null;
            try {
                xqp = xqc.prepareExpression(queryString);
                final XQPreparedExpression _xqp = xqp;
                callback.before(new PreparedExpression() {
                    @Override
                    public void bindString(QName qname, String svalue) throws Exception {
                        try {
                            _xqp.bindString(qname, svalue, null);
                        } catch (XQException xqe) {
                            throw new InvocationTargetException(xqe);
                        }
                    }
                    @Override
                    public void bindNode(QName qname, Node node) throws Exception {
                        try {
                            _xqp.bindNode(qname, node, null);
                        } catch (XQException xqe) {
                            throw new InvocationTargetException(xqe);
                        }
                    }
                });

                if (verbose) {
                    System.out.println("external vars: " + Arrays.asList(xqp.getAllExternalVariables()));
                    System.out.println("unbound vars: " + Arrays.asList(xqp.getAllUnboundExternalVariables()));
                    System.out.println("static result type: " + xqp.getStaticResultType());
                    System.out.println("static context: " + xqp.getStaticContext());
                }

                final XQResultSequence results = xqp.executeQuery();
                callback.after(new ResultSequence() {
                    @Override
                    public boolean next() throws Exception { 
                        try {
                            return results.next(); 
                        } catch (XQException xqe) {
                            throw new InvocationTargetException(xqe);
                        }
                    }
                    @Override
                    public String getItemAsString(Properties props) throws Exception {
                        try {
                            return results.getItemAsString(props);
                        } catch (XQException xqe) {
                            throw new InvocationTargetException(xqe);
                        }
                    }
                    @Override
                    public Node getNode() throws Exception {
                        try {
                            return results.getNode();
                        } catch (XQException xqe) {
                            throw new InvocationTargetException(xqe);
                        }
                    }
                });
            } catch (XQQueryException xqex) {
                Utils.xQQueryException(xqex, queryString);
                throw xqex;
            } finally {
                if (xqp != null)
                    xqp.close();
            }
        }
    }

    static boolean verbose = false;
    public static void main(String av[]) throws Exception {
        String queryString = av[0];

        BaseXClient bxClient = new BaseXClient();
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
