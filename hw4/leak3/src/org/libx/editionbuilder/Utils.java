package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.UnsupportedEncodingException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.exolab.castor.xml.XMLClassDescriptor;
import org.exolab.castor.xml.XMLFieldDescriptor;

import org.libx.xml.Additionalfiles;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Session;

import org.zkoss.zk.ui.event.Deferrable;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.OpenEvent;

import org.zkoss.zul.Button;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Column;
import org.zkoss.zul.Columns;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Constraint;
import org.zkoss.zul.Grid;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Html;
import org.zkoss.zul.Image;
import org.zkoss.zul.Include;
import org.zkoss.zul.Label;
import org.zkoss.zul.Popup;
import org.zkoss.zul.Row;
import org.zkoss.zul.Rows;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Timer;
import org.zkoss.zul.Vbox;

import org.zkoss.zul.ext.Constrainted;

public class Utils 
{
    /*
     * Support code for "special component" handling.
     * Whenever "drawGrid" is called and uses the default bean property creator.
     */
    static HashMap<String, BeanPropertyComponentCreator> recordSpecialComponent = new HashMap<String, BeanPropertyComponentCreator>();
    static String catalogPropertiesFileName = "/catalog.properties.default";
    static {
        getOptionsFromFile();
    }

    /**
     * Records the special component that has to be drawn for the property "options", which in turn is recorded in the catalog properties file. 
     * This component is then used by the drawGrid method in Utils
     */
    private static void getOptionsFromFile() {

        try {
            ReadObjectConfiguration conf = new ReadObjectConfiguration();
            Scanner scanner = new Scanner(new File(Config.xmlpath + catalogPropertiesFileName));

            ArrayList<Object> displayOptions = conf.read(scanner);
            for(Object o : displayOptions) {    
                if (!(o instanceof BeanPropertyComponentCreator)) {
                    System.out.println("Error: " + o.getClass() + " must implement BeanPropertyComponentCreator");
                    continue;
                }

                BeanPropertyComponentCreator oentry = (BeanPropertyComponentCreator)o;

                String whichKey = oentry.getPropertyName();
                Utils.recordSpecialComponent.put(whichKey, oentry);
            }
        } catch (Exception exc) {
            Utils.logUnexpectedException(exc);
        }
    }  


    
    /**
     * Set an attribute for the current desktop. */
    public static void setDesktopAttribute(String name, Object attr) {
        getCurrentDesktop().setAttribute(name, attr);
    }

    /**
     * Get an attribute for the current desktop. */
    public static Object getDesktopAttribute(String name) {
        return getCurrentDesktop().getAttribute(name);
    }

    /**
     * get current desktop. */
    public static Desktop getCurrentDesktop() {
        return Executions.getCurrent().getDesktop();
    }

    /**
     * get current session. */
    public static Session getCurrentSession() {
        return getCurrentDesktop().getSession();
    }

    /**
     * Set an attribute for the current session. */
    public static void setSessionAttribute(String name, Object attr) {
        getCurrentSession().setAttribute(name, attr);
    }

    /**
     * Get an attribute for the current session. */
    public static Object getSessionAttribute(String name) {
        return getCurrentSession().getAttribute(name);
    }

    public static class BeanPropertyChangeSupport {
        private java.beans.PropertyChangeSupport propertyChangeSupport;

        public void notifyPropertyChangeListeners(java.lang.String fieldName, java.lang.Object oldValue, java.lang.Object newValue)
        {
            if (propertyChangeSupport == null) return;
            propertyChangeSupport.firePropertyChange(fieldName,oldValue,newValue);
        }

        public boolean removePropertyChangeListener(java.beans.PropertyChangeListener pcl)
        {
            if (propertyChangeSupport == null) return false;
            propertyChangeSupport.removePropertyChangeListener(pcl);
            return true;
        }

        public void addPropertyChangeListener(java.beans.PropertyChangeListener pcl)
        {
            if (propertyChangeSupport == null) {
                propertyChangeSupport = new java.beans.PropertyChangeSupport(this);
            }
            propertyChangeSupport.addPropertyChangeListener(pcl);
        }
    }

    /**
     * Give a class in Castor's XML binding, return the matching descriptor class
     */
    static Class getDescriptorClass(Class catClass) {
        // as of Castor 1.1, descriptor classes are in subpackage "descriptor"
        try {
            String descClass = catClass.getPackage().getName() 
            + ".descriptors." 
            + catClass.getName().replaceFirst(".*\\.", "") + "Descriptor";
            return Class.forName(descClass);

        } catch (ClassNotFoundException cne) {
            cne.printStackTrace();
        }
        return null;
    }
    /**  
     * This interface is used to filter out the required or the optional attributes as required.
     */
    interface AttributeFilter {
        // return true if this field should be included
        public boolean include(XMLFieldDescriptor fd);
    }

    /**
     * This interface is used to select all attributes of the Xisbn.
     */
    static AttributeFilter acceptAll = new AttributeFilter() {
        public boolean include(XMLFieldDescriptor fd) {
            return true;
        }
    };

    public static Set<Object> trueAndFalse = new HashSet<Object>();
    static {
        trueAndFalse.add("true");
        trueAndFalse.add("false");
    }

    /*
     * Components that can convert an empty setting "" to null
     * should implement this interface and allows the setting
     * of a preference whether this conversion is done or not.
     *
     * For required options, it's better to have "" rather than null.
     * For optional options, it's usually better to convert to null.
     */
    public interface CanConvertEmptyToNull {
        public boolean isConvertEmptyToNull();
        public void setConvertEmptyToNull(boolean b);
    }

    /**
     * Textbox that is linked to a bean property.
     */
    public static class Valuebox extends Textbox implements CanConvertEmptyToNull {
        final Method setter, getter;
        final Object object;

        private boolean convertToNull = false;
        public boolean isConvertEmptyToNull() { return this.convertToNull; }
        public void setConvertEmptyToNull(boolean b) { this.convertToNull = b; }

        Valuebox (Object object, Method getter, Method setter) throws Exception {
            this.setter = setter;
            this.getter = getter;
            this.object = object;

            this.setValue((String)getter.invoke(object));
            this.setWidth("98%");   // fill 98% of available space
        }

        public void onChange(Event e) throws Exception {
            String value = this.getValue();
            // convert empty input "" to null value such that castor will 
            // remove the corresponding attribute.  Currently, the JavaScript code requires
            // that an empty attribute be not present (rather than be set to "")
            if (convertToNull && "".equals(value))
                value = null;
            setter.invoke(object, value);
        }
    }

    public static final String imgPrefix = "chrome://libx/skin/";

    /**
     * Implement an input element that allows a user to choose an image.
     * This will be just a drop-down box, filtered, and an Image.
     */
    public static class Imagebox extends Vbox {
        final Method setter, getter;
        final Object object;            // underlying object

        Image img;                      // preview of image
        Html msg;                       // message next to image
        ValueCombobox cbox;             // drop-down list, entries are FileEntryHelpers
        FileEntryHelper currentchoice;  // currently chosen FileEntryHelper

        static class FileEntryHelper {
            final org.libx.xml.File file;  // File to which this choice is bound, null if none.
            final int i;
            FileEntryHelper(org.libx.xml.File file, int i) {
                this.file = file;
                this.i = i;
            }
            public String toString() {
                if (file != null)
                    return "File #" + i + " " + file.getName();
                else
                    return "<Unassigned>";
            }
            org.libx.xml.File getFile() {
                return file;
            }
            boolean hasFile() {
                return file != null;
            }
        }
        static final FileEntryHelper noFileChoice = new FileEntryHelper(null, 0);

        /**
         * Implement java bean setter/getter such that this Imagebox can become
         * the target object on which a ValueCombobox can operate
         */
        public FileEntryHelper getCurrentFileEntry() {
            return currentchoice;
        }

        /** Helper method that reflects change in ImageBox's currentFileEntry
         * in underlying org.libx.xml.File object.
         */
        private void setUnderlyingObject() {
            try {
                if (currentchoice.hasFile()) {
                    // XXX imgPrefix implies 'skin' handle other files here too
                    setter.invoke(object, imgPrefix + currentchoice.getFile().getName());

                    // force reload even if URL did not change - this can happen
                    // if the old & new image have the same name, such as favicon.ico
                    img.setSrc(Model.getCurrentModelHttpPath() + "/"
                            + currentchoice.getFile().getName() + "?" + new Date());
                    msg.setContent("");
                } else {
                    // if a user choose unassigned, or if the assigned file is
                    // removed from the list of additional files, the property
                    // will be set to "".  This satisfies required XML attributes.
                    // TBD: output warning in this case.
                    setter.invoke(object, new Object [] { "" });
                    img.setSrc(System.getProperty("eb.warningimage"));
                    msg.setContent("no image set.");
                }
            } catch (Exception e) {
                MainWindowController.showException(e);
            }
        }

        public void setCurrentFileEntry(final FileEntryHelper newchoice) {
            currentchoice = newchoice;
            setUnderlyingObject();

            // if this file changes (because user uploads new file)
            // reload the image displayed, and update the drop-down list choices
            if (newchoice.hasFile()) {
                newchoice.getFile().addPropertyChangeListener(new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent evt) {
                        setUnderlyingObject();
                    }
                });
            }
        }

        /**
         * Obtain list of possible file entry choices, including the 'unassigned' choice.
         */
        List<Object> getFileEntryChoices(org.libx.xml.File []afiles) {
            List<Object> choices = new ArrayList<Object>();
            choices.add(noFileChoice);
            int nr = 1;
            for (org.libx.xml.File f : afiles) {
                if (FileTabController.isInternal(f))
                    continue;

                FileEntryHelper feh = new FileEntryHelper(f, nr++);
                choices.add(feh);
            }
            return choices;
        }

        List<Object> getFileEntryChoices(Additionalfiles afiles) {
            return getFileEntryChoices(afiles.getFile());
        }

        /**
         * Return the current choice.
         * If the file was not found, return the unassigned choice.
         */
        FileEntryHelper findCurrentChoice(List<Object> list, org.libx.xml.File currentf) {
            for (Object litem : list) {
                FileEntryHelper feh = (FileEntryHelper)litem;
                if (feh.getFile() == currentf)
                    return feh;
            }
            return noFileChoice;
        }

        Imagebox (final Object object, Method getter, final Method setter) throws Exception {
            this.object = object;
            this.getter = getter;
            this.setter = setter;

            String chromePath = (String)getter.invoke(object);
            /* If the path is not a chromePath (but points to an external URL), then
             * import it. This is true for some legacy edition's icon URLs.
             * Note: if chromePath is "", don't do this - as is the case for an
             * OpenURL.image's that are not specified.
             * findCurrentChoice below can handle those.
             */
            if (chromePath != null && !chromePath.equals("") && !chromePath.startsWith("chrome:")) {
                try {
                    chromePath = FileTabController.addNewFile("chrome/libx/skin/libx", chromePath);
                } catch (Exception ex) {
                    Utils.printLog("downloading file failed, ignoring - chromePath=%s", chromePath);
                    Utils.logUnexpectedException(ex);
                    chromePath = "";    // unspecified
                }
                setter.invoke(object, chromePath);
            }

            List<Object> choices = getFileEntryChoices(
                    Model.getCurrentModel().getEdition().getAdditionalfiles().getFile());
            currentchoice = findCurrentChoice(choices, 
                    FileTabController.getAdditionalFileEntry(chromePath));

            this.cbox = new ValueCombobox(this, 
                    beanPropertyGetter(this, "currentFileEntry"),
                    beanPropertySetter(this, "currentFileEntry", FileEntryHelper.class),
                    choices);
            this.appendChild(this.cbox);
            this.cbox.setValue(currentchoice.toString());

            this.img = new Image();
            this.appendChild(this.img);
            this.appendChild(this.msg = new Html());

            setCurrentFileEntry(currentchoice);

            // if the list of available files changes, or if any file changes its name,
            // we must reinitialize our list.  Ideally, we would like to listen for events on
            // <additionalfiles> and any descendants.  Castor doesn't support that, so we
            // listen at the top.
            Model.getCurrentModel().addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    // /edition/additionalfiles or /edition//file.name
                    if (!"fileList".equals(evt.getPropertyName()) && !"name".equals(evt.getPropertyName()))
                        return;
                    List<Object> choices = getFileEntryChoices(
                            Model.getCurrentModel().getEdition().getAdditionalfiles().getFile());
                    cbox.initializeLabels(choices);
                    // we must also check if the currently selected choice still exists and
                    // handle the case where it was removed.
                    FileEntryHelper oldchoice = currentchoice;        
                    currentchoice = findCurrentChoice(choices, currentchoice.getFile());
                    if (oldchoice != currentchoice)
                        setCurrentFileEntry(currentchoice);
                    cbox.setValue(currentchoice.toString());
                }
            });
        }
    }

    /**    
     * This class is a Checkbox, which makes corresponding changes in the model, when it is checked.
     */
    public static class ValueCheckbox extends Checkbox {

        Method setter, getter;
        Object obj;
        Object [] values;

        /**
         * Create a new Checkbox tied to an object via reflection
         * @param obj       the object to be changed
         * @param getter    method to read the object's value
         * @param setter    method to set the object's value
         * @param values    possible values for the object, [0] means checked.
         * @throws Exception
         */
        ValueCheckbox (Object obj, Method getter, Method setter, Object [] values) throws Exception { 
            this.setter = setter;
            this.getter = getter;
            this.obj = obj;
            this.values = values;
            // this.setLabel(Arrays.toString(values)); // debugging only
            this.setChecked(values[0].equals(getter.invoke(obj)));
        }

        public void onCheck(Event e) throws Exception {
            setter.invoke(obj, values[this.isChecked() ? 0 : 1]);
        }
    }

    /**
     * This is the combobox that represents the types of the resolver
     * @author tgaat
     *
     */
    public static class ValueCombobox extends Combobox {
        final Method setter, getter;
        final Object obj;
        HashMap<String, Object> label2value;

        /**
         * Create a new Checkbox tied to an object via reflection
         * @param obj       the object to be changed
         * @param getter    method to read the object's value
         * @param setter    method to set the object's value
         * @param li    possible values for the object, [0] means checked.
         * @throws Exception
         */
        ValueCombobox (Object obj, Method getter, Method setter, List<Object> li) throws Exception { 
            this.setter = setter;
            this.getter = getter;
            this.obj = obj;
            Object currentvalue = getter.invoke(obj);
            if (currentvalue != null)
                this.setValue(currentvalue.toString());
            else
                this.setValue("Please select a choice");
            initializeLabels(li);
        }

        void initializeLabels(List<Object> li) {
            this.label2value = new HashMap<String, Object>();
            this.setReadonly(true);
            this.getItems().clear(); 
            for (Object o: li){
                String label = o.toString();
                appendItem(label);
                label2value.put(label, o);
            }
        }

        public void onChange(Event e) throws Exception {
            Combobox c = (Combobox)e.getTarget();
            setter.invoke(obj, label2value.get(c.getValue()));
        }
    }

    /**
     * Create a help text component from the path that's registered in the
     * help system.
     */
    public static Component createHelpText(Object modelObj, String fd) {
        String path = HelpSystem.getHelpKey(modelObj, fd);
        return createHelpTextComponent(path);
    }

    /**
     * Implement a help popup.
     */
    public static class HelpPopup extends Popup { 
        HelpPopup(String deploy_help_path) {
            setWidth("500px");
            Include inc = new Include(deploy_help_path);
            appendChild(inc);
            logOpenEvents(this, deploy_help_path);
        }

        /**
         * Add onOpen event listener that logs when this popup is shown.
         */
        public static void logOpenEvents(Popup p, final String msg) {
            p.addEventListener(Events.ON_OPEN, new EventListenerAdapter(false) {
                public void onEvent(Event e) {
                    // onOpen is sent twice, on open and on close - 
                    // we only count the 'open' event.
                    // @see org.zkoss.zk.ui.event.OpenEvent
                    if (!((OpenEvent)e).isOpen())
                        return;
                    Utils.printLog("user read: %s", msg);
                }
            });
        }
    }

    /**
     * Create a component that's shown as help, including the help
     * icon and text.  Based on XML path in dot-notation.
     * Example for path: edition.catalogs.millenium.name
     */
    public static Component createHelpTextComponent(String path) {
        String help_path = HelpSystem.getHelpFilePath(path);
        String deploy_help_path = HelpSystem.getHelpFileDeployPath(path);
        File doc_f = new File(help_path);

        if(!doc_f.exists()) {
            return new Label(doc_f.getAbsolutePath() + " not found");
        } 

        Hbox h = new Hbox();
        Image icon = new Image(System.getProperty("eb.helpimage"));
        Popup p = new HelpPopup(deploy_help_path);
        icon.setTooltip("uuid(" + p.getUuid() + ")");
        h.appendChild(p);
        h.appendChild(icon);

        // read the first line from the help file into the label
        try {
            LineNumberReader lr = null;
            try {
                lr = new LineNumberReader(new FileReader(doc_f));
                Html ht = new Html(lr.readLine());
                ht.setStyle("text-align: left");
                h.appendChild(ht);
            } finally {
                if (lr != null)
                    lr.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } 
        h.setStyle("text-align: left");
        return h;
    }

    /**
     * Create a default input component for a bean property, depending on its
     * possible values.  For strings, a simple Valuebox is created and associated
     * with the object.
     * For other types, we enumerate all possible values. If the values are
     * "true"/"false", a ValueCheckbox is created.
     * 
     * @param cat       object that has named property
     * @param xmlName   name of property
     * @return          Component that can be displayed and that is associated with bean property
     */
    static Component createDefaultComponentForBeanProperty(Object cat, String xmlName) throws Exception {
        Object obj = getBeanProperty(cat, xmlName);
        Method getter = beanPropertyGetter(cat, xmlName);
        Class type = getter.getReturnType();

        if (type == String.class) {
            return new Valuebox(cat, getter, beanPropertySetter(cat, xmlName, String.class));
        } else {
            // Castor types have a enumerate method.
            Method en = obj.getClass().getDeclaredMethod("enumerate");
            Enumeration e = (Enumeration)en.invoke(obj);             
            List<Object> li = new ArrayList<Object>();

            HashSet<String> eAsString = new HashSet<String>();
            while (e.hasMoreElements()) {
                Object o = e.nextElement();
                li.add(o);
                eAsString.add(o.toString());
            }

            Method setter = beanPropertySetter(cat, xmlName, obj.getClass());
            if (eAsString.equals(trueAndFalse)) {
                Method v = obj.getClass().getDeclaredMethod("valueOf", String.class);

                return new ValueCheckbox(cat, getter, setter, 
                        new Object [] { 
                        v.invoke(null, "true"), 
                        v.invoke(null, "false") 
                });
            } else {
                return new ValueCombobox(cat, getter, setter, li);
            }
        }
    }

    /**
     * Draw a 2-column table with descriptions and input elements for properties
     * of provided object.
     */
    public static Vbox drawTable(Object cat, AttributeFilter filter) throws Exception {
        Vbox g = new Vbox();

        XMLClassDescriptor cdesc = (XMLClassDescriptor)Utils.getDescriptorClass(cat.getClass()).newInstance();
        for (XMLFieldDescriptor fd : cdesc.getAttributeDescriptors()) {

            if(!filter.include(fd))
                continue;

            Hbox r = new Hbox();
            r.setHflex("1");
            r.setStyle("table-layout: fixed");
            String xmlName = fd.getXMLName();

            Component c = createHelpText(cat.getClass(), xmlName);
            r.appendChild(c);

            Component c2 = createDefaultComponentForBeanProperty(cat, xmlName);
            r.appendChild(c2);

            Constraint constraint = Constraints.lookupConstraint(cat.getClass(), xmlName);
            if (constraint != null && c2 instanceof Constrainted)
                ((Constrainted)c2).setConstraint(constraint); 

            // convert optional attributes to null when user enters ""
            if (c2 instanceof CanConvertEmptyToNull && !fd.isRequired())
                ((CanConvertEmptyToNull)c2).setConvertEmptyToNull(true);

            g.appendChild(r);
        }
        g.setHflex("1");

        if (g.getChildren().size() == 0)
            return null;

        return g;
    }

    interface HelpTextCreator {
        public Component createHelpTextComponent(String xmlName);
    }

    /**
     * This function takes in as arguments the catalog as an object(could also be the Xisbn),
     * and a filter, and returns a grid.   Uses the default help text.
     */
    public static Grid drawGrid(final Object cat, AttributeFilter filter, String columnLabel, String [] sortOrder) throws Exception {
        final Utils.HelpTextCreator htc = new Utils.HelpTextCreator() {
            public Component createHelpTextComponent(String xmlName) {
                return createHelpText(cat.getClass(), xmlName);
            }
        };
        return drawGrid(cat, filter, columnLabel, htc, sortOrder);
    }

    /**
     * See drawGrid.  Takes explicit interface to create help text.
     */
    public static Grid drawGrid(Object cat, AttributeFilter filter, String columnLabel, HelpTextCreator htc, final String [] sortOrder) throws Exception {
        Grid g = new Grid();

        Columns cc = new Columns();        
        Rows rr = new Rows();

        Column col = new Column(columnLabel);
        col.setWidth("35%");
        cc.appendChild(col);

        col = new Column("Value");    // XXX
        col.setWidth("65%");
        cc.appendChild(col);

        XMLClassDescriptor cdesc = (XMLClassDescriptor)Utils.getDescriptorClass(cat.getClass()).newInstance();

        // place the terms listed in sortOrder first, sort the rest alphabetically
        XMLFieldDescriptor [] attributes = cdesc.getAttributeDescriptors();
        Arrays.sort(attributes, new Comparator<XMLFieldDescriptor>() {
            public int compare(XMLFieldDescriptor _this, XMLFieldDescriptor _that) {
                String thisName = _this.getXMLName();
                String thatName = _that.getXMLName();

                if (sortOrder != null) {
                    for (String key : sortOrder) {
                        if (key.equals(thisName))
                            return -1;
                        if (key.equals(thatName))
                            return 1;
                    }
                }
                // use numerical sorting if both end with a digit
                // This will sort param0, param1, param2, etc. numerically
                if (Character.isDigit(thisName.charAt(thisName.length()-1))
                    && Character.isDigit(thatName.charAt(thatName.length()-1))) {
                    // remove all non-digits
                    Integer thisInt = Integer.parseInt(thisName.replaceAll("\\D", ""));
                    Integer thatInt = Integer.parseInt(thatName.replaceAll("\\D", ""));
                    return thisInt.compareTo(thatInt);
                }
                return thisName.compareTo(thatName);
            }
        });

        for (XMLFieldDescriptor fd : attributes) {

            if (!filter.include(fd))
                continue;

            Row r = new Row();
            String xmlName = fd.getXMLName();

            Component c = htc.createHelpTextComponent(xmlName);
            r.appendChild(c);
            
            Component c2;
            
            if(recordSpecialComponent.get(xmlName) != null) {
                BeanPropertyComponentCreator  oentry = recordSpecialComponent.get(xmlName);
                c2 = oentry.createComponentForProperty(cat);
            }
            else
                c2 = createDefaultComponentForBeanProperty(cat, xmlName);
            
            if (c2 == null)
                continue;

            r.appendChild(c2); 

            Constraint constraint = Constraints.lookupConstraint(cat.getClass(), xmlName);
            if (constraint != null && c2 instanceof Constrainted)
                ((Constrainted)c2).setConstraint(constraint); 

            // convert optional attributes to null when user enters ""
            if (c2 instanceof CanConvertEmptyToNull && !fd.isRequired())
                ((CanConvertEmptyToNull)c2).setConvertEmptyToNull(true);

            rr.appendChild(r);
        } 

        
        if (rr.getChildren().size() == 0)
            return null;

        g.appendChild(rr);
        g.appendChild(cc);
        g.setWidth(Config.zkGridWidth);
        return g;
    }

    static String upperCaseName(String pname) {
        return Character.toUpperCase(pname.charAt(0)) + pname.substring(1, pname.length());
    }

    static Method beanPropertySetter(Object obj, String pname, Class type) throws NoSuchMethodException {
        return obj.getClass().getMethod("set" + upperCaseName(pname), new Class[] { type });
    }

    static Method beanPropertyGetter(Object obj, String pname) throws NoSuchMethodException {
        return beanPropertyGetter(obj.getClass(), pname);
    }

    static Method beanPropertyGetter(Class<?> clazz, String pname) throws NoSuchMethodException {
        return clazz.getMethod("get" + upperCaseName(pname));
    }

    static Object getBeanProperty(Object obj, String pname) throws Exception {
        Method m = beanPropertyGetter(obj, pname);
        return m == null ? null : m.invoke(obj);
    }

    static void setBeanProperty(Object obj, String pname, Object value) throws Exception {
        Method m = beanPropertySetter(obj, pname, value.getClass());
        m.invoke(obj, new Object [] { value });
    }

    static void clearBeanProperty(Object obj, String pname, Class clazz) throws Exception {
        Method m = beanPropertySetter(obj, pname, clazz);
        m.invoke(obj, new Object [] { null });
    }

    static void addBeanPropertyChangeListener(Object bean, PropertyChangeListener listener) throws Exception {
        Method m = bean.getClass().getMethod("addPropertyChangeListener", 
                new Class[] { PropertyChangeListener.class });
        m.invoke(bean, listener);
    }

    /**
     * Sets the default for the required attributes for a castor bean  
     * @param cdesc
     * @param bean
     * @throws Exception
     */
    static void setRequiredAttributes(Object bean) throws Exception {
        XMLClassDescriptor cdesc = (XMLClassDescriptor)getDescriptorClass(bean.getClass()).newInstance();

        for (XMLFieldDescriptor fd : cdesc.getAttributeDescriptors()) {
            if (fd.isRequired()) {
                Method g = Utils.beanPropertyGetter(bean, fd.getXMLName());
                if (g.invoke(bean) != null)
                    continue;
                Class<?> ftype = fd.getFieldType();
                Method s = Utils.beanPropertySetter(bean, fd.getXMLName(), ftype);
                try {
                    Method en = ftype.getDeclaredMethod("enumerate");
                    Enumeration e = (Enumeration)en.invoke(null);
                    s.invoke(bean, e.nextElement());
                } catch (NoSuchMethodException nsme) {
                    // We set this to "" here such that castor can marshal the object successfully
                    // Later, KeyValueOption.setDefaultValue will still place the correct default
                    // value there from the *.default files if the value is an empty string
                    s.invoke(bean, "");
                }
            }
        }
    }

    /**
     * A bean property component creator can create a ZUL component which 
     * can track a specified bean property.
     *
     * An implementation of this interface is expected to be passed the
     * property on which it works via the constructor.
     */ 
    interface BeanPropertyComponentCreator {
        /**
         * Create the component.
         */
        Component createComponentForProperty(Object bean);
        /*
         * Return the bean property's name
         */
        String getPropertyName();
        /*
         * Return the property's default value.
         */
        String getDefaultValue();
    }

    static abstract class KeyValueOption {
        protected String key;   
        protected String defvalue;   
        KeyValueOption(String key, String defvalue)  { 
            this.key = key; 
            this.defvalue=defvalue; 
        }
        public String getPropertyName() {
            return key.toString(); 
        }

        public String getDefaultValue() { return defvalue; }

        protected void setDefaultValue(Object bean) throws Exception {
            Object v = Utils.beanPropertyGetter(bean, getPropertyName()).invoke(bean);
            if (v == null || "".equals(v)) {
                Method setter = Utils.beanPropertySetter(bean, getPropertyName(), String.class);
                if (setter != null)
                    setter.invoke(bean, new Object [] { getDefaultValue() });
            }
        }
    }

    /**
     * Creates a default component depending on the objects read from the default file.
     * @author tgaat
     *
     */
    public static class DefaultComponent extends Utils.KeyValueOption implements BeanPropertyComponentCreator {
        public Component createComponentForProperty(Object bean) {
            try {
                setDefaultValue(bean);
                return Utils.createDefaultComponentForBeanProperty(bean, key);
            } catch (Exception e) {
                Utils.logUnexpectedException(e);
            }
            return null;
        }

        DefaultComponent (String key1, String defvalue) {
            super(key1,defvalue);
        }
    }

    public static class OmitBeanProperty extends KeyValueOption implements BeanPropertyComponentCreator {
        public Component createComponentForProperty(Object bean) {
            return null;
        }
        OmitBeanProperty (String key, String defvalue) {
            super(key, defvalue);
        }
    }

    public static class UserImage extends KeyValueOption implements BeanPropertyComponentCreator {
        public Component createComponentForProperty(Object bean) {
            try {
                setDefaultValue(bean);
                return new Utils.Imagebox(
                        bean,
                        Utils.beanPropertyGetter(bean, getPropertyName()),
                        Utils.beanPropertySetter(bean, getPropertyName(), String.class));
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
     * Called from onDrop handler if component 'source' is dropped on component 
     * 'target', both of which are children of container 'parent'.
     * Implements rearrangement of children in a box via drag-n-drop.
     */
    public static void dragAndDrop(Component source, Component target, Component parent) throws IllegalAccessException, InvocationTargetException {
        if (!target.getClass().isInstance(source)) {
            return;
        }
        
        int targetIndex = parent.getChildren().indexOf(target);
        int sourceIndex = parent.getChildren().indexOf(source);

        if (sourceIndex < targetIndex) {
            if (targetIndex == parent.getChildren().size() - 1)
                parent.appendChild(source);
            else
                parent.insertBefore(source,
                        (Component)parent.getChildren().get(targetIndex+1));
        } else {
            parent.insertBefore(source, target);
        }        
    }

    /*
     * Implement up/down buttons.
     * These button can be added to a list of components that share a common
     * parent. The parent must implement "CanRestoreModel", which is invoked
     * after the children's position changes.
     */
    interface CanRestoreModel {
        public void restoreModel() throws Exception;
    }

    private static abstract class UpdownButtonBaseClass extends Button {
        protected Component source;
        UpdownButtonBaseClass(final Component source) {
            this.source = source;
            addEventListener(Events.ON_CLICK, new EventListener() {
                public void onEvent(Event e) {
                    try {
                        Utils.dragAndDrop(source, getSibling(), source.getParent());
                        ((CanRestoreModel)source.getParent()).restoreModel();
                    } catch (Exception ex) {
                        MainWindowController.showException(ex);
                    }
                }
            });
        }
        protected abstract Component getSibling();
    }

    static class DownButton extends UpdownButtonBaseClass {
        protected Component getSibling() {
            return source.getNextSibling();
        }
        DownButton(final Component source) {
            super(source);
            setImage("/src/images/icons/go-down16blue.png");
        }
    }

    static class UpButton extends UpdownButtonBaseClass {
        protected Component getSibling() {
            return source.getPreviousSibling();
        }
        UpButton(final Component source) {
            super(source);
            setImage("/src/images/icons/go-up16blue.png");
        }
    }

    public static abstract class EventListenerAdapter implements EventListener, Deferrable {
        private boolean asap;
        public abstract void onEvent(Event e);

        /* In ZK 2.4 isAsap changed to isDeferrable */
        public boolean isDeferrable() {
            return !this.asap;
        }
        public EventListenerAdapter(boolean asap) {
            this.asap = asap;
        }
        public EventListenerAdapter(){
            this(true);
        }
    }

    /**
     * Bind property p2 in bean2 to property p1 in bean b1 such that when b1.p1 changes,
     * b2.p2 is updated using the given format.
     */
    public static void bindDependentBeanProperty(final Object bean1, final String beanProperty1, final Object bean2, final String beanProperty2, final String format) throws Exception {
        PropertyChangeListener onChange = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    setBeanProperty(bean2, beanProperty2, new Formatter(new StringBuilder())
                    .format(format, getBeanProperty(bean1, beanProperty1))
                    .toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        onChange.propertyChange(null);
        addBeanPropertyChangeListener(bean1, onChange);
    }

    /* Set a given property to a given value for all children of ZK component that
     * support that property.
     * For instance, to set all children to 100% width, use "width", "100%" as
     * propName and propValue, respectively.
     */
    static void setPropertyOnAllChildren(Component parent, String propName, Object propValue) throws Exception {
        for (Object o : parent.getChildren()) {
            try {
                Method m = beanPropertySetter(o, propName, propValue.getClass());
                m.invoke(o, propValue);
            } catch (NoSuchMethodException e) {
                /* ignore */
            }
        }
    }

    /**
     * Set hflex values on children. Number of provided values
     * must match number of children.
     */
    static void setHflexOnChildren(Component parent, String... hflex) {
        try {
            int i = 0;
            for (Object o : parent.getChildren()) {
                Method m = beanPropertySetter(o, "hflex", String.class);
                m.invoke(o, hflex[i++]);
            }

            if (parent.getChildren().size() != i)
                throw new IllegalArgumentException("too few hflex values are provided");
        } catch (Exception e) {
            Utils.logUnexpectedException(e);
        }
    }

    static void setHflexOnAllChildren(Component parent, String propValue) {
        try {
            setPropertyOnAllChildren(parent, "hflex", propValue);
        } catch (Exception e) {
            Utils.logUnexpectedException(e);
        }
    }

    static void setWidthOnAllChildren(Component parent, String propValue) {
        try {
            setPropertyOnAllChildren(parent, "width", propValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String formatDate(Date date) {

        if (date != null) {
            return new SimpleDateFormat("yyyy-MM-dd").format(date);
        }
        else 
            return "";
    }

    public static void logUnexpectedException(Throwable t) {
        printLogHead();
        System.out.println("Unexpected exception: " + t);
        t.printStackTrace(System.out);
    }

    /**
     * XXX implement this as Hbox subclass: "WarningBox" etc.
     */
    public static void showWarningMessage(String msg, Hbox messageBox) {
        Image warningImage = new Image();
        warningImage.setSrc(System.getProperty("eb.warningimage"));
        Html messageHtml = new Html();
        messageHtml.setContent(msg);
        messageBox.getChildren().clear();
        messageBox.appendChild(warningImage);
        messageBox.appendChild(messageHtml);
    }
    
    public static void printLog(String format, Object... args) {
        printLogHead();
        System.out.println(String.format(format, args));
    }

    private static void printLogHead() {
        Execution currentExecution = Executions.getCurrent();
        System.out.print(new Date() + ": ");

        // currentExecution is null if not invoked from event handling thread
        if (currentExecution != null) {
            String sessionId = null;

            try {
                // ZK doc says native session is javax.servlet.http.HttpSession
                Object nativeSession = getCurrentSession().getNativeSession();
                sessionId = (String)Utils.getBeanProperty(nativeSession, "id");
            } catch (Exception e) {
                // if native session object does not have getId()
                sessionId = "nosession";
            }

            System.out.print(currentExecution.getRemoteAddr() + ":" 
                    + sessionId + ":" + getCurrentDesktop().getId() + ": ");
        }
    }

    static String encodeURIComponent(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException use) { 
            return s;
        }
    }

    static String escapeHtmlEntities(String s) {
        return s.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    }

    /* Helper:
       reads content of entire web page at given URLConnection */
    static String slurpURL(URLConnection conn) throws IOException
    {
        DataInputStream dis = null;
        try { 
            dis = new DataInputStream(conn.getInputStream());

            int l = conn.getContentLength();    // can be -1 if unknown
            byte []b = new byte[l < 0 ? 100000 : l];
            int offset = 0;
            try {
                int bread = 0;
                do {
                    bread = dis.read(b, offset, b.length - offset);
                    if (bread > 0)
                        offset += bread;
                } while (bread > 0);
            } catch (EOFException _) {
                ; // ignore
            }
            return new String(b, 0, offset);
        } finally {
            if (dis != null)
                dis.close();
        }
    }

    /**
     * Set a timer that removes a component after some time
     * @param self the component to be removed
     * @param duration in milliseconds for long the element should appear
     */
    static void removeComponentAfter(final Component self, int duration) {
        final Component parent = self.getParent();
        final Timer t = new Timer(duration);

        t.addEventListener(Events.ON_TIMER, new EventListener() {
            public void onEvent(Event e) {
                self.setParent(null);
                t.setParent(null);
            }
        });
        parent.appendChild(t);
    }

    static class Strings {
        static String join(Iterable<?> c, String sep) {
            boolean first = true;
            StringBuffer sb = new StringBuffer();
            for (Object o : c) {
                if (!first)
                    sb.append(sep);
                sb.append(String.valueOf(o));
                first = false;
            }
            return sb.toString();
        }
    }

    static String makeSafeURL(URL url) throws UnsupportedEncodingException {
        String s = url.toString();
        StringBuffer out = new StringBuffer();
        char [] c = s.toCharArray();
        for (int i = 0; i < c.length; i++)
            if (c[i] < 128)
                out.append(c[i]);
            else
                out.append(URLEncoder.encode(String.valueOf(c[i]), "UTF-8"));
        return out.toString();
    }
}
