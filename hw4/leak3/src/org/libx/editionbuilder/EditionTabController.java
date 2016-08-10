package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.libx.editionbuilder.Utils.BeanPropertyComponentCreator;
import org.libx.xml.Edition;
import org.libx.xml.Name;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.HtmlBasedComponent;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Vbox;


/**
 * Controller for displaying the meta information about editions.
 * @author tgaat
 *
 */
public class EditionTabController extends Vbox {
    
    /**
     * Adds listeners to listen for a new model. 
     */
    public EditionTabController() {
        Model.addCurrentModelChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    loadModel((Model)evt.getNewValue());
                } catch (Exception e) {
                    e.printStackTrace();  
                }
            }
        });
    }

    /**
     * Helper method to call the populateList method. 
     * @param model
     * @throws Exception
     */
    private void loadModel(Model model) throws Exception {
        populateList(model);
    }

    /**
     * It populates the list of edition attributes.
     * @param model
     * @throws Exception
     */
    private void populateList(Model model) throws Exception {
        getChildren().clear(); 
        Edition edition = model.getEdition();

        appendChild(new EditionController(edition));
    }

    public static String editionNameOptionFile = "/edition.name.default";

    /**
     * Returns the configuration of how the fields are to be displayed in
     * the view.
     * @return
     * @throws Exception
     */
    private ArrayList<Object> getConfig() throws Exception {
        // XXX implement caching for configuration.
        ReadObjectConfiguration conf = new ReadObjectConfiguration();
        Scanner scanner = new Scanner(new File(Config.xmlpath + editionNameOptionFile));
        ArrayList<Object> compList = conf.read(scanner);
        return compList;
    }

    /**
     * Controller for the view pertaining to the edition attributes. 
     * @author tgaat
     *
     */
    public class EditionController extends Vbox {

        EditionController(Edition e) throws Exception {

            final Name name = e.getName();
            final Map<String,Textbox> attr2Textbox = new HashMap<String,Textbox>();

            List<Object> editionNameAttributeList = getConfig();
            for (Object o : editionNameAttributeList) {
                    Hbox r = new Hbox();
                    r.setHflex("1");                
                    r.setStyle("table-layout: fixed");
                
                    if (!(o instanceof BeanPropertyComponentCreator)) {
                        System.out.println("Error: " + o.getClass() + " must implement ResolverEntry");
                        continue;
                    }

                    BeanPropertyComponentCreator editionNameAttribute = (BeanPropertyComponentCreator)o;
                    String propertyname = editionNameAttribute.getPropertyName();

                    Component help = Utils.createHelpText(name.getClass(), propertyname);
                    ((HtmlBasedComponent)help).setHflex("1");
                    r.appendChild(help);

                    Component c = editionNameAttribute.createComponentForProperty(name);
                    // XXX this break encapsulation, but the attr2Textbox heuristics below 
                    // only works with Textboxes.  Find a better way to do this.
                    attr2Textbox.put(propertyname, (Textbox)c);
                    ((Textbox)c).setHflex("1");
                    r.appendChild(c);

                    this.appendChild(r);
            }
            this.setHflex("1");

            // if user enters a 'long' description, infer suitable 'short' descriptions
            name.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("long".equals(evt.getPropertyName())) {
                        String [] token = ((String)evt.getNewValue()).split("\\s+");
                        if ("".equals(name.getShort())) {
                            StringBuffer sh = new StringBuffer("LibX ");
                            for (int i = 1; i < token.length; i++) {
                                sh.append(Character.toUpperCase(token[i].charAt(0)));
                            }
                            name.setShort(sh.toString());
                            attr2Textbox.get("short").setValue(name.getShort());
                        }
                        
                        StringBuffer sh = new StringBuffer();
                        for (int i = 1; i < token.length; i++) {
                            sh.append(token[i]);
                            sh.append(" ");
                        }
                        String institutionGuess = sh.toString();

                        if ("".equals(name.getEdition())) {
                            name.setEdition(institutionGuess + "Edition");
                            attr2Textbox.get("edition").setValue(name.getEdition());
                        }

                        if ("".equals(name.getDescription())) {
                            name.setDescription("Toolbar for " + institutionGuess + "Library Users");
                            attr2Textbox.get("description").setValue(name.getDescription());
                        }
                    }
                }
            });
        }
    }
}
