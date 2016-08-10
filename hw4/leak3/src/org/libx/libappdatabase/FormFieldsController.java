package org.libx.libappdatabase;

import javax.xml.xpath.XPathExpressionException;

import org.libx.libappdatabase.EditableXMLChoiceField.Choice;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.EventTarget;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zkplus.databind.AnnotateDataBinder;
import org.zkoss.zul.Box;
import org.zkoss.zul.Button;
import org.zkoss.zul.Caption;
import org.zkoss.zul.Groupbox;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Html;
import org.zkoss.zul.Label;
import org.zkoss.zul.Row;
import org.zkoss.zul.Rows;
import org.zkoss.zul.Vbox;

public class FormFieldsController extends Box {
    private AnnotateDataBinder binder;
    private DisplayPropertyBean displayProperties;    
    private XMLDataModel xmlModel;
    private Rows xmlEditor;
    private Vbox xmlFields;

    void cleanAnnotations () {
        refreshAnnotations();
    }

    void prepareForNewEntry() {
    }

    String getEntryType(Node target) {
        String entryType = XPathSupport.evalString("local-name(//libx:package|//libx:libapp|//libx:module)", target); //$NON-NLS-1$
        return entryType;
    }

    /* This bean is tied to various ZUL attributes of element in the GUI that reflect the
     * current state of the FormFieldsController.  Must be public for bindings to work. */ 
    public static class DisplayPropertyBean {
        private String selectedId;
        private Node selectedEntry;

        public String getActiveEntryId() {
            return selectedId;
        }
        public String getActiveEntryType() {
            return XPathSupport.evalString("concat(substring('Package',0,100*boolean(//libx:package))," + //$NON-NLS-1$
                                              "substring('Libapp',0,100*boolean(//libx:libapp)),substring('Module',0,100*boolean(//libx:module)))",  //$NON-NLS-1$
                                              selectedEntry);
        }
        public String getActiveEntryLastUpdated() {
            return XPathSupport.evalString("./atom:updated/text()", selectedEntry); //$NON-NLS-1$
        }
        void setSelectedEntry(String selectedId, Node selectedEntry) {
            this.selectedId = selectedId;
            this.selectedEntry = selectedEntry;
        }
    }

    void refreshAnnotations() {
        binder.saveAll();
        binder.loadAll();
    }

    public void registerWithTreeController(final TreeController packageTreeController) {
        packageTreeController.addListener(new TreeController.ListenerBase() {
            @Override
            public void newEntryCreated(Element parentItem, String entryType) {
                prepareForNewEntry();
            }

            @Override
            public void entrySelected(String docName, String id) {
                try {
                    final Element target = xmlModel.getEntry(docName, id);
                    displayProperties.setSelectedEntry(packageTreeController.getSelectedId(), target);
                    setVisible(true);
                    refreshAnnotations();
                    setEntryFieldsEditor(packageTreeController, target, xmlModel, xmlEditor, docName);
                    Node libxEntry = XPathSupport.evalNode("./libx:package|./libx:libapp|./libx:module", target); //$NON-NLS-1$
                    setLibXFieldsEditor(libxEntry, xmlFields, xmlModel, docName);
                }
                catch (Exception ex) {
                    Utils.alert(ex);
                }
            }
        });

    }

    public void initialize(final XMLDataModel xmlModel, final Rows xmlEditor, final Vbox xmlFields) {
        this.xmlModel = xmlModel;
        this.xmlEditor = xmlEditor;
        this.xmlFields = xmlFields;

        // set up annotations
        binder = new AnnotateDataBinder(this);
        displayProperties = new DisplayPropertyBean();
        binder.bindBean("entrydisplay", displayProperties); //$NON-NLS-1$
    }

    public void addEntryRows (Rows xmlEditorRows, Node target) throws XPathExpressionException {
        EditableXMLTextField.Description[] entryFieldDescription = new EditableXMLTextField.Description[] { 
            new EditableXMLTextField.Description(Messages.getString("FFC_titleLabel"), "./atom:title", 1),  //$NON-NLS-1$ //$NON-NLS-2$
            new EditableXMLTextField.Description(Messages.getString("FFC_authorLabel"), "./atom:author/atom:name", 1),  //$NON-NLS-1$ //$NON-NLS-2$
            new EditableXMLTextField.Description(Messages.getString("FFC_authorURILabel"), "./atom:author/atom:uri", 1),  //$NON-NLS-1$ //$NON-NLS-2$
            new EditableXMLTextField.Description(Messages.getString("FFC_authorEmailLabel"), "./atom:author/atom:email", 1) }; //$NON-NLS-1$ //$NON-NLS-2$
        for (int i = 0; i < entryFieldDescription.length; i++) {                
            Row entryField = new Row();
            xmlEditorRows.appendChild(entryField);
            new EditableXMLTextField(target, entryFieldDescription[i], entryField);
        }
    }

    public void setEntryFieldsEditor(final TreeController packageTree, final Node target, final XMLDataModel xmlModel, final Rows xmlEditorRows, final String docName) throws XPathExpressionException {
        final String selectedId = packageTree.getSelectedId();

        EditableXMLTextField.Description[] moduleDescription = new EditableXMLTextField.Description[] {
            new EditableXMLTextField.Description(Messages.getString("FFC_moduleBodyLabel"), "./libx:module/libx:body", 20) }; //$NON-NLS-1$ //$NON-NLS-2$

        xmlEditorRows.getChildren().clear();
        if(getEntryType(target).contentEquals("package") || getEntryType(target).contentEquals("libapp") ) { //$NON-NLS-1$ //$NON-NLS-2$
            addEntryRows(xmlEditorRows, target);
        } else if (getEntryType(target).contentEquals("module")) { //$NON-NLS-1$
            addEntryRows(xmlEditorRows, target);
            Row moduleBodyField = new Row();
            xmlEditorRows.appendChild(moduleBodyField);
            new EditableXMLTextField(target, moduleDescription[0], moduleBodyField);
        }

        ((EventTarget) target).addEventListener("DOMSubtreeModified", new org.w3c.dom.events.EventListener() { //$NON-NLS-1$
            private boolean handlingEvent = false;
            @Override
            public void handleEvent(org.w3c.dom.events.Event event) {
                if (handlingEvent)
                    return;
                try {
                    handlingEvent = true;
                    // the updated XML is saved in the database via XUpdate
                    // and all other dependents are updated
                    xmlModel.putEntry(docName, selectedId, (Element) target);
                    refreshAnnotations();
                    Utils.outputDOMEvent(event);
                } catch (Exception ex) {
                    Utils.alert(ex);
                } finally {
                    handlingEvent = false;
                }
            }
        }, false);
    }

    Groupbox createGroupBox(String captionName) {
        final Groupbox groupBox = new Groupbox();
        Caption caption = new Caption();
        Label label = new Label(captionName);
        caption.appendChild(label);
        groupBox.appendChild(caption);
        return groupBox;
    }

    MultipleXMLFieldsController.NodeProducer createLibxFieldNode (final String fieldName, final String fieldText) {
        MultipleXMLFieldsController.NodeProducer newLibXFieldNode = new MultipleXMLFieldsController.NodeProducer () {
            @Override
            public Node addNode(Node parent) {
                Element libxFieldNode = parent.getOwnerDocument().createElementNS(XMLDataModel.LIBX_NS, fieldName);
                libxFieldNode.setTextContent(fieldText);
                parent.appendChild(libxFieldNode);
                return libxFieldNode;
            }
        };
        return newLibXFieldNode;
    }

    MultipleXMLFieldsController.FieldCreator[] createLibxFieldDescription (String fieldLabel) throws XPathExpressionException {
        final MultipleXMLFieldsController.FieldCreator[] libxFieldDescription = new MultipleXMLFieldsController.FieldCreator[] { new EditableXMLTextField.Description(fieldLabel, ".", 1) }; //$NON-NLS-1$
        return libxFieldDescription;
    }

    private MultipleXMLFieldsController.NodeProducer produceParamNode = new MultipleXMLFieldsController.NodeProducer() {
        @Override
        public Node addNode(Node parent) {
            Element paramNode = parent.getOwnerDocument().createElementNS(XMLDataModel.LIBX_NS, "param"); //$NON-NLS-1$
            paramNode.setAttribute("name", Messages.getString("FFC_paramName")); //$NON-NLS-1$ //$NON-NLS-2$
            paramNode.setAttribute("type", Messages.getString("FFC_paramType")); //$NON-NLS-1$ //$NON-NLS-2$
            Element description = paramNode.getOwnerDocument().createElementNS(XMLDataModel.LIBX_NS, "description"); //$NON-NLS-1$
            description.setTextContent(Messages.getString("FFC_paramDescription")); //$NON-NLS-1$
            paramNode.appendChild(description);
            parent.appendChild(paramNode);
            return paramNode;
        }
    };

    private MultipleXMLFieldsController.NodeProducer produceArgumentNode = new MultipleXMLFieldsController.NodeProducer() {
        @Override
        public Node addNode(Node parent) {
            Element argNode = parent.getOwnerDocument().createElementNS(XMLDataModel.LIBX_NS, "arg"); //$NON-NLS-1$
            argNode.setAttribute("name", "name"); //$NON-NLS-1$ //$NON-NLS-2$
            argNode.setAttribute("type", "string"); //$NON-NLS-1$ //$NON-NLS-2$
            argNode.setAttribute("value", "value"); //$NON-NLS-1$ //$NON-NLS-2$
            parent.appendChild(argNode);
            return argNode;
        }
    };

    public void addLibxFieldBox (final Node parentNode, MultipleXMLFieldsController.FieldCreator[] libxFieldDescription, 
                                    Groupbox libxFieldBox, String xpath, String buttonName, MultipleXMLFieldsController.NodeProducer produceLibxFieldNode) throws XPathExpressionException {
        libxFieldBox.setHflex("1"); //$NON-NLS-1$
        new MultipleXMLFieldsController(libxFieldBox, xpath, buttonName, parentNode, libxFieldDescription, produceLibxFieldNode);
    }

    public void produceLibxFieldBox (String caption, final Vbox xmlFields, final String xpath, final Node parentNode, final String buttonName, 
                                        final MultipleXMLFieldsController.FieldCreator[] libxFieldDescription, final MultipleXMLFieldsController.NodeProducer produceLibxFieldNode) throws XPathExpressionException {
        final Groupbox libXFieldBox = createGroupBox(caption);
        libXFieldBox.setHflex("1"); //$NON-NLS-1$
        xmlFields.appendChild(libXFieldBox);
        NodeList libxFieldNodes = XPathSupport.evalNodeSet(xpath, parentNode);
        if (libxFieldNodes.getLength() == 0) {
            final Button addDefaultFieldButton = new Button(buttonName);
            libXFieldBox.appendChild(addDefaultFieldButton);
            addDefaultFieldButton.addEventListener(Events.ON_CLICK, new EventListener() {
                public void onEvent(Event event) throws Exception {
                    produceLibxFieldNode.addNode(parentNode);
                    addLibxFieldBox(parentNode, libxFieldDescription, libXFieldBox, xpath, buttonName, produceLibxFieldNode);
                    libXFieldBox.removeChild(addDefaultFieldButton);
                }
            });
        } else {
            addLibxFieldBox(parentNode, libxFieldDescription, libXFieldBox, xpath, buttonName, produceLibxFieldNode);
        }
    }

    public void setLibXFieldsEditor(final Node libxEntry, final Vbox xmlFields, final XMLDataModel xmlModel, final String docName) throws Exception {
        xmlFields.getChildren().clear();

        MultipleXMLFieldsController.FieldCreator[] includeDescription = createLibxFieldDescription(Messages.getString("FFC_includeLabel")); //$NON-NLS-1$
        MultipleXMLFieldsController.FieldCreator[] excludeDescription = createLibxFieldDescription(Messages.getString("FFC_excludeLabel")); //$NON-NLS-1$
        MultipleXMLFieldsController.FieldCreator[] requireDescription = createLibxFieldDescription(Messages.getString("FFC_requireLabel")); //$NON-NLS-1$
        MultipleXMLFieldsController.FieldCreator[] guardedByDescription = createLibxFieldDescription(Messages.getString("FFC_guardedByLabel")); //$NON-NLS-1$
        MultipleXMLFieldsController.FieldCreator[] producesDescription = createLibxFieldDescription(Messages.getString("FFC_producesLabel")); //$NON-NLS-1$

        MultipleXMLFieldsController.NodeProducer includeNode = createLibxFieldNode("include", Messages.getString("FFC_includeTextContent")); //$NON-NLS-1$ //$NON-NLS-2$
        MultipleXMLFieldsController.NodeProducer excludeNode = createLibxFieldNode("exclude", Messages.getString("FFC_excludeTextContent")); //$NON-NLS-1$ //$NON-NLS-2$
        MultipleXMLFieldsController.NodeProducer requireNode = createLibxFieldNode("require", Messages.getString("FFC_requireTextContent")); //$NON-NLS-1$ //$NON-NLS-2$
        MultipleXMLFieldsController.NodeProducer guardedByNode = createLibxFieldNode("guardedby", Messages.getString("FFC_guardedByTextContent")); //$NON-NLS-1$ //$NON-NLS-2$
        MultipleXMLFieldsController.NodeProducer producesNode = createLibxFieldNode("produces", Messages.getString("FFC_producesTextContent")); //$NON-NLS-1$ //$NON-NLS-2$

        produceLibxFieldBox(Messages.getString("FFC_includesCaption"), xmlFields, ".//libx:include", libxEntry, Messages.getString("FFC_addIncludeButton"), includeDescription, includeNode ); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        produceLibxFieldBox(Messages.getString("FFC_excludesCaption"), xmlFields, ".//libx:exclude", libxEntry, Messages.getString("FFC_addExcludeButton"), excludeDescription, excludeNode ); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        produceLibxFieldBox(Messages.getString("FFC_requiresCaption"), xmlFields, ".//libx:require", libxEntry, Messages.getString("FFC_addRequireButton"), requireDescription, requireNode); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        if (getEntryType(libxEntry).contentEquals("module")) { //$NON-NLS-1$
            produceLibxFieldBox(Messages.getString("FFC_guardedByCaption"), xmlFields, ".//libx:guardedby", libxEntry, Messages.getString("FFC_addGuardedByButton"), guardedByDescription, guardedByNode ); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            produceLibxFieldBox(Messages.getString("FFC_producesCaption"), xmlFields, ".//libx:produces", libxEntry, Messages.getString("FFC_addProducesCaption"), producesDescription, producesNode ); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        EditableXMLChoiceField.Choice[] choice = new EditableXMLChoiceField.Choice[] { 
            new EditableXMLChoiceField.Choice("String", "string"),  //$NON-NLS-1$ //$NON-NLS-2$
            new EditableXMLChoiceField.Choice("Number", "number"),  //$NON-NLS-1$ //$NON-NLS-2$
            new EditableXMLChoiceField.Choice("Boolean", "boolean"),  //$NON-NLS-1$ //$NON-NLS-2$
            new EditableXMLChoiceField.Choice("Enum", "enum") }; //$NON-NLS-1$ //$NON-NLS-2$

        final EditableXMLChoiceField.Description variableTypeChange = new EditableXMLChoiceField.Description(Messages.getString("FFC_paramTypeLabel"), ".", "type", choice); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        final MultipleXMLFieldsController.FieldCreator[] optionDescription = new MultipleXMLFieldsController.FieldCreator[] { 
            new EditableXMLAttributeField.Description(Messages.getString("FFC_paramOptionLabel"), ".", "value") }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        final MultipleXMLFieldsController.NodeProducer produceOptionNode = new MultipleXMLFieldsController.NodeProducer() {
            @Override
            public Node addNode(Node parent) {
                Element optionNode = parent.getOwnerDocument().createElementNS(XMLDataModel.LIBX_NS, "option"); //$NON-NLS-1$
                optionNode.setAttribute("value", Messages.getString("FFC_paramOption")); //$NON-NLS-1$ //$NON-NLS-2$
                parent.appendChild(optionNode);
                return optionNode;
            }
        };

        variableTypeChange.setListener(new EditableXMLChoiceField.Listener() {

            @Override
            public void onSelect(Choice choice) {
                Component parent = variableTypeChange.getContainer();
                System.out.println("associated onSelect parent=" + parent);
                Node parentNode = variableTypeChange.getFieldNode();
                final Groupbox optionsBox = createGroupBox(Messages.getString("FFC_optionsCaption")); //$NON-NLS-1$
                if (choice.value.contentEquals("enum")) { //$NON-NLS-1$
                    NodeList optionNodes = XPathSupport.evalNodeSet("./libx:option", parentNode);
                    if (optionNodes.getLength() == 0) {
                        produceOptionNode.addNode(parentNode);
                    }
                    new MultipleXMLFieldsController(optionsBox, "./libx:option", Messages.getString("FFC_addOptionButton"), parentNode, optionDescription, produceOptionNode); //$NON-NLS-1$ //$NON-NLS-2$
                    // setParent below is the same as: parent.appendChild(optionsBox);
                    // verify that in fact it's ok to append optionsBox at the end after all other
                    // siblings.
                    optionsBox.setParent(parent);
                    System.out.println("adding as last child to parent=" + parent);

                    optionsBox.setWidth("80%");
                    if (!optionsBox.isVisible())
                        optionsBox.setVisible(true);
                } else {
                   optionsBox.setVisible(false);
                }
            }
        });

        final MultipleXMLFieldsController.FieldCreator[] paramDescription = new MultipleXMLFieldsController.FieldCreator[] { 
            new EditableXMLAttributeField.Description(Messages.getString("FFC_paramNameLabel"), ".", "name"),  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new EditableXMLTextField.Description(Messages.getString("FFC_paramDescriptionLabel"), "./libx:description", 4),  //$NON-NLS-1$ //$NON-NLS-2$
            variableTypeChange };

        final Groupbox paramsBox = createGroupBox(Messages.getString("FFC_paramsCaption")); //$NON-NLS-1$
        paramsBox.setHflex("1"); //$NON-NLS-1$
        xmlFields.appendChild(paramsBox);
        Node paramsNode = XPathSupport.evalNode(".//libx:params", libxEntry); //$NON-NLS-1$
        if (paramsNode == null) {
            final Button addDefaultParamsButton = new Button(Messages.getString("FFC_addDefaultParamsButton")); //$NON-NLS-1$
            paramsBox.appendChild(addDefaultParamsButton);
            addDefaultParamsButton.addEventListener(Events.ON_CLICK, new EventListener() {
                public void onEvent(Event event) throws Exception {
                    Element paramsNode = libxEntry.getOwnerDocument().createElementNS("http://libx.org/xml/libx2", "params"); //$NON-NLS-1$ //$NON-NLS-2$
                    produceParamNode.addNode(paramsNode);
                    libxEntry.appendChild(paramsNode);
                    addLibxFieldBox(paramsNode, paramDescription, paramsBox, "./libx:param", Messages.getString("FFC_addParamButton"), produceParamNode); //$NON-NLS-1$ //$NON-NLS-2$
                    paramsBox.removeChild(addDefaultParamsButton);
                }
            });
        } else {
            addLibxFieldBox(paramsNode, paramDescription, paramsBox, "./libx:param", Messages.getString("FFC_addParamButton"), produceParamNode); //$NON-NLS-1$ //$NON-NLS-2$
        }

        Groupbox entriesBox = createGroupBox(Messages.getString("FFC_entriesCaption")); //$NON-NLS-1$
        entriesBox.setHflex("1"); //$NON-NLS-1$
        new MultipleXMLEntriesController(entriesBox, ".//libx:entry", libxEntry, new MultipleXMLEntriesController.ComponentCreator() { //$NON-NLS-1$
            private NodeList idNodeParamNodes = null;
            private String[] paramNames;
            private String idNodeType;

            EditableXMLChoiceField.Choice[] choice = new EditableXMLChoiceField.Choice[] { 
                new EditableXMLChoiceField.Choice("String", "string"),  //$NON-NLS-1$ //$NON-NLS-2$
                new EditableXMLChoiceField.Choice("Number", "number"),  //$NON-NLS-1$ //$NON-NLS-2$
                new EditableXMLChoiceField.Choice("Boolean", "boolean"),  //$NON-NLS-1$ //$NON-NLS-2$
                new EditableXMLChoiceField.Choice("Enum", "enum") }; //$NON-NLS-1$ //$NON-NLS-2$

            final EditableXMLChoiceField.Description variableTypeChange = new EditableXMLChoiceField.Description(Messages.getString("FFC_argTypeLabel"), ".", "type", choice); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            {
                variableTypeChange.setListener(new EditableXMLChoiceField.Listener() {
                    Html validOptions;

                    @Override
                    public void onSelect(Choice choice) {
                        Component parent = variableTypeChange.getContainer();
                        Hbox argValueBox = new Hbox();
                        Node argNode = variableTypeChange.getFieldNode();
                        Attr nameAttribute = (org.w3c.dom.Attr) argNode.getAttributes().getNamedItem("name"); //$NON-NLS-1$
                        String argName = nameAttribute.getTextContent();
                        if (choice.value.contentEquals("string") || choice.value.contentEquals("number")) { //$NON-NLS-1$ //$NON-NLS-2$
                            EditableXMLAttributeField.Description desc = null;
                            desc = new EditableXMLAttributeField.Description("Value", ".", "value"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            new EditableXMLAttributeField(argNode, desc, argValueBox);
                            parent.appendChild(argValueBox);
                        } else if (choice.value.contentEquals("boolean")) { //$NON-NLS-1$
                            EditableXMLCheckboxField.Description checkboxDesc = null;
                            checkboxDesc = new EditableXMLCheckboxField.Description("Value", ".", "value"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            new EditableXMLCheckboxField(argNode, checkboxDesc, argValueBox);
                            parent.appendChild(argValueBox);
                        } else if (choice.value.contentEquals("enum")) { //$NON-NLS-1$
                            EditableXMLAttributeField.Description desc = null;
                            desc = new EditableXMLAttributeField.Description(Messages.getString("FFC_argValueLabel"), ".", "value"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            new EditableXMLAttributeField(argNode, desc, argValueBox);
                            for (int i = 0; i < paramNames.length; i++) {
                                if (argName.contentEquals(paramNames[i])) {
                                    Node paramNode = idNodeParamNodes.item(i);
                                    NodeList optionNodes = XPathSupport.evalNodeSet("./libx:option", paramNode); //$NON-NLS-1$
                                    int length = optionNodes.getLength();
                                    String optionValues[] = new String[length];
                                    for (int j = 0; j < length; j++) {
                                        Attr optionValueAttr = (org.w3c.dom.Attr) optionNodes.item(j).getAttributes().getNamedItem("value"); //$NON-NLS-1$
                                        optionValues[j] = optionValueAttr.getTextContent();
                                    }
                                    this.validOptions = new Html();
                                    validOptions.setContent(Messages.getString("FFC_argValuesMessage")); //$NON-NLS-1$
                                    for (int k = 0; k < length; k++) {
                                        validOptions.setContent(validOptions.getContent() + " " + optionValues[k]); //$NON-NLS-1$
                                    }
                                    break;
                                } else
                                    continue;
                            }
                            parent.appendChild(new Vbox(new Component [] {argValueBox, validOptions}));
                        }
                    }
                });
            }

            MultipleXMLFieldsController.FieldCreator[] argDescription = new MultipleXMLFieldsController.FieldCreator[] { 
                new EditableXMLAttributeField.Description(Messages.getString("FFC_argNameLabel"), ".", "name"),  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                variableTypeChange };

            @Override
            public void createComponentsForNode(Component subEntryBox, final Node entryNode) throws XPathExpressionException {
                String titleValue;
                Attr srcAttribute = (org.w3c.dom.Attr) entryNode.getAttributes().getNamedItem("src"); //$NON-NLS-1$
                String id = srcAttribute.getTextContent();
                Node idNode = null;
                try {
                    idNode = xmlModel.getEntry(docName, id);
                    idNodeType = XPathSupport.evalString("concat(substring('Package',0,100*boolean(//libx:package))," + //$NON-NLS-1$
                    		                                "substring('Libapp',0,100*boolean(//libx:libapp))," + //$NON-NLS-1$
                    		                                "substring('Module',0,100*boolean(//libx:module)))", idNode); //$NON-NLS-1$
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                Node titleNode = XPathSupport.evalNode("./atom:title", idNode); //$NON-NLS-1$
                titleValue = titleNode.getTextContent();

                idNodeParamNodes = XPathSupport.evalNodeSet(".//libx:params/libx:param", idNode); //$NON-NLS-1$
                int paramNodesCount = idNodeParamNodes.getLength();
                paramNames = new String[paramNodesCount];
                for (int k = 0; k < paramNodesCount; k++) {
                    Node paramNode = idNodeParamNodes.item(k);
                    Attr nameAttribute = (org.w3c.dom.Attr) paramNode.getAttributes().getNamedItem("name"); //$NON-NLS-1$
                    paramNames[k] = nameAttribute.getTextContent();
                }

                Html title = new Html();
                title.setContent(idNodeType + ": " + titleValue); //$NON-NLS-1$
                subEntryBox.appendChild(title);

                final Groupbox argsBox = createGroupBox(Messages.getString("FFC_argsCaption")); //$NON-NLS-1$
                argsBox.setHflex("1"); //$NON-NLS-1$
                subEntryBox.appendChild(argsBox);
                Node argsNode = XPathSupport.evalNode("./libx:args", entryNode); //$NON-NLS-1$
                if (argsNode == null) {
                    final Button addDefaultArgsButton = new Button(Messages.getString("FFC_addDefaultArgsButton")); //$NON-NLS-1$
                    argsBox.appendChild(addDefaultArgsButton);
                    addDefaultArgsButton.addEventListener(Events.ON_CLICK, new EventListener() {
                        public void onEvent(Event event) throws Exception {
                            Element argsNode = entryNode.getOwnerDocument().createElementNS("http://libx.org/xml/libx2", "args"); //$NON-NLS-1$ //$NON-NLS-2$
                            produceArgumentNode.addNode(argsNode);
                            entryNode.appendChild(argsNode);
                            addLibxFieldBox(argsNode, argDescription, argsBox, "./libx:arg", Messages.getString("FFC_addArgButton"), produceArgumentNode); //$NON-NLS-1$ //$NON-NLS-2$
                            argsBox.removeChild(addDefaultArgsButton);
                        }
                    });
                } else {
                    addLibxFieldBox(argsNode, argDescription, argsBox, "./libx:arg", Messages.getString("FFC_addArgButton"), produceArgumentNode); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
        });

        int entriesSize = entriesBox.getChildren().size();
        if (entriesSize > 1) {
            xmlFields.appendChild(entriesBox);
        }
    }
}
