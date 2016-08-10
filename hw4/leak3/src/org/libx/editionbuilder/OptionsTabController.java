package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.io.File;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Scanner;

import org.libx.editionbuilder.Utils.BeanPropertyComponentCreator;

import org.libx.xml.Edition;
import org.libx.xml.Option;
import org.libx.xml.Options;
import org.libx.xml.Searchoption;
import org.libx.xml.Searchoptions;

import org.libx.xml.types.OptionKeyType;

import org.zkoss.zk.ui.Component;

import org.zkoss.zk.ui.event.Event;

import org.zkoss.zul.Button;
import org.zkoss.zul.Row;
import org.zkoss.zul.Rows;


/**
 * OptionsTabController serves as the controller for the tab containing the Manage Options option
 * in the view.
 * Its function is to control the options of an Edition.
 */
public class OptionsTabController extends Rows 
{
    public static String optionsFileName = "/options.default";

    static {
        // add appropriate entries to help system map.
        // we use <Option.class> as the map to which to add the keys.
        Class optionClass = Option.class;
        Enumeration<?> e = OptionKeyType.enumerate();
        while (e.hasMoreElements()) {
            OptionKeyType t = (OptionKeyType)e.nextElement();
            HelpSystem.addHelpKey(optionClass, t.toString(), 
                    HelpSystem.getHelpKey(optionClass, "key") + "." + t);
        }
    }
    /**
     * Adds listeners to listen for a new Model   
     */   
    public void initialize() {
        Model.addCurrentModelChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    loadModel((Model)evt.getNewValue());
                } catch (Exception e) {
                    e.printStackTrace();    //XXX
                }
            }
        });
    }


    /**
     * A new model is loaded and accordingly the list of options is drawn on the view.
     */
    private void loadModel(Model model) throws Exception {
        populateList(model);

        model.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("optionlist".equals(evt.getPropertyName())) {
                    try {
                        populateList((Model)evt.getNewValue());
                    } catch (Exception e) {
                        e.printStackTrace();    // XXX
                    }
                }
            }
        });
    }


    /**
     * populateList populates the list of options, by reading a config file, 
     * which as of now is just hardcoded.
     */

    private void populateList(Model model) throws Exception {
        getChildren().clear();     
        Options options = model.getEdition().getOptions();
        Option[] opts = options.getOption();


        // store existing options in map for fast access by their string key
        HashMap<String, Option> key2Option = new HashMap<String, Option>();
        for (Option o : opts)
            key2Option.put(o.getKey().toString(), o);

        ReadObjectConfiguration conf = new ReadObjectConfiguration();
        Scanner scanner = new Scanner(new File(Config.xmlpath + optionsFileName));

        ArrayList<Object> displayOptions = conf.read(scanner);
        for (Object o : displayOptions) {
            if (!(o instanceof BeanPropertyComponentCreator)) {
                System.out.println("Error: " + o.getClass() + " must implement OptionEntry");
                continue;
            }

            BeanPropertyComponentCreator oentry = (BeanPropertyComponentCreator)o;
            String whichKey = oentry.getPropertyName();
            Option modelopt = key2Option.get(whichKey);

            // if option is not contained in Model, add it now
            if (modelopt == null) {
                modelopt = new Option();
                modelopt.setKey(OptionKeyType.valueOf(whichKey));
                modelopt.setValue(oentry.getDefaultValue());
                options.addOption(modelopt);
            }

            Row r = new Row();
            Component help = Utils.createHelpText(Option.class, whichKey);
            r.appendChild(help);

            Component c = oentry.createComponentForProperty(modelopt);
            r.appendChild(c);
            appendChild(r);
        } 
    }

    /**
     * Uses the default component for the "value" property of the option bean
     * @author gback
     *
     */
    public static class DefaultComponent extends Utils.KeyValueOption implements BeanPropertyComponentCreator {
        public Component createComponentForProperty(Object opt) {
            try {
                return Utils.createDefaultComponentForBeanProperty(opt, "value");
            } catch (Exception e) {
                e.printStackTrace(); // XXX
            }
            return null;
        }

        DefaultComponent (String key, String defvalue) { 
            super(key, defvalue);
        }
    }

    /**
     * Displays a true false choice checkbox if the option in the config file
     * supports true false values.
     *   
     * @author tgaat
     *
     */
    public static class TrueFalseChoice extends Utils.KeyValueOption implements BeanPropertyComponentCreator {
        public Component createComponentForProperty(Object opt) {
            try {
                return new Utils.ValueCheckbox(
                        opt,
                        Utils.beanPropertyGetter(opt, "value"), 
                        Utils.beanPropertySetter(opt, "value", String.class), 
                        new Object[] { "true", "false" });
            } catch (Exception e) {
                e.printStackTrace(); // XXX
            }
            return null;
        }

        TrueFalseChoice (String key, String defvalue) { 
            super(key, defvalue);
        }
    }

    public static class UserImage extends Utils.KeyValueOption implements BeanPropertyComponentCreator {
        public Component createComponentForProperty(Object opt) {
            try {
                return new Utils.Imagebox(opt,
                                          Utils.beanPropertyGetter(opt, "value"), 
                                          Utils.beanPropertySetter(opt, "value", String.class));
            } catch (Exception e) {
                Utils.logUnexpectedException(e);
            }
            return null;
        }

        UserImage (String key, String defvalue) { 
            super(key, defvalue);
        }
    }
    
    /**
     * Controller for the Searchoptions.
     * @author tgaat
     *
     */
    public static class SearchOptionsTabController extends Rows {
        Component enclosingGrid;

        public void initialize(Component enclosingGrid) {
            this.enclosingGrid = enclosingGrid;
            Model.addCurrentModelChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    try {
                        loadModel((Model)evt.getNewValue());
                    } catch (Exception e) {
                        e.printStackTrace();    //XXX
                    }
                }
            });
        }

        /**
         * Adds a new searchoption. Adds a new searchoptions field itself if it is not present.
         * @throws Exception
         */
        public void addNewSearchOption() throws Exception {

            Searchoption s = new Searchoption();
            s.setLabel("Search Label");
            s.setValue("code");
            addSearchOption(s);

            Edition currentEdition = Model.getCurrentModel().getEdition();
            Searchoptions so = currentEdition.getSearchoptions();
            if (so == null) {
                currentEdition.setSearchoptions(so = new Searchoptions());
            }
            so.addSearchoption(s);
        }        


        /**
         *Loads the model and populates the view according to the new model.
         */
        private void loadModel(Model model) throws Exception {
            try {
                populateList(model);
            } catch (Exception e) {
                Utils.logUnexpectedException(e);
            }

            model.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("searchoptionlist".equals(evt.getPropertyName())) {
                        try {
                            populateList((Model)evt.getNewValue());
                        } catch (Exception e) {
                            Utils.logUnexpectedException(e);
                        }
                    }
                }
            });
        }


        /**
         * Populates the list of searchoptions in the view initially itself, or each time the 
         * model is loaded.
         * @param model
         * @throws Exception
         */
        private void populateList(Model model) throws Exception {
            getChildren().clear(); 
            Searchoptions searchoptions = (Searchoptions)model.getEdition().getSearchoptions();

            enclosingGrid.setVisible(false);
            for (Searchoption s : searchoptions.getSearchoption()) {
                addSearchOption(s);
            }
        }

        /**
         * Adds a controller for a searchoption
         * @param searchoption
         * @throws Exception
         */
        void addSearchOption(Searchoption searchoption) throws Exception {
            enclosingGrid.setVisible(true);
            appendChild(new SearchOptionController(searchoption));
        }

        /**
         * This represents the controller for a single searchoption.
         * 
         * @author tgaat
         *
         */
        public class SearchOptionController extends Row {
            Searchoption s;
            
           /**
            * Represents the delete button used to delete each searchoption.
            * @author tgaat
            *
            */ 
            public class Delbutton extends Button {
                Delbutton() {
                    this.setLabel("Delete Searchoption");
                }
                
                public void onClick(Event e) throws Exception {  
                    SearchOptionController.this.setParent(null);
                    Searchoptions so = Model.getCurrentModel().getEdition().getSearchoptions();
                    so.removeSearchoption(s);
                    enclosingGrid.setVisible(so.getSearchoptionCount() > 0);
                }
                
            }

            /**
             * Appends a textbox each for the label and value fields of searchoptions.
             * Also appends a delete button for deleting each searchoption
             * @param s
             * @throws Exception
             */
            public SearchOptionController(Searchoption s) throws Exception {
                this.s = s;
                appendChild(new Utils.Valuebox(
                        s,
                        Utils.beanPropertyGetter(s, "label"),
                        Utils.beanPropertySetter(s, "label", String.class)));

                appendChild(new Utils.Valuebox(
                        s,
                        Utils.beanPropertyGetter(s, "value"),
                        Utils.beanPropertySetter(s, "value", String.class)));
                
                appendChild(new Delbutton());
            }        
            
            public Searchoption getSearchoption() {
                return this.s;
            }
            
        }
    }
}
