package org.libx.editionbuilder;

import java.io.InputStreamReader;

import java.net.URL;
import java.net.URLConnection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.exolab.castor.types.AnyNode;

import org.libx.xml.srw.RecordData;
import org.libx.xml.srw.RecordType;
import org.libx.xml.srw.SearchRetrieveResponse;

/**
 * Search WorldCat registry for institution profiles.
 *
 * @author Godmar Back
 */
public class SearchWorldcat {
    public static int maxRecords = 30;

    private String makeQueryUrl(String sterm) {
        StringBuffer url = new StringBuffer("http://www.worldcat.org/webservices/registry/search/Institutions?");
        String [][]keyvalues = new String [][] {
            new String [] { "version", "1.1" },
            new String [] { "operation", "searchRetrieve" },
            new String [] { "recordSchema", "info:rfa/rfaRegistry/schemaInfos/adminData" },
            new String [] { "maximumRecords", Integer.toString(maxRecords) },
            new String [] { "startRecord", "1" },
            new String [] { "resultSetTTL", "300" },
            new String [] { "recordPacking", "xml" }
        };

        for (String [] kv : keyvalues) {
            url.append(kv[0]);
            url.append('=');
            url.append(Utils.encodeURIComponent(kv[1]));
            url.append('&');
        }

        url.append("query=" + Utils.encodeURIComponent(
                     "local.oclcAccountName all \"" + sterm + "\""
                    +" or "
                    +"local.institutionAlias all \"" + sterm + "\""
                    +" or "
                    +"local.institutionName all \"" + sterm + "\""));
        return url.toString();
    }

    private static SearchWorldcat singleton = new SearchWorldcat();
    static List<Record> search(String sterm) throws Exception {
        return singleton._search(sterm);
    }

    static class Record implements Comparable<Record> {
        String oclcId;
        String briefName;
        double similarity;

        Record(String oclcId, String briefName, double similarity) {
            this.oclcId = oclcId;
            this.briefName = briefName;
            this.similarity = similarity;
        }

        // natural order is in descending similarity
        // break ties by OCLC number - list smaller numbers first
        public int compareTo(Record that) {
            int r = new Double(that.similarity).compareTo(this.similarity);
            if (r != 0)
                return r;

            try {
                Integer thisN = new Integer(this.oclcId);
                Integer thatN = new Integer(that.oclcId);
                return thisN.compareTo(thatN);
            } catch (NumberFormatException nfe) {
                return 0;
            }
        } 

        public String toString() {
            return this.oclcId + ":" + this.similarity + ":" + this.briefName;
        }
    }

    /**
     * Search Worldcat registry for string sterm
     */
    List<Record> _search(String sterm) throws Exception {
        String url = makeQueryUrl(sterm);

        URLConnection urlconn = new URL(url).openConnection();
        urlconn.connect();
        SearchRetrieveResponse srr = (SearchRetrieveResponse)SearchRetrieveResponse.unmarshal(
                new InputStreamReader(urlconn.getInputStream()));
        ArrayList<Record> result = new ArrayList<Record>();
        if (srr.getRecords() == null)
            return result;

        for (RecordType r : srr.getRecords().getRecord()) {
            RecordData rdata = r.getRecordData();
            if (rdata == null)
                continue;

            for (Object o : rdata.getAnyObject()) {
                AnyNode node = (AnyNode)o;
                Set<String> lookfor = new HashSet<String>();
                String resourceIdKey = ":resourceID";
                String briefLabelKey = ":briefLabel";
                String adNameKey = "ad:name";
                String adAliasKey = "ad:alias";
                lookfor.add(resourceIdKey);
                lookfor.add(briefLabelKey);
                lookfor.add(adNameKey);
                lookfor.add(adAliasKey);
                Map<String, String> map = findTextItems(node, lookfor);
                if (map.get(resourceIdKey) == null || map.get(briefLabelKey) == null) {
                    continue;
                }

                String oclcId = map.get(resourceIdKey).replace("info:rfa/localhost/Institutions/", "");
                String briefLabel = map.get(briefLabelKey);
                String displayedLabel = briefLabel;
                if (map.get(adNameKey) != null) {
                    displayedLabel = map.get(adNameKey);
                    if (map.get(adAliasKey) != null) {
                        displayedLabel += "/" + map.get(adAliasKey);
                    }
                }
                result.add(new Record(oclcId, displayedLabel, cosineSimilarity(sterm, briefLabel)));
            }
        }

        /* sort by cosine similarity since OCLC does such a piss-poor job at ranking. */
        Collections.<Record>sort(result);
        return result;
    }

    private Set<String> array2CleanSet(String []v) {
        Set<String> set = new HashSet<String>(v.length);
        for (int i = 0; i < v.length; i++)
            set.add(v[i].replaceAll("[:\\.,()]*$", "").replaceAll("^[()]*", "").toLowerCase());

        return set;
    }

    private double cosineSimilarity(String s1, String s2) {
        String sep = "[\\s,\\-;]+";
        Set<String> set1 = array2CleanSet(s1.split(sep));
        Set<String> set2 = array2CleanSet(s2.split(sep));

        Set<String> common = new HashSet<String>(set1);
        common.retainAll(set2);
        double sim = common.size() / Math.sqrt(set1.size() * set2.size());
        // System.out.println("cos('" + s1 + "','" + s2 + "') = " + sim);
        return sim;
    }

    /* Recursively traverse XML tree, adding to map text content of 
     * <lookforX>text content</lookforX>
     * for elements named "lookforX".
     */
    private HashMap<String, String> findTextItems(AnyNode node, Set<String> lookfor) {
        HashMap<String, String> map = new HashMap<String, String>();
        traverse(node, lookfor, map);
        return map;
    }

    private void traverse(AnyNode node, Set<String> lookfor, HashMap<String, String> map) {
        switch (node.getNodeType()) {
        case AnyNode.ELEMENT:
            String name = node.getNamespacePrefix() + ":" + node.getLocalName();
            if (lookfor.contains(name))
                map.put(name, node.getStringValue().trim());

            /* recurse */
            if (node.getFirstChild() != null) {
                traverse(node.getFirstChild(), lookfor, map);
            }
            /* FALL THROUGH */

        case AnyNode.TEXT:
            if (node.getNextSibling() != null) {
                traverse(node.getNextSibling(), lookfor, map);
            }
            break;

        case AnyNode.ATTRIBUTE:
        default:
            break;
        }
    }

    public static void main(String []av) throws Exception {
        for (Record r : search(av[0]))
            System.out.println(r);
    }
}
