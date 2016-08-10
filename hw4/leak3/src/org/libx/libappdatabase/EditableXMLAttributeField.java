package org.libx.libappdatabase;

import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.events.EventTarget;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;

public class EditableXMLAttributeField {
    private Attr domAttr;
    private Textbox editableTextbox;

    public EditableXMLAttributeField(Node node, Description desc, Component parent) {
        this.editableTextbox = new Textbox();
        String xpath = desc.xpath;
        Node attrNode = XPathSupport.evalNode(xpath, node);
        this.domAttr = (org.w3c.dom.Attr)attrNode.getAttributes().getNamedItem(desc.attribute);
        editableTextbox.setHflex("1");
        editableTextbox.setValue(attrNode.getAttributes().getNamedItem(desc.attribute).getTextContent());

        ((EventTarget)domAttr).addEventListener("DOMAttrModified", new org.w3c.dom.events.EventListener() {
            @Override
            public void handleEvent(org.w3c.dom.events.Event event) {
                Attr eventAttr = (Attr)event.getTarget();
                if (!editableTextbox.getValue().contentEquals(eventAttr.getValue())) {
                    //suppress DOMAttrModified fired on domAttr
                    editableTextbox.setValue(eventAttr.getValue());
                }
            }} , false
        );

        editableTextbox.addEventListener(Events.ON_CHANGE, new EventListener() {
            public void onEvent(Event event) throws Exception {
                domAttr.setValue(editableTextbox.getValue());
                // this will trigger DOMAttrModified on domAttr , and also
                // DOMSubtreeModified on all ancestors.
            }
        });

        Label attrField = new Label();
        attrField.setValue(desc.field);
        editableTextbox.setRows(1);
        editableTextbox.setHflex("1");
        parent.appendChild(attrField);
        parent.appendChild(editableTextbox);
    }

    public static class Description extends MultipleXMLFieldsController.AbstractFieldCreator implements MultipleXMLFieldsController.FieldCreator {
        String field;
        String xpath;
        String attribute;
        public Description (String field, String xpath, String attribute) {
            this.field = field;
            this.xpath = xpath;
            this.attribute = attribute;
        }
        @Override
        public void createField(Node fieldNode, Component parent) {
            super.createField(fieldNode, parent);
            new EditableXMLAttributeField(fieldNode, this, parent);
        }

    }
}
