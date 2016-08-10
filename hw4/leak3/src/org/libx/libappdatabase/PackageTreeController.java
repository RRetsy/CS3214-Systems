package org.libx.libappdatabase;

import org.w3c.dom.Element;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.MutationEvent;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Window;
import org.zkoss.zul.event.TreeDataEvent;
import org.zkoss.zul.event.TreeDataListener;

public class PackageTreeController extends TreeController {

    @Override
    public void initialize(Window mainWindow, XMLDataModel xmlModel, String docName, Clipboard clipboard) {  
        super.initialize(mainWindow, xmlModel, docName, clipboard);

        // retrieve tree from database, wrap in treemodel, and add listeners
        final DomNodeTreeModel pkgModel = createPackageTreeModel(docName);
        setModel(pkgModel);
        notifyModelChange(pkgModel);
        addEventListener(Events.ON_DROP, getRootAdditionListener());
    }

    /**
     * Retrieve tree from database, and define the event listeners for the tree.
     *
     * @return DomNodeTreeModel 
     */
    private DomNodeTreeModel createPackageTreeModel (final String docName) {
        try {
            final DomNodeTreeModel treeModel = new DomNodeTreeModel(xmlModel, 
                xmlModel.getTreeAsDomDocument(docName).getDocumentElement(),
                docName
            );

            treeModel.setNodeRemovedListener(new EventListener() {
                public void handleEvent(Event event) {
                    Object target_obj = ((MutationEvent)event).getTarget();
                    Element target = (Element)target_obj;
                    if (target == null) return;

                    Element parent = (Element)target.getParentNode();

                    /**
                     * Case 2: a <entry> is removed from a <node>. This means that
                     * an <entry> was edited and is about to be updated by the new
                     * version in the database. This is handled by rebuilding the 
                     * tree.
                     * TODO: when the interface is replaced with fields to edit
                     * individual DOM elements, these DOM elements should be
                     * edited in-place, and no rebuild should occur. For now, 
                     * however, there is no straightforward way to tell which parts
                     * of an <entry> have been modified and, since its modification
                     * could alter the roots of the tree, a rebuild must occur.
                     */
                    if (target.getNodeName() == "entry" && parent.getNodeName() == "node") {
                        DomNodeTreeModel pkgModel = createPackageTreeModel(docName);
                        setModel(pkgModel);
                        notifyModelChange(pkgModel);
                    }
                }
            });

            // disable drop if tree becomes empty
            treeModel.addTreeDataListener(new TreeDataListener() {
                @Override
                public void onChange(TreeDataEvent event) {
                    if (treeModel.getChildCount(treeModel.getRoot()) == 0)
                        setDroppable("false");
                }
            });
            return treeModel;
        }
        catch (Exception ex) {
            Utils.alert(ex);
        }

        // if creation of DomNodeTreeModel from database failed, substitute empty document
        // sanity check: is this the right behavior? it may hide errors from the user.
        try {
            return new DomNodeTreeModel(xmlModel, Utils.parseXml("<root/>").getDocumentElement(), docName);
        }
        catch (Exception ex) {
            Utils.alert(ex);
            return null;
        }
    }
}
