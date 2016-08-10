package org.libx.editionbuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.exolab.castor.xml.XMLClassDescriptor;
import org.exolab.castor.xml.XMLFieldDescriptor;
import org.libx.xml.Edition;

/**
 * The help system support is a two-level map that maps 
 * a <Object, String> pair to a String value.
 * 
 * When this class is loaded, it will add mappings that map all
 * XML types to a map that maps all attribute names to their full path
 * name from the root. This value can be used to name a help file.
 * For instance, "org.libx.xml.Millenium.class", "name" is mapped to
 * "editions.catalogs.millenium.name".
 *
 * The 'main' method creates 
 * empty templates for documentation in the docs/ directory.
 */
public class HelpSystem
{
    /**
     * Map a class such as org.libx.xml.Millenium to a HashMap that contains
     * a map that maps each attribute to the full documentation name.
     */
    public static final HashMap<Object, HashMap<String, String>> xmlClass2HelpPathMap =
        new HashMap<Object, HashMap<String, String>>();

    /**
     * Return help key associated with a <Class, Key> pair
     */
    public static String getHelpKey(Object key1, String key2) {
        HashMap<String, String> hm = xmlClass2HelpPathMap.get(key1);
        if (hm != null)
            return hm.get(key2);
        return null;
    }
    
    /**
     * Return help file path for a given class/attribute.
     * Relative to servlet path, usable for <include> zul elements.
     */
    public static String getHelpFileDeployPath(String key) {
        return File.separatorChar + Config.reldocpath + File.separatorChar + key + suffix;
    }

    /**
     * Return help file path for a given class/attribute.
     * Absolute in host filesystem, usable for java.io.File().
     */
    public static String getHelpFilePath(String key) {
        return Config.docpath + File.separatorChar + key + suffix;
    }

    private static String suffix = ".html";
    private static String separator = ".";

    public static void main(String []av) throws Exception {
        for (Object key1 : xmlClass2HelpPathMap.keySet()) {
            HashMap<String, String> m = xmlClass2HelpPathMap.get(key1);

            for (Map.Entry<String, String> e : m.entrySet()) {
                String path = e.getValue();
                String filename = Config.docpath + File.separatorChar 
                                + path + suffix;

                File f = new File(filename);
                if (!f.exists() 
                    || System.getProperty("recreate", "false").equals("true")) {
                    System.out.println("Creating: " + filename);
                    FileOutputStream fs = new FileOutputStream(f);
                    PrintWriter pw = new PrintWriter(fs);
                    pw.println("Place holder for <b>" + path + "</b>");
                    pw.close();
                    fs.close();
                } else {
                    System.out.println(filename + " exists, skipping.");
                }
            }
        }
    }

    /**
     * Recursively traverse XMLClassDescriptors and 
     * populate xmlClass2HelpPathMap.
     */
    static {
        try {
            traverse("", Edition.class.getName());
        } catch (Exception e) { 
            e.printStackTrace();
        }
    }

    private static void traverse(String path, String c0) throws Exception {
        Class<?> c = Utils.getDescriptorClass(Class.forName(c0));
        XMLClassDescriptor cdesc = (XMLClassDescriptor)c.newInstance();

        /* Descriptors that are true element descriptor have a method
         * isElementDefinition() that returns true. */
        Method m = c.getDeclaredMethod("isElementDefinition");
        if ((Boolean)m.invoke(cdesc)) {
            path += cdesc.getXMLName() + separator;
            for (XMLFieldDescriptor f : cdesc.getAttributeDescriptors()) {
                Class cx = Class.forName(c0);
                String name = f.getXMLName();
                addHelpKey(cx, name, path + name);
            }
        }

        for (XMLFieldDescriptor f : cdesc.getElementDescriptors()) {
            traverse(path, f.getFieldType().getName());
        }
    }
    
    /**
     * Add a help key for a given <Class, Key> pair.
     */
    static void addHelpKey(Object cx, String key, String value) {
        HashMap<String, String> hm = xmlClass2HelpPathMap.get(cx);
        if (hm == null) {
            hm = new HashMap<String, String>();
            xmlClass2HelpPathMap.put(cx, hm);
        }

        hm.put(key, value);
    }
}
