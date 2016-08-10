package org.libx.editionbuilder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.naming.InitialContext;

import javax.sql.DataSource;

/**
 * Various utilities to support database access.
 * 
 * @author gback
 *
 */
public class DbUtils {

    /**
     * An action to be performed on each entry in a result set.
     */
    public interface ResultSetAction {
        void execute(ResultSet rs) throws SQLException;
    }
    
    enum InteractionType {
        UPDATE, QUERY
    };
    
    /**
     * Perform a sql query and run result set action for each entry in returned result set.
     * 
     * @param sqlStatement
     * @param rsAction
     * @return a message that could be displayed to the user
     */
    public static void doSqlQueryStatement(String sqlStatement, ResultSetAction rsAction, String ... param) throws Exception {
        doSqlStatement(InteractionType.QUERY, sqlStatement, rsAction, param);
    }

    /**
     * Add a comment here.
     */
    public static void doSqlUpdateStatement(String sqlStatement, String ... param) throws Exception {
        doSqlStatement(InteractionType.UPDATE, sqlStatement, null, param);
    }

    private static class RowCounter implements ResultSetAction {
        private int rowCount;
        public void execute(final ResultSet rs) throws SQLException {
            rowCount = rs.getInt(1);
        }
        int getRowCount() {
            return rowCount;
        }
    }

    /**
     * Count number of rows in a table.
     */
    public static int getTableCount(String tableName) throws Exception {
        RowCounter rowCounter = new RowCounter();
        DbUtils.doSqlQueryStatement("SELECT COUNT(*) FROM " + tableName, rowCounter);
        return rowCounter.getRowCount();
    }

    /**
     * Count number of rows in a table, limited by a where clause.
     */
    public static int getTableCount(String tableName, String whereClause, String ... param) throws Exception {
        RowCounter rowCounter = new RowCounter();
        DbUtils.doSqlQueryStatement("SELECT COUNT(*) FROM " + tableName + " WHERE " + whereClause, rowCounter, param);
        return rowCounter.getRowCount();
    }

    /**
     * Execute a SQL statement and performs ResultSetAction.
     */
    private static DataSource ds = null;
    private static void doSqlStatement(InteractionType it, String sqlStatement, ResultSetAction rsAction, String ... param) throws Exception {
        try {
            if (ds == null)
                ds = (DataSource)new InitialContext().lookup(Config.dbjndipath);
        } catch (javax.naming.NamingException ne) {
            // suppress error msg in non-JNDI mode (when run from cmdline)
            if (System.getProperty("jdbc.url") == null)
                Utils.logUnexpectedException(ne);
        }
        
        Exception propagateToCaller = null;
        Connection conn = null; 
        PreparedStatement stmt = null;
        try { 
            if (ds != null) {
                conn = ds.getConnection(); 
            } else {
                // for standalone testing.
                String url = System.getProperty("jdbc.url");
                String user = System.getProperty("jdbc.username");
                String pass = System.getProperty("jdbc.password");
                if (url == null || user == null || pass == null)
                    throw new Exception("Please provide jdbc.* parameters for non-JNDI use");
                Class.forName("com.mysql.jdbc.Driver"); // register driver
                conn = DriverManager.getConnection(url, user, pass);
            }
            stmt = conn.prepareStatement(sqlStatement);
            int pn = 1;
            for (String p : param)
                stmt.setString(pn++, p);

            switch(it) {
                case QUERY:
                    ResultSet srs = stmt.executeQuery();
                    while (srs.next()) {
                        rsAction.execute(srs);
                    }
                    break;  
                case UPDATE:
                    stmt.executeUpdate();
                    break;
            }
        } catch (SQLException se) {
            propagateToCaller = se;
            System.out.println("###"+se.getErrorCode());
        } finally { 
            // perform proper cleanup, closing statement and connection
            if (stmt != null) { 
                try { 
                    stmt.close(); 
                } catch (SQLException ex) { 
                    System.out.println("Exception closing statement: " + ex);
                    
                    if (propagateToCaller == null)
                        propagateToCaller = ex;
                }
            }
            if (conn != null) { 
                try { 
                    conn.close(); 
                } catch (SQLException ex) { 
                    System.out.println("Exception closing connection: " + ex);
                    
                    if (propagateToCaller == null)
                        propagateToCaller = ex;
                } 
            } 
        }
        
        if (propagateToCaller != null)
            throw propagateToCaller;
    }
}
