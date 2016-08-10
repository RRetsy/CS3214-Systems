package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.libx.editionbuilder.Utils.BeanPropertyComponentCreator;
import org.libx.editionbuilder.Utils.Valuebox;

import org.libx.xml.Bookmarklet;
import org.libx.xml.Catalogs;
import org.libx.xml.CatalogsItem;
import org.libx.xml.Searchoption;
import org.libx.xml.Searchoptions;

import org.zkoss.zk.ui.Component;

import org.zkoss.zk.ui.event.DropEvent;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;

import org.zkoss.zul.Button;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Column;
import org.zkoss.zul.Columns;
import org.zkoss.zul.Grid;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Row;
import org.zkoss.zul.Rows;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Vbox;
import org.zkoss.zul.Window;


/**
 * This is the widget that displays the readable list of options and also contains an embedded window that enables a user to 
 * change the options.  
 * @author tgaat
 *
 */

public class OptionsWidgetController {

    Component optionsWindow;                // currently displaying options window
    HashMap<String, Code> code2label = new HashMap<String, Code>();
    private ArrayList<OptionsReadableLabelBox> attachedOptionLabelBoxes = new ArrayList<OptionsReadableLabelBox>();

    /**
     * OptionReadableLabels are created as a by-product of loading catalogs.
     * On a model change, the list of attachedOptions must be reset before new ones
     * are added.  We cannot do this in a model change notification because this
     * must be guaranteed to happen BEFORE new catalogs are loaded.
     *
     * Called from CatalogTabController's model change callback.
     */
    void resetOptionLabelList() {
        attachedOptionLabelBoxes = new ArrayList<OptionsReadableLabelBox>();
    }

    public OptionsWidgetController() throws Exception {

        /**
         * Attaches a listener to a model, that populates the code2label hashmap after each  model load, 
         * as the code2label hashmap should contain the searchoptions specific to the model.
         * 
         */
        Model.addCurrentModelChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    populateCode2label();

                    Model model = Model.getCurrentModel();

                    model.addPropertyChangeListener(new PropertyChangeListener() {

                        /**
                         * After each model load, the following listener listens to changes in the label of any searchoptions.
                         * Any change should be reflected across all the catalog options, 
                         * owing to the fact that the set of searchoptions is common to all the catalogs. 
                         */
                        public void propertyChange(PropertyChangeEvent evt) {
                            if ("label".equals(evt.getPropertyName()) ) {
                                try {
                                    for (OptionsReadableLabelBox orbox : attachedOptionLabelBoxes) {
                                        orbox.updateLabel();
                                    }
                                } catch (Exception e) {
                                    Utils.logUnexpectedException(e);
                                }
                            }

                            /**
                             * After each model load, the following listener listens to changes in the list of searchoptions.
                             * Any additions or subtractions from the list of searchOptions should also be reflected across all the 
                             * catalogs, as the searchoptions are common to all the catalogs. 
                             */

                            if ("searchoptionList".equals(evt.getPropertyName())) {                                
                                populateCode2label();
                                for (OptionsReadableLabelBox orbox : attachedOptionLabelBoxes) {
                                    try {
                                        orbox.notifySearchOptionsListChange();
                                    } catch (Exception e) {
                                        Utils.logUnexpectedException(e);
                                    }                 
                                }
                            }
                        }
                    });


                } catch (Exception e) {
                    MainWindowController.showException(e);
                }
            }
        });        
    }

    /**
     * Check whether a given code is defined in the current model.
     */
    boolean hasCode(String code) {
        return code2label.containsKey(code);
    }

    /**
     * The class Code contains informationation about each code; namely the label corresponding to the code and whether it is built in or whether it 
     * is custom added by the user. 
     * @author tgaat
     *
     */
    static class Code {
        String label;
        boolean isBuiltin;

        Code(String lab, boolean ib) {
            this.label = lab;
            this.isBuiltin = ib;
        }
    }


    /**
     * Populates the hashmap code2label from 2 sources - the enum SearchOptions containing the built in options
     *  and the searchoptions specific to the model.  
     *
     */
    private void populateCode2label() { 

        code2label.clear();

        for (SearchOptions so : SearchOptions.values()) {
            code2label.put(so.code, new Code(so.label,true));
        }

        Searchoptions soptionsFromModel = Model.getCurrentModel().getEdition().getSearchoptions();

        for (Searchoption so : soptionsFromModel.getSearchoption()) {
            boolean isBuiltin = code2label.containsKey(so.getValue());
            code2label.put(so.getValue(), new Code(so.getLabel(), isBuiltin));
        }
    }

    /**
     * This is the controller for the component that is to be drawn for the "options" property.
     * @author tgaat
     *
     */

    public static class OptionsComponentController extends Utils.KeyValueOption implements BeanPropertyComponentCreator {
        public Component createComponentForProperty(Object catalog) {
            try {
                return new OptionsReadableLabelBox(catalog,
                        Utils.beanPropertyGetter(catalog, "options"), 
                        Utils.beanPropertySetter(catalog, "options", String.class),
                        Utils.beanPropertyGetter(catalog, "contextmenuoptions"),
                        Utils.beanPropertySetter(catalog, "contextmenuoptions", String.class)
                       );
            } catch (Exception e) {
                Utils.logUnexpectedException(e);
            }
            return null;
        }

        OptionsComponentController (String key1, String defvalue) { 
            super(key1, defvalue);
        }
    }

    /**
     * This is the component that is drawn for the options property. It contains a label that represents the readable list of options, 
     * a button that allows the user to change the options. The window containing the options is also attached to this Vbox.
     * OptionsReadableLabelBox contains a vector called OptionsVector used essentially to determine the order in which the options
     * have to be displayed. Also the presentOptionsBitset keeps track of which option is currently selected. 
     * @author tgaat
     */

    public static class OptionsReadableLabelBox extends org.zkoss.zul.Vbox {
        private final Object catalog;
        private final OptionsWidgetController optionsWidgetController;
        private final Method optionsGetter, ctxtMenuOptionsGetter;
        private final Method optionsSetter, ctxtMenuOptionsSetter;
        private final Vector<String> optionsVector;
        private final BitSet presentOptionsBitset;
        private final Label optionsReadableLabel;

        OptionsReadableLabelBox (Object cat, Method optionsGetter, Method optionsSetter, Method ctxtMenuOptionsGetter, Method ctxtMenuOptionsSetter) throws Exception {
            this.setWidth("100%");

            this.catalog = cat;
            this.optionsGetter = optionsGetter;
            this.optionsSetter = optionsSetter;
            this.ctxtMenuOptionsGetter = ctxtMenuOptionsGetter;
            this.ctxtMenuOptionsSetter = ctxtMenuOptionsSetter;
            this.optionsWidgetController = (OptionsWidgetController)Utils.getDesktopAttribute("optionsWidgetController");
            this.optionsVector = new Vector<String>();
            this.presentOptionsBitset = new BitSet();

            String options = (String)optionsGetter.invoke(catalog);
            if (options == null)
                options = "";

            String[] codes = options.split(";");

            String correctOptionString = "";

            if (!"".equals(options)) {
                for (String code : codes) {          
                    if(!this.optionsWidgetController.code2label.keySet().contains(code)) {
                        String msg  = "Warning: dropped invalid code "+code+" from options";
                        Utils.printLog("%s", msg);
                        MainWindowController.showStatus(StatusCode.WARNING, msg);
                    } else {
                        correctOptionString = correctOptionString+code+";";
                    }
                }
            }

            options = correctOptionString.equals("")?"":correctOptionString.substring(0,correctOptionString.length() - 1);
            setOption(options);

            Button changeOptionsButton = new Button("Change");

            changeOptionsButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                public void onEvent(Event e) {     
                    displayOptionsWindow();
                }                                      
            }); 

            recordOptionsPresence();
            addUsedOptionsToModelIfNeeded(); 

            optionsWidgetController.attachedOptionLabelBoxes.add(this);
            this.optionsReadableLabel = new Label();
            updateLabel();

            PropertyChangeListener optionChangeListener = new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    try {
                        if ("options".equals(evt.getPropertyName()) ) {  
                            updateLabel();
                        }
                    } catch (Exception e) {
                        Utils.logUnexpectedException(e);
                    }
                }
            };

            Utils.addBeanPropertyChangeListener(this.catalog, optionChangeListener);

            Hbox hb = new Hbox(new Component[] { this.optionsReadableLabel, changeOptionsButton });
            this.appendChild(hb);
        }

        void updateLabel() {
            optionsReadableLabel.setValue(returnReadableOptions());
        }

        private void setOption(String searchOptionString) throws IllegalAccessException, InvocationTargetException {
            this.optionsSetter.invoke(this.catalog, searchOptionString);
        }

        void notifySearchOptionsListChange() throws Exception {             
            recordOptionsPresence();
        }

        /**
         * Create and display a new popup.
         *
         * This method ensures that only 1 options window is open at a time. 
         * If any other window is open, it closes that one
         * and opens the currently selected window. 
         */
        private void displayOptionsWindow() {
            try {
                // create new window
                OptionsPopupWindow oPopupWindow = optionsWidgetController.new OptionsPopupWindow(OptionsReadableLabelBox.this);

                OptionsReadableLabelBox.this.appendChild(oPopupWindow);
                if (this.optionsWidgetController.optionsWindow != null) {
                    this.optionsWidgetController.optionsWindow.setParent(null);
                }

                this.optionsWidgetController.optionsWindow = oPopupWindow;

                oPopupWindow.setWidth("60%");       
                oPopupWindow.setLeft("10%");
                oPopupWindow.doModal();
            } catch (Exception e) {
                Utils.logUnexpectedException(e);
            }
        }

        Set<String> getAllowedOptionsSet(Class catalogClass) {
            /* Adopt a robust programming style. */
            Constraints.OptionConstraint optionConstraint = Constraints.lookupOptionConstraint(catalogClass);
            if (optionConstraint == null) {
                Utils.printLog("Warning: option constraint missing for %s"
                        + " substituting bookmarklet options", catalogClass);
                optionConstraint = Constraints.lookupOptionConstraint(Bookmarklet.class);
            }
            return optionConstraint.getValidOptions();
        }

        /**
         * populates the optionsVector initially from the set of options and sets the bits 
         * corresponding to the options that are present initially.   
         * @param arr
         */
        private void recordOptionsPresence() throws Exception {
            this.optionsVector.clear();
            this.presentOptionsBitset.clear();

            String optionString = (String)optionsGetter.invoke(this.catalog);
            String[] currentOptionsCodes = optionString.split(";");

            Set<String> allowedOptionsSet = getAllowedOptionsSet(this.catalog.getClass());

            Set<String> presentOptionsSet = new HashSet<String>();

            /**
             * The default options String for certain catalogs(e.g. Bookmarklets) is "".
             * In that case, a default list of options is added using the allowedOptionsSet
             */
            if(currentOptionsCodes.length == 1 && currentOptionsCodes[0] == "") {                
                int i = 0;
                for(String code : allowedOptionsSet) {                   
                    presentOptionsSet.add(code);
                    this.optionsVector.add(code);
                    this.presentOptionsBitset.clear(i);                   
                    i++;
                }
            }

            else {
                int i = 0;
                for(String code : currentOptionsCodes) {
                    presentOptionsSet.add(code);
                    this.optionsVector.add(code);
                    this.presentOptionsBitset.set(i);                   
                    i++;
                }

                HashSet<String> absentOptions = new HashSet<String>(this.optionsWidgetController.code2label.keySet());
                absentOptions.removeAll(presentOptionsSet);    

                boolean addOption = false;

                for (String code: absentOptions) {
                    if(Constraints.isAddOptionAllowed.contains(this.catalog.getClass()))
                        addOption = allowedOptionsSet.contains(code) || !this.optionsWidgetController.code2label.get(code).isBuiltin;
                    else
                        addOption = allowedOptionsSet.contains(code);

                    if(addOption) {                         
                        this.optionsVector.add(code);
                        this.presentOptionsBitset.clear(i);
                        i++;
                    }
                }
            }
        }

        /** 
         * Ensure that all used options are added to the model if needed.
         * Handles legacy editions that did not list built-in codes in their
         * model's search options.
         */ 
        private void addUsedOptionsToModelIfNeeded() {

            Searchoptions soptions = Model.getCurrentModel().getEdition().getSearchoptions();   
            // extract existing search option codes in set
            HashSet<String> modelSearchOptions = new HashSet<String>();
            for(Searchoption soption : soptions.getSearchoption()) {
                modelSearchOptions.add(soption.getValue());
            }

            for (int optindex = 0; optindex < presentOptionsBitset.length(); optindex++) {
                String optCode = this.optionsVector.get(optindex);
                if (presentOptionsBitset.get(optindex) && !modelSearchOptions.contains(optCode)) {
                    Searchoption soption = new Searchoption();
                    soption.setValue(optCode);
                    Code c = optionsWidgetController.code2label.get(optCode);
                    soption.setLabel(c.label);
                    soptions.addSearchoption(soption);
                }
            }
        }

        /**
         * This computes the string of readable options from their labels.
         * @return
         */

        public String returnReadableOptions() { 

            String expandedOptions="";
            String separator = ", ";

            try {
                String options = (String)this.optionsGetter.invoke(this.catalog);

                if(!options.equals(""))
                {
                    for(String code : options.split(";")) {
                        String lab = this.optionsWidgetController.code2label.get(code).label;          
                        expandedOptions = expandedOptions+lab+separator;
                    }
                    expandedOptions = expandedOptions.substring(0,expandedOptions.length()-separator.length());    
                }

            } catch (Exception exc) {
                Utils.logUnexpectedException(exc);
            }

            return expandedOptions;
        }  

        boolean getContextMenuPreference(String so) {
            try {
                String ctxt = (String)ctxtMenuOptionsGetter.invoke(catalog);
                if (ctxt == null) {
                    // implement legacy default context menu prefs
                    // add 'i' always, and add 'Y', 'a', and 't' for primary catalog
                    // context menu prefs are represented as a semicolon-separated list
                    // but unlike options, order does not matter. Therefore, simple
                    // string manipulation is sufficient
                    int catnr = 0;
                    for (CatalogsItem ci : Model.getCurrentModel().getEdition().getCatalogs().getCatalogsItem()) {
                        if (ci.getChoiceValue() == catalog)
                            break;
                        catnr++;
                    }

                    String options = ";" + (String)optionsGetter.invoke(catalog) + ";";

                    List<String> s = new ArrayList<String>();
                    if (catnr == 0) {
                        for (String o : new String [] { "Y", "a", "t" }) {
                            if (options.contains(";" + o + ";"))
                                s.add(o);
                        }
                    }

                    if (options.contains(";i;"))
                        s.add("i");

                    ctxt = Utils.Strings.join(s, ";");
                    ctxtMenuOptionsSetter.invoke(catalog, ctxt);
                }
                return (";" + ctxt + ";").contains(";" + so + ";");
            } catch (Exception e) {
                Utils.logUnexpectedException(e);
            }
            return false;
        }

        void removeContextMenuPreference(int index) {
            removeContextMenuPreference(optionsVector.get(index));
        }

        void removeContextMenuPreference(String so) {
            try {
                String ctxt = ";" + (String)ctxtMenuOptionsGetter.invoke(catalog) + ";";
                ctxt = ctxt.replace(";" + so + ";", ";").replaceFirst("^;", "").replaceFirst(";$", "");
                ctxtMenuOptionsSetter.invoke(catalog, ctxt);
            } catch (Exception e) {
                Utils.logUnexpectedException(e);
            }
        }

        void addContextMenuPreference(String so) {
            try {
                String ctxt = (String)ctxtMenuOptionsGetter.invoke(catalog);
                if ("".equals(ctxt))
                    ctxt = so;
                else
                    ctxt += ";" + so;
                ctxtMenuOptionsSetter.invoke(catalog, ctxt);
            } catch (Exception e) {
                Utils.logUnexpectedException(e);
            }
        }
    }

    /**
     * This enum contains all the built in options
     * @author tgaat
     *
     */
    public static enum SearchOptions {
        Y ("Y", "Keyword"), 
        t ("t", "Title"), 
        a ("a", "Author"),
        d ("d","Subject"),
        i ("i","ISBN/ISSN"), 
        is ("is","ISSN"), 
        c ("c","Call Number"),
        jt ("jt","Journal Title"), 
        at ("at","Article Title"),
        doi ("doi", "DOI"), 
        pmid ("pmid", "Pubmed Id");

        String code;
        String label;

        SearchOptions(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }

    /**
     * This Window contains the list of options that are valid for the particular catalog. It also gives the users provision to
     * select/deselect options, add and delete custom options etc.  
     * @author tgaat
     *
     */

    public class OptionsPopupWindow extends Window { 
        private Grid gridOptions;
        private OptionsReadableLabelBox oReadableLabelBox;
        private Button addButton;

        OptionsPopupWindow (OptionsReadableLabelBox oReadableLabelBox) throws Exception {
            this.setWidth("100%");
            this.setStyle("table-layout: fixed");
            this.setBorder("normal");
            this.setTitle("Select Options");
            this.setClosable(true);

            this.oReadableLabelBox = oReadableLabelBox;   

            attachOptionsGrid(this);  
        }

        /**
         * Attaches the grid of options and also an add button if allowed.
         * @param parent
         */
        public void attachOptionsGrid(Component parent) {
            this.gridOptions = drawGrid();
            this.gridOptions.setWidth("100%");
            Vbox vb = new Vbox();

            this.appendChild(gridOptions);

            if (Constraints.isAddOptionAllowed.contains(this.oReadableLabelBox.catalog.getClass())) {
                addButton = new Button("Add an Option");

                addButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                    public void onEvent(Event e) {      
                        try {
                            addButton.setDisabled(true);

                            // Appends a new OptionsRow in case of a custom added option.
                            OptionsPopupWindow.this.appendChild(new NewSearchOptionBox(new Searchoption()));
                        } catch (Exception e1) {
                            MainWindowController.showException(e1);
                        }
                    }
                });

                vb.appendChild(addButton);
            }

            vb.appendChild(new Label("You can drag and drop options to change their order"));
            this.appendChild(vb);
        }

        /**
         * This method checks if a particular option is selected or not, and accordingly attaches an OptionsRow to the 
         * grid of options.
         * @return
         */
        private Grid drawGrid() {
            Grid g = new Grid();
            g.setStyle("table-layout: fixed");
            final Rows rows = new Rows();

            Columns cols = new Columns();

            Column checkBoxColumn = new Column();
            checkBoxColumn.setWidth("4%");
            cols.appendChild(checkBoxColumn);

            Column labelCol = new Column();
            labelCol.setWidth("35%");
            cols.appendChild(labelCol);

            Column codeCol = new Column();
            codeCol.setWidth("20%");
            cols.appendChild(codeCol);

            Column ctxtMenu = new Column();
            ctxtMenu.setWidth("15%");
            cols.appendChild(ctxtMenu);

            Column changeDelCol = new Column();
            // no width
            cols.appendChild(changeDelCol);

            int i = 0;

            for (String code : this.oReadableLabelBox.optionsVector) {
                appendOptionsRow(rows, code, i);
                i++;
            }

            g.appendChild(cols);
            g.appendChild(rows);

            return g;
        }

        /**
         * Appends an OptionsRow
         */
        private void appendOptionsRow(Rows rows, String soption, int i) {
            boolean isChecked = this.oReadableLabelBox.presentOptionsBitset.get(i);
            OptionsRow r = new OptionsRow(isChecked, soption, i);
            rows.appendChild(r);
        }

        public class NewSearchOptionBox extends Hbox {

            private Searchoption searchOption;

            public NewSearchOptionBox(Searchoption searchOption) throws Exception {

                this.searchOption = searchOption;
                this.setAlign("end"); //Places components at the bottom
                this.setStyle("table-layout: fixed");

                final Valuebox labelBox = new Utils.Valuebox(
                        this.searchOption,
                        Utils.beanPropertyGetter(this.searchOption, "label"),
                        Utils.beanPropertySetter(this.searchOption, "label", String.class));
                labelBox.setValue("");
                Component helpLabel = Utils.createHelpText(Searchoption.class,"label");

                final Valuebox codeBox = new Utils.Valuebox(
                        this.searchOption,
                        Utils.beanPropertyGetter(this.searchOption, "value"),
                        Utils.beanPropertySetter(this.searchOption, "value", String.class));
                codeBox.setValue("");
                Component helpCode = Utils.createHelpText(Searchoption.class,"value");

                /**
                 * The submit button adds the newly added custom option to the list of searchoptions, computes the new grid
                 * and attaches it to the window. 
                 */
                Button submit = new Button("Submit");

                submit.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                    public void onEvent(Event e) {      
                        try {
                            addSubmitAction(labelBox, codeBox);
                            addButton.setDisabled(false);
                        } catch (Exception e1) {
                            MainWindowController.showException(e1);
                        }
                    }                   
                });

                Vbox vbLabel = new Vbox(new Component[]{helpLabel,labelBox});
                Vbox vbCode = new Vbox(new Component[]{helpCode, codeBox});
                this.appendChild(vbLabel);
                this.appendChild(vbCode);
                this.appendChild(submit);
                Utils.setHflexOnChildren(this, "3", "3", "4");
                this.setWidth("100%");
            }

            private void addSubmitAction(final Valuebox labelBox, final Valuebox codeBox) throws InterruptedException {

                /*
                 * This patch gets rid of the XML Validation Exception caused when the user adds
                 * a custom option with either the label or the code blank. 
                 * On adding this patch, if a user tries to upload a blank option, s/he will
                 * be prevented from doing so.  
                 */
                if(labelBox.getValue().equals("") || codeBox.getValue().equals("")) {
                    Messagebox.show("You cannot keep the Label and Internal Code fields blank");
                }

                else {
                    if (!code2label.containsKey(codeBox.getValue())) {
                        Searchoptions soptions = Model.getCurrentModel().getEdition().getSearchoptions();
                        addSearchOption(labelBox, codeBox, soptions);
                    }
                    else { 
                        Messagebox.show("You cannot use this code. It is in use for some other option");
                        System.out.println("The key is not unique");
                    }
                }
            }
            /* As the option is added, each catalog will build a new
             * OptionsPopupWindow (via "searchoptionList" listener.
             * The current window is detached, and next time when the change button is clicked, 
             * it will pick up the new OptionsPopupWindow that is constructed.
             */
            private void addSearchOption(final Valuebox labelBox, final Valuebox codeBox, Searchoptions soptions) {
                code2label.put(codeBox.getValue(), new Code(labelBox.getValue(),false));                                
                soptions.addSearchoption(soptions.getSearchoptionCount(), NewSearchOptionBox.this.searchOption);
                oReadableLabelBox.displayOptionsWindow();
            }
        }

        /**
         * It represents each row in the options grid. 
         * It contains a checkbox, on checking which a particular option is selected. 
         * @author tgaat
         *
         */

        class OptionsRow extends Row {
            private final int rowIndex;   
            private final Label optLabel; // holds the code's label, e.g. "ISBN/ISSN"
            private final Checkbox ctxtMenuCheckBox;  // checkbox for ctxt menu 

            public OptionsRow(boolean isChecked, final String so, final int rowIndex) {
                this.rowIndex = rowIndex;

                this.setDraggable("true");
                this.setDroppable("true");

                SearchoptionsCheckbox c = new SearchoptionsCheckbox();
                c.setChecked(isChecked);

                final Code code = code2label.get(so); 
                if (code == null)
                    throw new RuntimeException("option code '" + so + "' is unknown, known codes are: " + code2label.keySet());

                optLabel = new Label(code.label);

                this.appendChild(c);
                Hbox changeBox = new Hbox();
                changeBox.setWidth("100%");

                changeBox.appendChild(optLabel);
                this.appendChild(changeBox);

                Hbox hb = new Hbox();
                hb.setAlign("center");
                this.appendChild(new Label(so));

                ctxtMenuCheckBox = new Checkbox("(CtxtMenu)");
                ctxtMenuCheckBox.setChecked(oReadableLabelBox.getContextMenuPreference(so));
                ctxtMenuCheckBox.setDisabled(!isChecked);
                ctxtMenuCheckBox.addEventListener(Events.ON_CHECK, new Utils.EventListenerAdapter(true) {
                    public void onEvent(Event e) {
                        if (ctxtMenuCheckBox.isChecked()) {
                            oReadableLabelBox.addContextMenuPreference(so);
                        } else {
                            oReadableLabelBox.removeContextMenuPreference(so);
                        }
                    }
                });
                this.appendChild(ctxtMenuCheckBox);

                ChangeButton cb = new ChangeButton(changeBox);
                hb.appendChild(cb);

                if (!code.isBuiltin) {
                    /**
                     * Delete button. On clicking this button, if a custom option is not in use(by any other catalog) then it is deleted.
                     */
                    Button delButton = new Button("Delete");

                    delButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter(true) {
                        public void onEvent(Event e) {     
                            try {
                                Searchoptions soptions = Model.getCurrentModel().getEdition().getSearchoptions();

                                Catalogs cats = Model.getCurrentModel().getEdition().getCatalogs();
                                HashSet<String> allOptionsSet = new HashSet<String>();

                                for (CatalogsItem catitem : cats.getCatalogsItem()) {
                                    Object cat =  catitem.getChoiceValue();  

                                    String options = (String)Utils.getBeanProperty(cat, "options");
                                    if (options != null) {
                                        for (String option : options.split(";")) {
                                            allOptionsSet.add(option);
                                        }
                                    }
                                }

                                if(!allOptionsSet.contains(so)) 
                                {

                                    /* as the option is removed, each catalog will build a new
                                     * OptionsPopupWindow (via "searchoptionList" listener.
                                     */
                                    for (Searchoption soption : soptions.getSearchoption()) {
                                        if(soption.getValue().equals(so)) {             
                                            removeSearchOption(soptions, soption);
                                            break;
                                        }
                                    }
                                }
                                else {
                                    Messagebox.show("This option is being used in this or some other catalog. " +
                                    "You cannot delete it.");
                                } 

                            } catch(Exception exc) {
                                MainWindowController.showException(exc); 
                            }
                        }           
                    });   
                    hb.appendChild(delButton);
                }
                this.appendChild(hb);

                /**
                 * Takes care of reformulating the options after drag and drop.
                 */
                this.addEventListener(Events.ON_DROP, new Utils.EventListenerAdapter() {
                    public void onEvent(Event e) {     
                        try {
                            DropEvent de = (DropEvent )e;
                            Utils.dragAndDrop(de.getDragged(), OptionsRow.this, OptionsRow.this.getParent());                                                        
                            reFormulateOptionsString();
                        } catch(Exception exc) {
                            Utils.logUnexpectedException(exc);
                        }
                    }                 
                });                
            }

            private void removeSearchOption(Searchoptions soptions, Searchoption soption) {
                code2label.remove(soption.getValue());
                soptions.removeSearchoption(soption);
                oReadableLabelBox.displayOptionsWindow();
            } 

            /**
             * This checkbox needs to be checked in order to select/deselect an option. 
             * On checking/unchecking it will set/clear
             * the bit corresponding to itself in the presentOptionsBitSet.
             * @author tgaat
             *
             */
            public class SearchoptionsCheckbox extends Checkbox {
                public void onCheck(Event e) throws Exception {
                    setOptionState(this.isChecked(), OptionsRow.this.rowIndex);
                }

                private void setOptionState(boolean isChecked, int index) throws Exception {
                    if(isChecked) {
                        oReadableLabelBox.presentOptionsBitset.set(index);
                        ctxtMenuCheckBox.setDisabled(false);
                    } 
                    else {
                        oReadableLabelBox.presentOptionsBitset.clear(index);

                        // if an option is unselected, it shouldn't be in default context menu
                        ctxtMenuCheckBox.setDisabled(true);
                        ctxtMenuCheckBox.setChecked(false);
                        oReadableLabelBox.removeContextMenuPreference(index);
                    }

                    reFormulateOptionsString();
                }       
            }

            /**
             * Enables the user to change the label corresponding to a code. 
             * @author tgaat
             *
             */
            public class ChangeButton extends Button {
                private Hbox parent;

                public ChangeButton(Hbox parent) {
                    this.parent = parent;
                    this.setLabel("Change");
                }        

                public void onClick(Event e) throws Exception {
                    this.setDisabled(true);

                    final Textbox tb = new Textbox(optLabel.getValue());
                    tb.setWidth("100%");
                    optLabel.setValue("");
                    final Button b = new Button("Submit");

                    String textboxid = tb.getId();
                    String buttonid = b.getId();

                    b.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter(true) {
                        public void onEvent(Event e) {      
                            try {

                                optLabel.setValue(tb.getValue());
                                String strCode = oReadableLabelBox.optionsVector.get(rowIndex);

                                // determine if this is a built-in code
                                boolean isBuiltin = code2label.get(strCode).isBuiltin;
                                Code code = new Code(tb.getValue(), isBuiltin);
                                code2label.put(strCode, code);

                                setLabelOfSearchoptionInModel(strCode, code.label);  
                                parent.getChildren().clear();
                                reFormulateOptionsString();
                                parent.appendChild(optLabel);
                                ChangeButton.this.setDisabled(false);
                            } catch (Exception e1) {
                                MainWindowController.showException(e1);
                            }
                        }

                        private void setLabelOfSearchoptionInModel(String code, String label) {
                            Searchoptions so = Model.getCurrentModel().getEdition().getSearchoptions();

                            for (Searchoption soption : so.getSearchoption()) {
                                if (soption.getValue().equals(code)) {
                                    soption.setLabel(label);
                                }
                            }
                        }      
                    });   

                    tb.setWidgetListener("onKeyDown", "fireSubmitOnEnter(event, this.$f('"+textboxid+"'), this.$f('"+buttonid+"'));");

                    parent.setWidth("100%");
                    parent.appendChild(tb);
                    parent.appendChild(b);
                }
            }
        }

        /**
         * Reformulates the new semi-colon separated option string. 
         * @param optionsReadableLabelBox
         * @throws Exception
         */  
        private void reFormulateOptionsString() throws Exception {
            String searchOptionString = "";            

            Rows rows = gridOptions.getRows();

            for(Object row : rows.getChildren()) {
                OptionsRow optRow = (OptionsRow)row;
                if (oReadableLabelBox.presentOptionsBitset.get(optRow.rowIndex)) {
                    searchOptionString += oReadableLabelBox.optionsVector.get(optRow.rowIndex)+";";
                }                 
            }

            if(searchOptionString.equals("")) {
                MainWindowController.showStatus(StatusCode.WARNING, "You have deselected all options","You have deselected all options");
            }

            searchOptionString = searchOptionString.equals("") ? "" : searchOptionString.substring(0,searchOptionString.length() -1);            

            this.oReadableLabelBox.setOption(searchOptionString);
        }
    }
}  
