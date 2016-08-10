package org.libx.editionbuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.libx.xml.Searchoption;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.WrongValueException;

import org.zkoss.zul.Constraint;

/**
 * Constraint management for edition builder.
 */
public class Constraints {
    private static HashMap<String, Constraint> constraints = new HashMap<String, Constraint>();
    static HashSet<Class> isAddOptionAllowed = new HashSet<Class>();
    
    static void registerConstraint(Class clazz, String name, Constraint constraint) {
        constraints.put(clazz.getName() + name, constraint);
    }

    static Constraint lookupConstraint(Class clazz, String name) {
        return constraints.get(clazz.getName() + name);
    }
    
    static void allowAddOptions(Class clazz) {
        isAddOptionAllowed.add(clazz);
    }

    /**
     * Prototype of constraint management.
     */
    static {
        final HashSet<String> YtadiCatalogOptions = new HashSet<String>();
        final HashSet<String> YtadicCatalogOptions = new HashSet<String>();
        final HashSet<String> YtaicCatalogOptions = new HashSet<String>();
        final HashSet<String> standardCatalogOptions = new HashSet<String>();
        final HashSet<String> scholarOptions = new HashSet<String>();
        final HashSet<String> openurlOptions = new HashSet<String>();
        final HashSet<String> evergreenOptions = new HashSet<String>();
        final HashSet<String> worldcatOptions = new HashSet<String>();
        final HashSet<String> primoOptions = new HashSet<String>();

        for (String opt : new String [] { "Y", "t", "a", "i", "c" })
            YtaicCatalogOptions.add(opt);
        for (String opt : new String [] { "Y", "t", "a", "d", "i" })
            YtadiCatalogOptions.add(opt);
        for (String opt : new String [] { "Y", "t", "a", "d", "i", "c" })
            YtadicCatalogOptions.add(opt);
        for (String opt : new String [] { "Y", "t", "a", "d", "c", "i", "jt" })
            standardCatalogOptions.add(opt);
        for (String opt : new String [] { "Y", "at", "jt", "a" })
            scholarOptions.add(opt);
        for (String opt : new String [] { "jt", "i", "at", "a", "pmid", "doi" })
            openurlOptions.add(opt);

        final HashSet<String> allOptions = new HashSet<String>();
        allOptions.addAll(standardCatalogOptions);
        allOptions.addAll(scholarOptions);
        allOptions.addAll(openurlOptions);

        registerConstraint(org.libx.xml.Millenium.class, "options", new OptionConstraint(standardCatalogOptions));
        registerConstraint(org.libx.xml.Web2.class, "options", new OptionConstraint(standardCatalogOptions));
        registerConstraint(org.libx.xml.Aleph.class, "options", new OptionConstraint(standardCatalogOptions));
        registerConstraint(org.libx.xml.Voyager.class, "options", new OptionConstraint(standardCatalogOptions));
        registerConstraint(org.libx.xml.Horizon.class, "options", new OptionConstraint(standardCatalogOptions));
        registerConstraint(org.libx.xml.Sirsi.class, "options", new OptionConstraint(standardCatalogOptions));
        registerConstraint(org.libx.xml.Scholar.class, "options", new OptionConstraint(scholarOptions));
        registerConstraint(org.libx.xml.Openurlresolver.class, "options", new OptionConstraint(openurlOptions));
        registerConstraint(org.libx.xml.Sfx.class, "options", new OptionConstraint(openurlOptions));
        registerConstraint(org.libx.xml.Sersol.class, "options", new OptionConstraint(openurlOptions));
        registerConstraint(org.libx.xml.Vubis.class, "options", new OptionConstraint(YtadiCatalogOptions));
        registerConstraint(org.libx.xml.Voyager7.class, "options", new OptionConstraint(YtadiCatalogOptions));
        registerConstraint(org.libx.xml.Talisprism.class, "options", new OptionConstraint(YtaicCatalogOptions));
        registerConstraint(org.libx.xml.Polaris.class, "options", new OptionConstraint(YtadicCatalogOptions));

        for (String opt : new String [] { "Y", "t", "a", "d", "c", "i" })
            evergreenOptions.add(opt);
        registerConstraint(org.libx.xml.Evergreen.class, "options", new OptionConstraint(evergreenOptions));
        
        for (String opt : new String [] { "Y", "t", "a", "i", "d" })
            worldcatOptions.add(opt);
        registerConstraint(org.libx.xml.Worldcat.class, "options", new OptionConstraint(worldcatOptions));
        
        for (String opt : new String [] { "Y", "a", "t", "d", "c", "i" })
            primoOptions.add(opt);
        registerConstraint(org.libx.xml.Primo.class, "options", new OptionConstraint(primoOptions));

        allowAddOptions(org.libx.xml.Millenium.class);
        allowAddOptions(org.libx.xml.Bookmarklet.class);
        allowAddOptions(org.libx.xml.Custom.class);
        
        final OptionConstraint allDeclared = new OptionConstraint(null) {
                /* Recomputes validInModel on every call based on current search options. */
                Set<String> getValidOptions() {
                    validInModel = new HashSet<String>(allOptions);
                    for (Searchoption opt : Model.getCurrentModel().getEdition().getSearchoptions().getSearchoption()) {
                        validInModel.add(opt.getValue());
                    }
                    return validInModel;
                }

                public void validate(Component comp, Object value) throws WrongValueException {
                    getValidOptions();
                    super.validate(comp, value);
                }
        };

        /* bookmarklet's valid option depend on the current model. */
        registerConstraint(org.libx.xml.Bookmarklet.class, "options", allDeclared);
        registerConstraint(org.libx.xml.Custom.class, "options", allDeclared);
    }

    static class OptionConstraint implements Constraint {
        protected Set<String> validInModel;
        OptionConstraint (Set<String> validInModel) {
            this.validInModel = validInModel;
        }

        Set<String> getValidOptions() {
            return validInModel;
        }

        public void validate(Component comp, Object value) throws WrongValueException {
            String [] opts = ((String)value).split(";");

            for (String opt : opts) {
                if (!validInModel.contains(opt))
                    throw new WrongValueException("Illegal value '" + opt + "' for the options field: " 
                        + "options must be a semicolon-separated list of valid options. "
                        + "Valid options are: " + validInModel + "\n"
                        + "For bookmarklets only, you may define additional options in the Options tab under Search Options");
            }
        }
    }

    /* Find OptionConstraints object for a given catalog class */
    static OptionConstraint lookupOptionConstraint(Class clazz) {
        return (OptionConstraint)constraints.get(clazz.getName() + "options");
    }
}
