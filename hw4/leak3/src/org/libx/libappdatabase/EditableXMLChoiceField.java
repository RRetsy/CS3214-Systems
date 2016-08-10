package org.libx.libappdatabase;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.events.EventTarget;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Groupbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.ListModelList;
import org.zkoss.zul.Listbox;

public class EditableXMLChoiceField {
    private Attr domAttr;
    private Listbox typesList;
    private Groupbox optionsbox;

    public EditableXMLChoiceField(Node node, Description desc, Component parent, Choice [] choice) throws XPathExpressionException {
        this(node, desc, parent, choice, Listener.EMPTY);
    }
    public EditableXMLChoiceField(Node node, Description desc, Component parent, Choice [] choice, final Listener l) throws XPathExpressionException {
        this.optionsbox = new Groupbox();
        this.typesList = new Listbox();
        typesList.setMold("select");
        String xpath = desc.xpath;
        Node attrNode = XPathSupport.evalNode(xpath, node);
        this.domAttr = (org.w3c.dom.Attr)attrNode.getAttributes().getNamedItem(desc.attribute);
        String attrValue = domAttr.getValue();

        Label attrField = new Label();
        attrField.setValue(desc.field);
        typesList.setHflex("1");
        parent.appendChild(attrField);
        parent.appendChild(typesList);
        parent.appendChild(optionsbox);

        final ListModelList liveListModel = new ListModelList(choice);
        typesList.setModel(liveListModel);
        for(int i = 0; i < choice.length; i ++) {
            if (choice[i].value.contentEquals(attrValue)) {
                l.onSelect(choice[i]);
                typesList.setSelectedItem(typesList.getItemAtIndex(i));
                break;
            }    
        }

        optionsbox.setVisible(false);
       
        ((EventTarget)domAttr).addEventListener("DOMAttrModified", new org.w3c.dom.events.EventListener() {
            @Override
            public void handleEvent(org.w3c.dom.events.Event event) {
                Attr eventAttr = (Attr)event.getTarget();
                if (!typesList.getSelectedItem().getValue().equals(eventAttr.getValue())) {
                    // suppress DOMAttrModified fired on domAttr
                    typesList.getSelectedItem().setValue(eventAttr.getValue());
                }
            }} , false
        );

        typesList.addEventListener(Events.ON_SELECT, new EventListener() {
            public void onEvent(Event event) throws Exception {
                Choice choice = (Choice) ((Listbox) event.getTarget()).getSelectedItem().getValue();
                domAttr.setValue(choice.value);
                l.onSelect(choice);
                // this will trigger DOMAttrModified on domAttr , and also
                // DOMSubtreeModified on all ancestors.
            }
        });
    }

    public static class Description extends MultipleXMLFieldsController.AbstractFieldCreator implements MultipleXMLFieldsController.FieldCreator {
        String field;
        String xpath;
        String attribute;
        Choice [] choice;
        Listener l;
        public Description (String field, String xpath, String attribute, Choice [] choice) throws XPathExpressionException {
            this(field, xpath, attribute, choice, Listener.EMPTY);
        }
        public Description (String field, String xpath, String attribute, Choice [] choice, Listener l1) throws XPathExpressionException {
            this.field = field;
            this.xpath = xpath;
            this.attribute = attribute;
            this.choice = choice;
        }
        public void setListener (Listener l) {
            this.l = l;            
        }
        @Override
        public void createField(Node fieldNode, Component parent) {
            super.createField(fieldNode, parent);
            try {
                new EditableXMLChoiceField(fieldNode, this, parent, choice, l);
            } catch (XPathExpressionException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public static class Choice {
        String label;
        String value;

        public Choice (String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String toString () {
            return label;
        }
    }

    interface Listener {
        public void onSelect (Choice c);
        static Listener EMPTY = new Listener () {
            @Override
            public void onSelect(Choice c) {           
            }            
        };
    };
}
