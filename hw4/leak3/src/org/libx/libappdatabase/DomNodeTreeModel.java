package org.libx.libappdatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.events.MutationEvent;
import org.zkoss.zul.AbstractTreeModel;
import org.zkoss.zul.event.TreeDataEvent;

/**
 * Implement tree model based on the tree formed by a DOM node.
 *
 * This class implements a tree suitable for org.zkoss.zul.Tree.setModel().
 * This is accomplished by mapping getChildCount, getChild, and isLeaf
 * to the DOM equivalents.
 */
public class DomNodeTreeModel extends AbstractTreeModel {
    public static boolean debug = true;

    private EventTarget rootTarget;
    private final Element root;
    private XMLDataModel xmlModel;
    private boolean doNotUpdateDatabase = false;

    DomNodeTreeModel(final XMLDataModel xmlModel, final Element root, final String docName) {
        super(root);
        this.rootTarget = (EventTarget)root;
        this.root = root;
        this.xmlModel = xmlModel;

        /**
         * This event handler is invoked whenever some node is appended to the
         * DomNodeTreeModel
         */
        setNodeInsertedListener(new EventListener() {
            public void handleEvent(Event event) {
                if (doNotUpdateDatabase)
                    return;

                Element target = (Element) ((MutationEvent)event).getTarget();
                Element parent = (Element) target.getParentNode();

                if (debug) {
                    System.out.println("node inserted: \n "
                        +"target.name : "+ target.getNodeName() +"\n"
                        +"target.id   : "+ target.getAttribute("id") +"\n"
                        +"target xml  : "+ Utils.xmlToString((Node)target) +"\n"
                        + "parent.name : "+ parent.getNodeName() +"\n"
                    );

                    if (target.getFirstChild() != null)
                        System.out.println("target.child: "+ target.getFirstChild().getNodeName() +"\n");
                }

                /**
                 * Case 1: A <node> is attached to a <node>. This means that
                 *  an existing entry has been attached to this 'target'
                 *
                 * We add the id of the new <node> as a child reference in the 
                 * parent <node> in the database.
                 * We don't do this if the parent is <root> because that means
                 * the entry is not linked to anything in LibX package tree.
                 */
                try {
                    if (target.getNodeName() == "node" && parent.getNodeName() == "node") {
                        xmlModel.appendReferenceToEntry(
                            target.getAttribute("id"),
                            parent.getAttribute("id"),
                            docName
                        );
                    }

                    // update roots if node is attached to root
                    if ("root".contentEquals(parent.getNodeName()))
                        xmlModel.updateRoots(docName);
                } catch (Exception ex) {
                    Utils.alert(ex);
                }

                fireTreeDataEvent(event, TreeDataEvent.INTERVAL_ADDED); // redundant? See default listener
                forceRerendering(parent);
            }
        });
        /**
         * This event handler is invoked whenever some node is removed from the
         * DomNodeTreeModel
         *
         * It is called *BEFORE* the node is actually removed, see
         * http://www.w3.org/TR/DOM-Level-2-Events/events.html#Events-MutationEvent
         */
        setNodeRemovedListener(new EventListener() {
            public void handleEvent(Event event) {
                Element target = (Element) ((MutationEvent)event).getTarget();
                Element parent = (Element) target.getParentNode();

                /**
                 * a <node> element is removed. This means that a reference to this
                 * node in the parent node's <entry> contents shall be removed
                 */
                if ("node".equals(target.getNodeName()) && "node".equals(parent.getNodeName())) {
                    try {
                        String parentId = parent.getAttribute("id");
                        String targetId = target.getAttribute("id");
                        xmlModel.removeReferenceFromEntry(targetId, parentId, docName);
                       
                    } catch (Exception ex) {
                        Utils.alert(ex);
                    }
                }

                fireTreeDataEvent(event, TreeDataEvent.INTERVAL_REMOVED);   // redundant? See default listener
            }
        });
        /*
        ((DomNodeTreeModel)tree.getModel()).setSubtreeModifiedListener(new EventListener() {
            public void handleEvent(Event event) {
                 XXX this handler conflicts with the insert/remove handler, since
                   any change to the tree will fire this event handler. this
                   issue needs to be resolved before nodes can be edited in 
                   place
            }
        });
        */
    }

    /**
     * If a just-removed node is now orphaned (the last reference to it was removed) 
     * it may have become a root. In this case, it should show up under the root node.
     *
     * This is called after removedNode. We cannot implement this logic in
     * the NodeRemoved listener because that listener is invoked before the
     * node has actually been removed.  If we called appendChild there, it
     * would trigger removeChild from its current parent, resulting in an
     * infinitely recursive call to the NodeRemoved listener.  Poooh.
     */
    void checkForOrphan (String docName, Element removedNode) throws Exception {
        List<Element> e = xmlModel.findReferencesToEntry(docName, removedNode.getAttribute("id"));
        if (e.isEmpty()) {
            root.appendChild(removedNode);
        }
    }

    /**
     * Returns the root node of the tree
     */
    public Element getRootNode() {
        return root;
    }

    /**
     * Returns whether the tree is empty or not. Specifically, 'true' is
     * returned if the root has no child Elements, and 'false' is returned
     * if the root has at least one child Element that is displayed in the
     * tree.
     *
     * @return true if tree is empty, false otherwise
     */
    public boolean isEmpty () {
        return (((Element)root).getElementsByTagName("node").getLength() == 0);
    }

    /**
     * Extracts an attribute value from a DOM Element. This is meant to be a
     * wrapper in order to hide the DOM implementation from the controllers.
     *
     * @param elem the Element to get the attribute from
     * @param attr_name the name of the Attribute to return 
     * @return value of the Attribute
     */
    public static String getProperty (Object elem, String attr_name) {
        return ((Element)elem).getAttribute(attr_name);
    }

    /**
     * Given an event and the event type, fire event to be heard by the
     * tree model.
     *
     * @param event the Event that occurred
     * @param eventType the type of TreeDataEvent
     * @return the DOM node that this event was effected upon
     */
    private void fireTreeDataEvent(Event event, int eventType) {
        MutationEvent mEvent = (MutationEvent)event;
        Node target = (Node)mEvent.getTarget(); 
        Node parent = target.getParentNode();
        int childIndex = getChildIndexFromParent(parent, target);
        fireEvent(parent, childIndex, childIndex, eventType);
    }

    /**
     * Given parent and child nodes, find the index at which the child occurs 
     * in the parent.
     *
     * @param parent Node in which the target is a child
     * @param target the child node to determine the index of
     * @return int indicating the index of the child, or -1 if not found
     */
    private int getChildIndexFromParent (Node parent, Node target) {
        ArrayList<Element> children = getChildElements(parent);
        for (int i = 0; i < children.size(); i++) {
            if (target == (Node)children.get(i)) return i;
        }
        return -1;
    }

    /**
     * Attach a user-defined subtreeModifiedListener to the DOM tree.
     * 
     * @param EventListener the listener to attach to the dom tree
     *        for the 'subtree modified' event
     */
    void setSubtreeModifiedListener (EventListener listener) {
        this.rootTarget.addEventListener(
            "DOMSubtreeModified", listener, /* capture */ false
        );
    }

    /**
     * Attach a user-defined nodeInsertedListener to the DOM tree.
     * 
     * @param EventListener the listener to attach to the dom tree
     *        for the 'node inserted' event
     */
    void setNodeInsertedListener (EventListener listener) {
        this.rootTarget.addEventListener(
            "DOMNodeInserted", listener, /* capture */ false
        );
    }
    /**
     * Attach a user-defined nodeRemovedListener to the DOM tree.
     * 
     * @param EventListener the listener to attach to the dom tree
     * for the 'node removed' event
     */
    void setNodeRemovedListener (EventListener listener) {
        this.rootTarget.addEventListener(
            "DOMNodeRemoved", listener, /* capture */ false
        );
    }

    /**
     * Defines a set of default DOM node listeners that simply fire 
     * TreeDataEvent^s to update the zk tree.
     */
    void setDefaultListeners () {
        setSubtreeModifiedListener(new EventListener() {
            public void handleEvent(Event event) {
                // fireTreeDataEvent(event, TreeDataEvent.CONTENTS_CHANGED);
                // XXX save entire tree here, perhaps;
                // This is fired also after any #text descendant has setData called on it
                // to update its value.
            }
        });
        setNodeInsertedListener(new EventListener() {
            public void handleEvent(Event event) {
                fireTreeDataEvent(event, TreeDataEvent.INTERVAL_ADDED);
            }
        });
        setNodeRemovedListener(new EventListener() {
            public void handleEvent(Event event) {
                fireTreeDataEvent(event, TreeDataEvent.INTERVAL_REMOVED);
            }
        });
    }

    /**
     * Given a generic DOM Node, extract all children that are of type
     * Element, ensuring that all nodes in our in-memory tree are
     * of type Element. This gives us the ability to utilize the Element
     * API, which has some useful methods not found in Node, and ensures
     * that only real Elements are displayed in the tree.
     *
     * @param Node parent parent node to extract child elems from
     * @return ArrayList containing Elements (as Node types)
     */
    private static ArrayList<Element> getChildElements (Node parent) {
        ArrayList<Element> child_elems = new ArrayList<Element>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (isElement(child) && "node".equals(child.getNodeName())) child_elems.add((Element)child);
        }
        return child_elems;
    }

    /**
     * Find all nodes in parent_node that match the tagname of child_node, and
     * remove them before inserting child_node. This method will most often be 
     * used to replace an entry element with an updated one, so usually only
     * one replacement will occur.
     *
     * Appears to be currently UNUSED
     *
     * @param parent_node the node to which the child_node will be attached
     * @param child_node the child_node to attach to parent_node
     * @return None
     * @see #attachNodeToParent(Object, Object, copy)
     */
    synchronized void replaceChildNode (Element parent, Element child) {
        ArrayList<Element> children = getChildElements(parent);
        for (int i = 0; i < children.size(); i++) {
            Node cur_child = (Node)children.get(i);
            if (cur_child.getNodeName().contentEquals(child.getNodeName())) {
                parent.removeChild(cur_child);
            }
        }
        attachNodeToParent(parent, child, false);
    }

    /**
     * Attach a node to another node as its child. This method also handles the
     * case in which two nodes may be from different documents, requiring an 
     * import or adoption of the child node.
     *
     * @param parent_node the node to which the child_node will be attached
     * @param child_node the node to append to the parent_node
     * @param copy create a copy of the child element
     * @return child element or copy thereof
     */
    public synchronized Element attachNodeToParent (Element parent, Element child, boolean copy) {
        Document parent_doc = parent.getOwnerDocument();
        Document child_doc  = child.getOwnerDocument();

        // the document may differ from copying/moving nodes from 
        // package to/from workspace
        if (parent_doc != child_doc) {
            child = (Element)parent_doc.importNode(child, true);
        }
        else if (copy) child = (Element)child.cloneNode(true);

        parent.appendChild(child);
        return child;
    }

    /**
     * When a node was cloned and pasted, we must recursively clone <node>
     * objects.  However, these already exist in the XML database, so suppress
     * updating the database during the deep copy.
     */
    void deepCopy(Element srcNode, Element dstNode) {
        try {
            doNotUpdateDatabase = true;
            NodeList srcChildren = srcNode.getChildNodes();
            for (int i = 0; i < srcChildren.getLength(); i++) {
                Node srcChildNode = srcChildren.item(i);
                if (srcChildNode instanceof Element) {  // skip non-element children, such as text nodes
                    Element srcChild = (Element) srcChildNode;
                    Element dstChild = attachNodeToParent(dstNode, srcChild, true);
                    deepCopy(srcChild, dstChild);
                }
            }
        } finally {
            doNotUpdateDatabase = false;
        }
    }

    Element addNewEntryToParent (Element parent, String id, String type, String title) throws Exception {
        Element newNode = parent.getOwnerDocument().createElement("node");
        newNode.setAttribute("id", id);
        newNode.setAttribute("title", title);
        newNode.setAttribute("type", type);
        parent.appendChild(newNode);
        return newNode;
    }

    /**
     * Remove node from parent; if expunge is true, then remove all references
     * to node and remove from database.
     *
     * @param node the node to remove
     * @param expunge if true, remove node from database completely
     * @return none
     */
    void removeNode (Element to_remove, boolean expunge) {
        System.out.println("Node to be removed is: " + Utils.xmlToString(to_remove));
        System.out.println("Parent is: " + Utils.xmlToString(to_remove.getParentNode()));
        if (!expunge) {
            to_remove.getParentNode().removeChild(to_remove);
            return;
        }
        System.out.println("expunging node.");

        // else, traverse entire tree and remove all references to 'to_remove'
        Element root = to_remove.getOwnerDocument().getDocumentElement();
        NodeList all_elems = root.getElementsByTagName(to_remove.getNodeName());
        for (int i = 0; i < all_elems.getLength(); i++) {
            Element child = (Element)all_elems.item(i);
            if (child.getAttribute("id").contentEquals(to_remove.getAttribute("id")))
                child.getParentNode().removeChild(child);
        }
    }

    // fire TreeData modification event to force rerendering
    private void forceRerendering(Node child) {
        Node parent = child.getParentNode();
        int childIndex = getChildIndexFromParent(parent, child);
        fireEvent(parent, childIndex, childIndex, TreeDataEvent.CONTENTS_CHANGED);
    }

    // fire TreeData modification event to force rerendering
    private void forceRerenderingAllchildren(Node parent) {
        fireEvent(parent, 0, getChildCount(parent) - 1, TreeDataEvent.CONTENTS_CHANGED);
    }

    private static boolean isElement(Node node) {
        return (node.getNodeType() == Node.ELEMENT_NODE);
    }
    
    private Map<Node, Boolean> childHasBeenAdded = new HashMap<Node, Boolean>();
    /** Returns the child of parent at index index in the parent's child array. */
    @Override
    public Object getChild(Object parent, int index) {
        assert parent instanceof Node;
        final Element child = getChildElements((Node)parent).get(index);
        if (!childHasBeenAdded.containsKey(child)) {
            childHasBeenAdded.put(child, true);
            xmlModel.addEntryChangeListener(getProperty(child, "id"), new XMLDataModel.EntryChangeListener() {
                public void entryChanged(Element entry) {
                    /* Propagate change in ./title/text() to node@title */
                    String newTitle = XPathSupport.evalString("./atom:title/text()", entry);
                    child.setAttribute("title", newTitle);

                    forceRerendering(child);
                }
            });
        }
        return child;
    }

    /** Returns the number of children of parent. */
    @Override
    public int getChildCount(Object parent) {
        assert parent instanceof Node;
        return getChildElements((Node)parent).size();
    }

    /** Returns true if node is a leaf. */
    @Override
    public boolean isLeaf(Object node) {
        assert node instanceof Node;
        return (getChildElements((Node)node).size() == 0);

    }
}
