package org.libx.libappdatabase;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.zkoss.zk.ui.Component;
import org.zkoss.zul.Vbox;

public class MultipleXMLEntriesController {

    public MultipleXMLEntriesController (Component container, final String xpath, final Node entry, ComponentCreator compDescription) throws XPathExpressionException {
        NodeList getExistingNodes = XPathSupport.evalNodeSet(xpath, entry);
        if (getExistingNodes.getLength() == 0)
            return;

        Vbox subEntryBox = new Vbox();
        subEntryBox.setHflex("1");
        for (int i =0; i < getExistingNodes.getLength(); i++)
            compDescription.createComponentsForNode(subEntryBox, getExistingNodes.item(i));
        container.appendChild(subEntryBox);
    }
    
    public interface ComponentCreator {
        public void createComponentsForNode(Component parent, Node node) throws XPathExpressionException;
    }
}
