package org.libx.libappdatabase;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Button;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Vbox;

public class MultipleXMLFieldsController {
    static class AbstractFieldCreator implements FieldCreator{
         Component parent;
         Node fieldNode;

        @Override
        public void createField(Node fieldNode, Component container) {
            this.parent = container;
            this.fieldNode = fieldNode;
        }

        @Override
        public Component getContainer() {
            return parent;
        }

        @Override
        public Node getFieldNode() {
            return fieldNode;
        }
    }

    public MultipleXMLFieldsController (final Component container, final String xpath, final String buttonName, final Node entry, 
            final FieldCreator [] fieldDescription, final NodeProducer nodeProducer ) {

        Button addButton = new Button(buttonName);
        container.appendChild(addButton);

        NodeList getExistingNodes = XPathSupport.evalNodeSet(xpath, entry);
        for (int i = 0; i < getExistingNodes.getLength(); i++)
            createRow(container, getExistingNodes.item(i), fieldDescription);
        final Node parentNode = getExistingNodes.item(0).getParentNode();
        addButton.addEventListener(Events.ON_CLICK, new EventListener() {
            public void onEvent(Event event) throws Exception {
                Node newNode = nodeProducer.addNode(parentNode);
                createRow(container, newNode, fieldDescription);
            }
        });
    }

    void createRow(Component container, final Node fieldNode, FieldCreator [] fieldDescription) {
        final Vbox fieldsRows = new Vbox();
        final Hbox fieldsRow = new Hbox();
        final Hbox[] fieldBox = new Hbox[fieldDescription.length];

        for (int i=0; i < fieldDescription.length; i++) {
            fieldBox[i] = new Hbox();
            fieldDescription[i].createField(fieldNode, fieldBox[i]);
            fieldsRow.appendChild(fieldBox[i]);
        }
        Button removeFieldButton = new Button(Messages.getString("MXFC_removeFieldButton")); //$NON-NLS-1$
        fieldsRow.appendChild(removeFieldButton);
        removeFieldButton.addEventListener(Events.ON_CLICK, new EventListener() {
            public void onEvent(Event event) throws Exception {
                fieldNode.getParentNode().removeChild(fieldNode);
                fieldsRow.setParent(null);
            }
        });
        fieldsRows.appendChild(fieldsRow);
        container.appendChild(fieldsRows);
    }

    public interface NodeProducer {
        public Node addNode (Node parent);
    }

    public interface FieldCreator {
        public void createField(Node fieldNode, Component container);
        Node getFieldNode();
        public Component getContainer();
    }
}
