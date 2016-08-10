package org.libx.libappdatabase;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.w3c.dom.events.EventTarget;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;

public class EditableXMLTextField {
    private Text domText;
    private Textbox editableTextbox;

    public EditableXMLTextField(Node entry, Description desc, Component parent) {
        this.editableTextbox = new Textbox();
        String xpath = desc.xpath;
        Node textNode = XPathSupport.evalNode(xpath, entry);
        this.domText = (org.w3c.dom.Text)textNode.getFirstChild();
        editableTextbox.setHflex("1");
        editableTextbox.setValue(textNode.getFirstChild().getTextContent());
        
        ((EventTarget)domText).addEventListener("DOMCharacterDataModified", new org.w3c.dom.events.EventListener() {
            @Override
            public void handleEvent(org.w3c.dom.events.Event event) {
                Text eventText = (Text)event.getTarget();
                if (!editableTextbox.getValue().contentEquals(eventText.getData())) {
                    //suppress DOMCharacterDataModified fired on domText
                    editableTextbox.setValue(eventText.getData());
                }
            }} , false
        );

        editableTextbox.addEventListener(Events.ON_CHANGE, new EventListener() {
            public void onEvent(Event event) throws Exception {
                domText.setData(editableTextbox.getValue());
                // this will trigger DOMCharacterDataModified on domText , and also
                // DOMSubtreeModified on all ancestors.
            }
        });

        Label textField = new Label();
        textField.setValue(desc.field);
        editableTextbox.setRows(desc.rows);
        editableTextbox.setHflex("1");
        parent.appendChild(textField);
        parent.appendChild(editableTextbox);
    }

    public static class Description extends MultipleXMLFieldsController.AbstractFieldCreator implements MultipleXMLFieldsController.FieldCreator{
        String field;
        String xpath;
        int rows;
        public Description (String field, String xpath, int rows) throws XPathExpressionException {
            this.field = field;
            this.xpath = xpath;
            this.rows = rows;
        }
        @Override
        public void createField(Node fieldNode, Component parent) {
            super.createField(fieldNode, parent);
            new EditableXMLTextField(fieldNode, this, parent);
        }
    }
}
