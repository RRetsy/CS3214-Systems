package org.libx.editionbuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.libx.symlink.SymLink;

/**
 * Cache information about an edition that was expensive to obtain, such as
 * the URL of the edition's icon or the short information. Some information
 * would come from the database, some information would come from the filesystem.
 * Whenever a change is made to any edition parameter, its entry must be 
 * invalidated. 
 *
 * @author godmar
 */
public class EditionCache {
    /*
     * cachedRecords is a dynamically growing list of editions the user has browsed to.
     * It is filled in on-demand.
     * We should use weak references here.
     */
    static HashMap<String, Record> cachedRecords = new HashMap<String, Record>();

    private static class Record {
        final String editionId, shortDesc;
        final String iconPath;
        final String versionString;
        final Date lastBuildDate; // date .xpi last built, or null
        final int liveRevision; // -1 is not live
        final ArrayList<String> editionOwners;

        Record(String editionId, String shortDesc, String versionString, String iconPath, int liveRevision, Date lastBuildDate, ArrayList<String> editionOwners) {
            this.editionId = editionId;
            this.shortDesc = shortDesc;
            this.versionString = versionString;
            this.iconPath = iconPath;
            this.liveRevision = liveRevision;
            this.lastBuildDate = lastBuildDate;
            this.editionOwners = editionOwners;
        }

        public String toString() {
            return "[id=" + editionId + ",short='" + shortDesc + "', version=" 
                + versionString + ", liveRevision=" + liveRevision + "]";
        }
    }

    static class NotFoundError extends java.lang.Error {
        NotFoundError (String msg) {
            super(msg);
        }
    }

    /**
     * Check that record is in cache, recreating it if necessary.
     */
    private static void ensureRecordIsInCache(String editionId) {
        if (!cachedRecords.containsKey(editionId))
            cacheEditionInformation(editionId);

        if (!cachedRecords.containsKey(editionId))
            throw new NotFoundError("Edition: " + editionId 
                    + " not found, check edition database for consistency.");
    }

    private static void cacheEditionInformation(final String editionId) {
        try {
            DbUtils.doSqlQueryStatement("SELECT shortDesc FROM editionInfo WHERE editionId = '" + editionId + "'",
                    new DbUtils.ResultSetAction() {
                public void execute(ResultSet rs) throws SQLException {
                    addEdition(editionId, rs.getString(1));
                }
            });
        } catch (Exception ex) {
            Utils.logUnexpectedException(ex);
        }
    }

    // <option key="icon" value="chrome://libx/skin/virginiatech.ico"/>
    private static Pattern iconPattern = Pattern.compile("option key=\"icon\" value=\"chrome://libx/skin/([^\"]*)\"");
    private static Pattern versionPattern = Pattern.compile("edition[^>]+version=\"([^\"]*)\"");

    private static HashSet<String> editionsWithoutConfigurations = new HashSet<String>();
    static synchronized void addEdition(String editionId, String shortDesc) {
        if (cachedRecords.containsKey(editionId))
            return;

        int liveRevisionNumber = -1;
        String liveName = SymLink.readlink(Model.getLiveFSPath(editionId));
        if (liveName != null) {
            String[] liveSplit = liveName.split("\\.");
            liveRevisionNumber = Integer.parseInt(liveSplit[liveSplit.length - 1]);
        }

        String iconPath = "";
        String versionString = "";
        try {
            int rev = liveRevisionNumber;
            // if edition is not live, use last revision number here, else use live revision
            if (rev == -1) {
                List<Integer> revisions = Model.getRevisions(editionId);
                rev = revisions.get(revisions.size() - 1);
            }
            FileReader fis = new FileReader(Model.getFSPath(editionId, rev) + "/" + Model.configFileName);
            BufferedReader res = new BufferedReader(fis);
            try {
                String line;
                while ((line = res.readLine()) != null) {
                    Matcher m = iconPattern.matcher(line);
                    if (m.find()) {
                        iconPath = Model.getHttpPath(editionId, rev) + "/" + m.group(1);
                    }
                    m = versionPattern.matcher(line);
                    if (m.find()) {
                        versionString = m.group(1);
                    }
                }
            } finally {
                res.close();
            }

            Date lastBuildDate = null;
            File xpiFile = new File(Model.getBuildXpiPath(editionId, rev));
            if (xpiFile.exists())
                lastBuildDate = new Date(xpiFile.lastModified());

            Record newRecord = new Record(editionId, shortDesc, versionString, 
                                          iconPath, liveRevisionNumber, 
                                          lastBuildDate,
                                          getEditionOwnersFromDatabase(editionId));
            cachedRecords.put(editionId, newRecord);
        } catch (Throwable ex) {
            if (editionsWithoutConfigurations.add(editionId))
                Utils.printLog("edition %s not added to cache, ex=%s", editionId, ex);

            try {
                // create dummy record if edition directory is missing      
                Record newRecord = new Record(editionId, shortDesc, "unknown",
                                              "unknown", -1, 
                                              null,
                                              getEditionOwnersFromDatabase(editionId));
                cachedRecords.put(editionId, newRecord);
            } catch (Exception ex2) {
                Utils.logUnexpectedException(ex2);
            }
        }
    }

    private static ArrayList<String> getEditionOwnersFromDatabase(final String editionId) throws Exception {
        String selectEditionOwners = 
            "SELECT email FROM editionMaintainer WHERE editionId = '" + editionId + "'";

        final ArrayList<String> owners = new ArrayList<String>();
        DbUtils.doSqlQueryStatement(selectEditionOwners, new DbUtils.ResultSetAction() {
            public void execute(final ResultSet rs) throws SQLException {
                owners.add(rs.getString(1));
            }
        });
        return owners;
    }

    /*
     * Invalidate entire edition cache.
     */
    public static synchronized void invalidateAll() {
        cachedRecords.clear();
    }

    /**
     * Invalidate a cached edition. This should be called whenever
     * the short description or owner of an edition changes.
     */
    static synchronized void invalidateEdition(String editionId) {
        cachedRecords.remove(editionId);
    }

    static synchronized int getLiveRevisionNumber(String editionId) {
        ensureRecordIsInCache(editionId);
        return cachedRecords.get(editionId).liveRevision;
    }

    static synchronized boolean isLive(String editionId) {
        ensureRecordIsInCache(editionId);
        return cachedRecords.get(editionId).liveRevision != -1;
    }

    static synchronized String getIconPath(String editionId) {
        ensureRecordIsInCache(editionId);
        return cachedRecords.get(editionId).iconPath;
    }

    static synchronized String getVersionString(String editionId) {
        ensureRecordIsInCache(editionId);
        return cachedRecords.get(editionId).versionString;
    }

    static synchronized Date getLastBuildDate(String editionId) {
        ensureRecordIsInCache(editionId);
        return cachedRecords.get(editionId).lastBuildDate;
    }

    static synchronized String getShortDesc(String editionId) {
        ensureRecordIsInCache(editionId);
        return cachedRecords.get(editionId).shortDesc;
    }

    static synchronized ArrayList<String> getEditionOwners(String editionId) {
        ensureRecordIsInCache(editionId);
        return cachedRecords.get(editionId).editionOwners;
    }
}
