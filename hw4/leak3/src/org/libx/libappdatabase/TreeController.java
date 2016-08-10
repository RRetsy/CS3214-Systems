package org.libx.libappdatabase;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.DropEvent;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.OpenEvent;
import org.zkoss.zul.Menuitem;
import org.zkoss.zul.Menupopup;
import org.zkoss.zul.Popup;
import org.zkoss.zul.Tree;
import org.zkoss.zul.Treecell;
import org.zkoss.zul.Treeitem;
import org.zkoss.zul.TreeitemRenderer;
import org.zkoss.zul.Treerow;
import org.zkoss.zul.Window;

public class TreeController extends Tree {
    protected XMLDataModel xmlModel;
    protected String docName;
    private Popup packageMenuPopup;
    private Popup libappMenuPopup;
    private Popup moduleMenuPopup;
    private Element selectedEntry;
    private Clipboard clipboard;

    class ClipboardEntry {
        private String type;
        private Element element;
        ClipboardEntry(String type, Element element) {
            this.type = type;
            this.element = element;
        }
        @Override
        public String toString() {
            return type + " " + Utils.xmlToString(element);
        }
    }

    public static class Clipboard {
        private ClipboardEntry entry;   // currently copied information, if any
        void set(ClipboardEntry entry) { this.entry = entry; }
        void clear() { this.entry = null; }
        boolean isEmpty() { return this.entry == null; }
        ClipboardEntry get() { return this.entry; }
        @Override
        public String toString() { 
            return "Clipboard: " + String.valueOf(entry); 
        }
    }

    private Window mainWindow;

    public void setSelectedEntry (Element selectedEntry) {
        boolean newEntry = this.selectedEntry != selectedEntry;
        this.selectedEntry = selectedEntry;
        if (newEntry)
            notifyEntrySelected(getSelectedId());
    }

    public Element getSelectedEntry () {
        return this.selectedEntry;
    }

    public String getSelectedId () {
        String id = getSelectedEntry().getAttribute("id");
        if (id == null || id.length() == 0) return "root";
        return id;
    }

    public String getSelectedId (Element entry) {
        String id = entry.getAttribute("id");
        if (id == null || id.length() == 0) return "root";
        return id;
    }

    public Element getEntry (String id) throws Exception {
        Element idEntry = xmlModel.getEntry(docName, id);
        return idEntry;
    }

    protected void initialize (Window mainWindow, XMLDataModel xmlModel, String docName, Clipboard clipboard) {
        this.xmlModel = xmlModel;
        this.docName = docName;
        this.clipboard = clipboard;
        this.packageMenuPopup = new PackageMenuPopup();
        this.libappMenuPopup = new LibappMenuPopup();
        this.moduleMenuPopup = new ModuleMenuPopup();
        this.mainWindow = mainWindow;

        /* menupopup must be attached to the window to be rendered. */
        mainWindow.appendChild(this.packageMenuPopup);
        mainWindow.appendChild(this.libappMenuPopup);
        mainWindow.appendChild(this.moduleMenuPopup);

        final Menupopup backgroundContextMenu = new BackgroundContextMenu();
        setContext(backgroundContextMenu);
        mainWindow.appendChild(backgroundContextMenu);

        initializeTreeItemRenderer();
    }

    private void initializeTreeItemRenderer() {
        setTreeitemRenderer(new TreeitemRenderer() {
            public void render (Treeitem item, Object data) {
                System.out.println("Inside tree item renderer");
                if (data == null) return;
                item.setValue(data);

                String itemType = ((Element) data).getAttribute("type");
                if(itemType.equals("package"))
                    item.setContext(packageMenuPopup);
                else if(itemType.equals("libapp"))
                    item.setContext(libappMenuPopup);
                else if(itemType.equals("module"))
                    item.setContext(moduleMenuPopup);

                final DomNodeTreeModel domModel = (DomNodeTreeModel) getModel();

                /* Every CONTENTS_CHANGED event triggers a rerendering of this node.
                 * We must reuse the same Treerow/Treeitem elements here.
                 */
                Treerow row = item.getTreerow();
                if (row == null) {
                    row = new Treerow();
                    row.setParent(item);
                    row.setDraggable("true");
                    row.setDroppable("true");

                    /**
                     * Handle the event where the user left-clicks on a Treeitem
                     */
                    row.addEventListener(Events.ON_CLICK, new EventListener() {
                        public void onEvent(Event e) {
                            Treeitem clicked_item = (Treeitem)((Treerow)e.getTarget()).getParent();
                            //Object node_xml=((Treerow)e.getTarget()).getValue();
                            Element cur_node = (Element) clicked_item.getValue();
                            setSelectedEntry(cur_node);
                        }
                    });

                    /**
                     * Handle the event where the user drags a Treeitem, and drops it
                     * onto another Treeitem.
                     */
                    row.addEventListener(Events.ON_DROP, new EventListener() {
                        public void onEvent(Event e) { 
                            Treeitem dragged_item = (Treeitem)((Treerow)(((DropEvent)e).getDragged())).getParent();
                            Treeitem dest_item = (Treeitem)((Treerow)e.getTarget()).getParent();
                            if (dragged_item.getValue() == dest_item.getValue()) {
                                Utils.alert( new Error("An Entry cannot be a child of itself"));
                            }

                            domModel.attachNodeToParent(
                                (Element)dest_item.getValue(), (Element)dragged_item.getValue(), 
                                /* copy */ true
                            );
                            dest_item.setOpen(true);
                        }
                    });
                }

                /* Create Treecells on first rerendering; change labels on second rendering. */
                String sTitle = DomNodeTreeModel.getProperty(data, "title");
                String sType = DomNodeTreeModel.getProperty(data, "type");

                Treecell title, type;
                List<?> treecells = row.getChildren();
                if (treecells.size() == 0) {
                    row.appendChild(new Treecell(sTitle));
                    row.appendChild(new Treecell(sType));
                } else {
                    title = (Treecell)treecells.get(0);
                    type = (Treecell)treecells.get(1);
                    title.setLabel(sTitle);
                    type.setLabel(sType);
                }

            }
        });
    }
    /**
     * Special Case: 
     * When an item is attached to the root, 
     * TODO: make this a variable, not a function
     */
    protected EventListener getRootAdditionListener() {
        return new EventListener() { /* onDrop */
            public void onEvent(Event e) {
                Treeitem dragged_item = (Treeitem)((Treerow)(((DropEvent)e).getDragged())).getParent();
                final DomNodeTreeModel domModel = (DomNodeTreeModel) getModel();
                domModel.attachNodeToParent(
                    (Element)((DomNodeTreeModel)getModel()).getRootNode(), 
                    (Element)dragged_item.getValue(), true
                );
            }
        };
    }

    void refreshTree () {
        this.clear();
        initializeTreeItemRenderer();
    }

    interface Listener {
        /**
         * Tree has loaded a new model.
         */
        public void modelChange(DomNodeTreeModel model);
        /**
         * User selected an entry.
         */
        public void entrySelected(String docName, String id);
        /**
         * User created a new entry. (via context menu)
         */
        public void newEntryCreated(Element parent_item, String entryType);
        /**
         * User deleted an entry. (via context menu)
         */
        public void entryDeleted(Element child_item, Element parent_item);
    }

    /* Convenience class for Listeners that don't implement all methods. */
    static abstract class ListenerBase implements Listener {
        @Override
        public void modelChange(DomNodeTreeModel model) { }
        @Override
        public void entrySelected(String docName, String id) { }
        @Override
        public void newEntryCreated(Element parent_item, String entryType) { }
        @Override
        public void entryDeleted(Element child_item, Element parent_item) { }
    }

    protected List<Listener> treeControlListeners = new ArrayList<Listener>();

    void addListener(Listener l) {
        treeControlListeners.add(l);
    }

    void removeListener(Listener l) {
        treeControlListeners.remove(l);
    }

    protected void notifyModelChange(DomNodeTreeModel model) {
        for (Listener l : treeControlListeners)
            l.modelChange(model);
    }

    protected void notifyEntrySelected(String id) {
        for (Listener l : treeControlListeners)
            l.entrySelected(docName, id);
    }

    protected void notifyNewEntryCreated(Element parent_item, String entryType) throws Exception {
        for (Listener l : treeControlListeners)
            l.newEntryCreated(parent_item, entryType);
    }

    protected void notifyEntryDeleted(Element child_item, Element parent_item) throws Exception {
        for (Listener l : treeControlListeners)
            l.entryDeleted(child_item, parent_item);
    }

    protected void createEntryActions(Element parent_item, String entryType, Treeitem right_clicked_item) throws Exception {
        final DomNodeTreeModel domModel = (DomNodeTreeModel) getModel();
        String id = xmlModel.insertNewEntry(docName, entryType);
        domModel.addNewEntryToParent(parent_item, id, entryType, "New " + entryType);

        if (right_clicked_item != null)
            right_clicked_item.setOpen(true);
        notifyNewEntryCreated(parent_item, entryType);
    }

    /**
     * Support for context menu used for operations on tree nodes.
     */
    abstract class MenupopupAdapter extends Menupopup {
        protected Menuitem pasteItem;
        protected Menuitem cloneItem;
        protected Menuitem copyItem;
        protected Menuitem removeItem;
        protected Menuitem expungeItem;

        /**
         * Convenience function to add menu item.
         */ 
        protected Menuitem addMenuitem(String label, EventListener onClickHandler) {
            Menuitem new_child = new Menuitem();
            new_child.setLabel(label);
            new_child.addEventListener(Events.ON_CLICK, onClickHandler);
            appendChild(new_child);
            return new_child;
        }

        protected void sharedMenuItems() {
            removeItem = addMenuitem("Remove Reference", new EventListener() {
                public void onEvent (Event e) {
                    final DomNodeTreeModel domModel = (DomNodeTreeModel) getModel();
                    Element entryToRemove = (Element) getRightClickedDOMNode();
                    domModel.removeNode(entryToRemove, /* expunge */ false);
                    try {
                        domModel.checkForOrphan(docName, entryToRemove);
                    } catch (Exception ex) {
                        Utils.alert(ex);
                    }
                }
            });

            expungeItem = addMenuitem("Expunge Entry", new EventListener() {
                public void onEvent (Event e) {
                    final DomNodeTreeModel domModel = (DomNodeTreeModel) getModel();
                    Element entryToExpunge = (Element) getRightClickedDOMNode();
                    domModel.removeNode(entryToExpunge, /* expunge */ true);
                }
            });

            copyItem = addMenuitem("Copy", new EventListener() {
                public void onEvent (Event e) {
                    Element entryToCopy = (Element) getRightClickedDOMNode();
                    clipboard.set(new ClipboardEntry(XMLDataModel.getEntryType(entryToCopy), entryToCopy));
                    System.out.println("entry to copy is: " + clipboard);
                }
            });

            /* NOTE: calling attachNodeToParent only adds a reference in the DOMNode on 
             * the left, and updates the <entry src=""> list.
             * It does not actually clone the item being pasted.  That would require
             * assigning a new id.
             */
            pasteItem = addMenuitem("Paste", new EventListener() {
                public void onEvent (Event e) {
                    final DomNodeTreeModel domModel = (DomNodeTreeModel)getModel();
                    System.out.println("Pasting clipboard entry: " + clipboard);

                    Element pasteTarget = (Element) getRightClickedDOMNode();
                    System.out.println("Paste target entry here is: " + Utils.xmlToString(pasteTarget));
                    domModel.attachNodeToParent(pasteTarget, clipboard.get().element, true);
                    clipboard.clear();       // or should it remain in the clipboard for multiple pastings???
                }
            });

            cloneItem = addMenuitem("Clone & Paste", new EventListener() {
                public void onEvent (Event e) {
                    final DomNodeTreeModel domModel = (DomNodeTreeModel)getModel();
                    System.out.println("Cloning clipboard entry: " + clipboard);

                    Element pasteTarget = (Element) getRightClickedDOMNode();
                    try {
                        Element srcNode = clipboard.get().element;
                        String cloneId = xmlModel.cloneEntry(docName, getEntry(srcNode.getAttribute("id")));
                        String cloneTitle = XPathSupport.evalString(".//atom:title/text()", getEntry(cloneId));
                        Element clone = domModel.addNewEntryToParent(pasteTarget, cloneId, clipboard.get().type, cloneTitle);
                        clipboard.clear();       // or should it remain in the clipboard for multiple pastings???
                        domModel.deepCopy(srcNode, clone);
                    } catch (Exception ex) {
                        Utils.alert(ex);
                    }
                }
            });
        }

        protected void onMenuOpen(Component rightClickedItem) {
            rightClickedTreeItem = (Treeitem)rightClickedItem;
            rightClickedDOMNode = (Element) rightClickedTreeItem.getValue();
            activateCopyClonePasteItems();
        }

        protected void activateCopyClonePasteItems() {
            /* decide whether to enable/disable copy/paste/clone */
            boolean pasteOk = false;
            boolean cloneOk = false;
            String targetType = rightClickedDOMNode.getAttribute("type");
            if (!clipboard.isEmpty()) {
                boolean isBackground = rightClickedDOMNode != null 
                    && "root".contentEquals(rightClickedDOMNode.getNodeName());

                if (isBackground)
                    pasteOk = cloneOk = true;

                if (!targetType.contentEquals("module")) {
                    // nothing can be pasted onto a module
                    String clipboardType = clipboard.get().element.getAttribute("type");
                    if (targetType.contentEquals("libapp") && clipboardType.contentEquals("module")) {
                        pasteOk = cloneOk = true;
                    }
                    if (targetType.contentEquals("package") && clipboardType.contentEquals("libapp")) {
                        pasteOk = cloneOk = true;
                    }
                }
            }

            pasteItem.setDisabled(!pasteOk);
            cloneItem.setDisabled(!cloneOk);
        }

        protected Treeitem rightClickedTreeItem;
        protected Element rightClickedDOMNode;

        /* Returns Element in document backing the DomNodeTreeModel controller (<node> ... etc.)
         * Does not return the <entry>!
         */
        Object getRightClickedDOMNode() {
            return rightClickedDOMNode;
        }

        Treeitem getRightClickedItem() {
            return rightClickedTreeItem;
        }

        MenupopupAdapter() {
            addEventListener(Events.ON_OPEN, new EventListener() {
                public void onEvent(Event e) {
                    Component rightClickedItem = ((OpenEvent)e).getReference();

                    // ignore 2nd ON_OPEN event, which is sent on close
                    if (rightClickedItem == null)
                        return;

                    onMenuOpen(rightClickedItem);
                }
            });
        }
    }

    private class PackageMenuPopup extends MenupopupAdapter {
        PackageMenuPopup() {
            addMenuitem("Create New Package", new EventListener() {
                public void onEvent (Event e) {
                    try {
                        final Treeitem right_clicked_item = getRightClickedItem();
                        Element parent_item = (Element)getRightClickedDOMNode();
                        createEntryActions(parent_item, "package", right_clicked_item);
                    } catch (Exception e1) {
                        Utils.alert(e1);
                    }
                }
            });

            addMenuitem("Create New LibApp", new EventListener() {
                public void onEvent (Event e) {
                    try {
                        final Treeitem right_clicked_item = getRightClickedItem();
                        Element parent_item = (Element)getRightClickedDOMNode();
                        createEntryActions(parent_item, "libapp", right_clicked_item);
                    } catch (Exception e1) {
                        Utils.alert(e1);
                    }
                }
            });
            sharedMenuItems();
        }
    }

    private class LibappMenuPopup extends MenupopupAdapter {
        LibappMenuPopup() {
            addMenuitem("Create New Module", new EventListener() {
                public void onEvent (Event e) {
                    try {
                        final Treeitem right_clicked_item = getRightClickedItem();
                        Element parent_item = (Element)getRightClickedDOMNode();
                        createEntryActions(parent_item, "module", right_clicked_item);
                    } catch (Exception e1) {
                        Utils.alert(e1);
                    }
                }
            });

            sharedMenuItems();
        }
    }

    private class ModuleMenuPopup extends MenupopupAdapter {
        ModuleMenuPopup() {
            sharedMenuItems();
        }
    }

    /**
     * The background menu allows creating packages, libapps, and modules,
     * allows pasting, but does not allow copying
     *
     * We inherit from PackageMenuPopup and then adjust as required.
     */
    private class BackgroundContextMenu extends PackageMenuPopup {
        BackgroundContextMenu() {
            Menuitem moduleItem = addMenuitem("Create New Module", new EventListener() {
                public void onEvent (Event e) {
                    try {
                        final Treeitem right_clicked_item = getRightClickedItem();
                        Element parent_item = (Element)getRightClickedDOMNode();
                        createEntryActions(parent_item, "module", right_clicked_item);
                    } catch (Exception e1) {
                        Utils.alert(e1);
                    }
                }
            });
            // move it in second place below Create Package/Libapp
            insertBefore(moduleItem, (Component)getChildren().get(2));
            removeChild(copyItem);
            removeChild(expungeItem);
            removeChild(removeItem);
        }

        @Override
        protected void onMenuOpen(Component rightClickedItem) {
            rightClickedTreeItem = null;
            rightClickedDOMNode = ((DomNodeTreeModel)TreeController.this.getModel()).getRootNode();
            activateCopyClonePasteItems();
        }
    }
}
