package org.libx.libappdatabase;

import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.events.EventTarget;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Label;

public class EditableXMLCheckboxField {
    private Attr domAttr;
    private Checkbox editableCheckbox;

    public EditableXMLCheckboxField(Node entry, Description desc, Component parent) {
        this.editableCheckbox = new Checkbox();
        String xpath = desc.xpath;
        Node attrNode = XPathSupport.evalNode(xpath, entry);
        this.domAttr = (org.w3c.dom.Attr)attrNode.getAttributes().getNamedItem(desc.attribute);
        editableCheckbox.setChecked(Boolean.valueOf(domAttr.getTextContent()));

        ((EventTarget)domAttr).addEventListener("DOMAttrModified", new org.w3c.dom.events.EventListener() {
            @Override
            public void handleEvent(org.w3c.dom.events.Event event) {
                Attr eventAttr = (Attr)event.getTarget();
                if(!String.valueOf(editableCheckbox.isChecked()).contentEquals(eventAttr.getValue())) {
                    //suppress DOMAttrModified fired on domAttr
                    editableCheckbox.setChecked(Boolean.getBoolean(eventAttr.getValue()));
                }
            }} , false
        );

        editableCheckbox.addEventListener(Events.ON_CHECK, new EventListener() {
            public void onEvent(Event event) throws Exception {
                domAttr.setValue(String.valueOf(editableCheckbox.isChecked()));
                // this will trigger DOMAttrModified on domAttr , and also
                // DOMSubtreeModified on all ancestors.
            }
        });

        Label attrField = new Label();
        attrField.setValue(desc.field);
        editableCheckbox.setHflex("1");
        parent.appendChild(attrField);
        parent.appendChild(editableCheckbox);
    }

    public static class Description extends MultipleXMLFieldsController.AbstractFieldCreator implements MultipleXMLFieldsController.FieldCreator{
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
            new EditableXMLCheckboxField(fieldNode, this, parent);
        }
    }
}
