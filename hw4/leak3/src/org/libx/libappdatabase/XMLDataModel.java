package org.libx.libappdatabase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.libx.libappdatabase.XMLDatabaseClient.Query;
import org.libx.libappdatabase.XMLDatabaseClient.Query.ResultSequence;
import javax.xml.namespace.QName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

/*
 * Support methods for interacting with the database.
 */
public class XMLDataModel {
    final static String ATOM_NS = "http://www.w3.org/2005/Atom";
    final static String LIBX_NS = "http://libx.org/xml/libx2";
    private XMLDatabaseClient bxClient;
    private EntryCache entryCache = new EntryCache();

    /**
     * Sole constructor. Initializes a connection with the XML database, and 
     * sends this connection instance to the Query to prepare the query
     * collection.
     * 
     * @throws Exception if the database connection cannot be established
     */
    public XMLDataModel () throws Exception {
        bxClient = new BaseXSession();
    }

    //--- XML Database Operations
    /**
     * Determine the roots of the tree, and update the roots database with
     * this data.
     *
     * XXX: Instead of running this convoluted query, we should recognize
     * when the roots change and update the metadata directly. 
     *
     * @return None
     * @throws Exception if any database error occurs
     */
    void updateRoots(final String documentName) throws Exception {
        bxClient.runQuery("update_roots.xq",
                "doc_name", documentName);
    }

    private String getCurrentId() throws Exception {
        return bxClient.runQueryAsString("get_current_id.xq");
    }

    // find a better place for this
    static String getEntryType(Element target) {
        String entryType = XPathSupport.evalString("local-name(//libx:package|//libx:libapp|//libx:module)", target); //$NON-NLS-1$
        return entryType;
    }

    interface EntryChangeListener {
        public void entryChanged(Element entry);
    }

    /**
     * This class caches libx2:entry elements retrieved from the
     * database in memory for faster access.  
     * Typically, only a subset of entry elements is in memory.
     *
     * For consistency, all retrievals and updates to entry elements
     * must go through this cache.
     *
     * The cache supports a notification facility when an entry changes.
     */
    public class EntryCache {
        
        // TBD: replace with WeakHashmap
        private Map<String, Element> entries = new HashMap<String, Element>();

        private Map<String, List<EntryChangeListener>> listenersByNodeId =
            new HashMap<String, List<EntryChangeListener>>();
        
        private void addEntryChangeListener(String id, EntryChangeListener listener) {
            List<EntryChangeListener> listeners = listenersByNodeId.get(id);
            if (listeners == null) 
                listenersByNodeId.put(id, listeners=new ArrayList<EntryChangeListener>());
            listeners.add(listener);
        }
        
        void notifyEntryChanged(String id, Element node) {
            System.out.println("change for entry with id: " + id);
            for (EntryChangeListener l : listenersByNodeId.get(id))
                l.entryChanged(node);
        }

        Element getEntry(String docName, String id) throws Exception {
            Element entry = entries.get(id);
            if (entry == null) {
                entry = getEntryAsDomElement(id, docName);
                entries.put(id, entry);
            }
            return entry;
        }

        /**
         * Return a complete entry as a DOM Element.
         *
         * @param id the id of the entry to return
         * @param documentName The document name of the feed
         * @return DOM Document
         * @throws Exception if any database errors occur
         */
        private Element getEntryAsDomElement (final String id, final String documentName) throws Exception {
            /*
            Need string to XML conversion because BaseX's DOM implementation doesn't support DOM events.
            https://mailman.uni-konstanz.de/pipermail/basex-talk/2010-July/000432.html
            */
            String itemString = bxClient.runQueryAsString("get_full_entry.xq", 
                    "id", id, 
                    "doc_name", documentName);
            return Utils.parseXml(itemString).getDocumentElement();
        }

        void putEntry(String docName, String id, Element entry) throws Exception {
            assert entries.get(id) == entry : "entry not in cache";
            Calendar now = Calendar.getInstance();
            Text updatedTextNode = (org.w3c.dom.Text)XPathSupport.evalNodeSet("./atom:updated", entry).item(0).getFirstChild();
            updatedTextNode.setTextContent(new AtomDate(now.getTime()).toString());
            replaceEntry(id, entry, docName);
            notifyEntryChanged(id, entry);
        }

        /**
         * private once replaceEntry/text version is gone.
         *
         * @param id the id of the entry to be replaced
         * @param node is the node to be inserted
         * @param documentName The document name of the feed
         * @throws Exception if any database errors occur
         */
        void replaceEntry (final String id, final Element node, final String documentName) throws Exception {
            bxClient.runQuery("replace_entry.xq", 
                "entry", node,
                "id", id,
                "doc_name", documentName);
        }
    }

    Element getEntry(String docName, String id) throws Exception {
        return entryCache.getEntry(docName, id);
    }

    void putEntry(String docName, String id, Element entry) throws Exception {
        entryCache.putEntry(docName, id, entry);
    }

    void addEntryChangeListener(String id, EntryChangeListener listener) {
        entryCache.addEntryChangeListener(id, listener);
    }

    /**
     * Insert a new entry into the database.
     * 
     * @param documentName The document name of the feed
     * @param entryType The type of the entry(package, libapp or module)
     * @return None
     * @throws Exception if any database error occurs
     */

    public String insertNewEntry (final String documentName, final String entryType) throws Exception {
        bxClient.runQuery("insert_new_entry.xq",
            "doc_name", documentName,
            "entry_type", entryType);

        return getCurrentId();
    }

    public String cloneEntry (final String documentName, final Element entry) throws Exception {
        bxClient.runQuery("clone_entry.xq",
            "doc_name", documentName,
            "entry", entry);

        return getCurrentId();
    }

    /**
     * Replace an entry with a different document, in place, atomically.
     *
     * @param xml String representing the xml content to insert
     * @param id the id of the entry to replace
     * @param documentName The document name of the feed
     * @return None
     * @throws Exception if any database error occurs
     * @throws NonexistentEntryException if the entry we are trying to replace
     *  does not exist
     * @see #replaceEntry(Document, String)
     */
    // To be removed
    public void replaceEntry (String xml, String id, String documentName) throws Exception {
        entryCache.replaceEntry(id, Utils.parseXml(xml).getDocumentElement(), documentName);
    }

    /**
     * Remove an entry from the database, and remove all references to this
     * entry from parent entries.
     *
     * @param id the id of the entry to remove
     * @param documentName The document name of the feed
     * @return None
     * @throws Exception if any database error occurs
     */
    public void expungeEntry(final String id, final String documentName) throws Exception {
        bxClient.runQuery("expunge_entry.xq",
                "id", id,
                "doc_name", documentName);
        // XXX TBD: remove all references to 'id' in all DOMNode model (workspace and package tree)

        updateRoots(documentName);
    }

    /**
     * Given an existing child and existing parent, append the child as an
     * entry inside the parent.
     *
     * @param childId the id of the child
     * @param parentId the id of the parent
     * @param documentName The document name of the feed
     * @returns None
     * @throws Exception if any database error occurs
     */
    public void appendReferenceToEntry(final String childId, final String parentId, final String documentName) throws Exception {
        bxClient.runQuery("append_child_to_entry.xq",
            "child_id", childId,
            "parent_id", parentId,
            "doc_name", documentName);

        updateRoots(documentName);
    }
    /**
     * Removes a reference to a child entry from a parent entry. Does not remove
     * the entry definition from the database, only the reference.
     *
     * @param childId the id of the child
     * @param parentId the id of the parent
     * @param documentName The document name of the feed
     * @returns None
     * @throws Exception if any database error occurs
     */
    public void removeReferenceFromEntry(final String childId, final String parentId, final String documentName) throws Exception {
        bxClient.runQuery("remove_child_from_entry.xq", 
                "child_id", childId,
                "parent_id", parentId,
                "doc_name", documentName);

        updateRoots(documentName);
    }

    public List<Element> findReferencesToEntry (final String documentName, final String id) throws Exception {
        return bxClient.runQueryAsElements("find_references_to_entry.xq",
            "doc_name", documentName,
            "id", id);
    }

    /**
     * return a DOM Node representing the tree of packages/libapps/modules
     *
     * @param documentName The document name of the feed
     * @return a single DOM Document, which is the root of the tree
     * @throws Exception if any database error occurs
     */
    public Document getTreeAsDomDocument (final String docName) throws Exception {
        Query.Callback.Adapter query = new Query.Callback.Adapter() {
            @Override
            public void before(Query.PreparedExpression xqp) throws Exception {
                xqp.bindString(new QName("doc_name"), docName);
            }
            @Override
            public void after(ResultSequence results) throws Exception {
                Document tree = null;
                // see note in getEntryAsDomElement
                if (results.next()) {
                    tree = Utils.parseXml(results.getItemAsString(null));
                }
                if (tree == null) {
                    tree = Utils.parseXml("<root/>");
                }
                setData(tree);
            }
        };
        bxClient.runQuery("get_connected_tree.xq", query);
        return (Document) query.getData();
    }

    /**
     * Return an ArrayList of the ids for all roots of the tree
     *
     * @return the root ids
     * @throws Exception if any database error occurs
     */
    public ArrayList<String> getRootIds (final String docName) throws Exception {
        if (true)
            throw new Error("I didnt think this code was is used.");

        final ArrayList<String> roots = new ArrayList<String>();

        bxClient.runQuery("get_root_ids.xq", new Query.Callback.Adapter() {
            @Override
            public void before(Query.PreparedExpression xqp) throws Exception {
                xqp.bindString(new QName("doc_name"), docName);
            }
            @Override
            public void after(ResultSequence results) throws Exception {
                while (results.next()) {
                    String id = results.getNode().getFirstChild().getNodeValue();
                    roots.add(id);
                }
            }
        });

        return roots;
    }

    /** 
     * Publish a Libx2 feed
     *
     * @param documentName The document name of the feed
     * @param targetUrl The target url of the feed
     * @returns the results of the query as a string
     */
    public String publishFeed (final String documentName, final String targetUrl) throws Exception {
        final StringBuilder feeds = new StringBuilder();

        for (String feed : bxClient.runQuery("publish.xq", 
                    "doc_name", documentName, 
                    "target_url", targetUrl))
            feeds.append(feed);

        return feeds.toString();
    }

    /** 
     * Publish the Libx2 feed
     *
     * @param documentName The document name of the feed
     * @param targetUrl The target url of the feed
     * @param id The id of the entry requested
     * @returns the results of the query as a string
     */
    public String publishFeedEntry (final String documentName, final String targetUrl, final String id) throws Exception {
        final StringBuilder feeds = new StringBuilder();

        for (String feed : bxClient.runQuery("publish_entry.xq", 
                    "doc_name", documentName, 
                    "target_url", targetUrl, 
                    "id", id))
            feeds.append(feed);

        return feeds.toString();
    }
}
