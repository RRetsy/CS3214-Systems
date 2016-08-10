package org.libx.editionbuilder;

import java.util.HashSet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.libx.xml.Aleph;
import org.libx.xml.Bookmarklet;
import org.libx.xml.Centralsearch;
import org.libx.xml.Custom;
import org.libx.xml.Evergreen;
import org.libx.xml.Horizon;
import org.libx.xml.Millenium;
import org.libx.xml.Polaris;
import org.libx.xml.Sirsi;
import org.libx.xml.Talisprism;
import org.libx.xml.Voyager7;
import org.libx.xml.Voyager;
import org.libx.xml.Vubis;
import org.libx.xml.Web2;
import org.libx.xml.Worldcat;

/**
 * Check consistency of a catalog.
 * Implemented as Visitor.
 */
public class CatalogConsistencyChecker extends ConsistencyChecker {
    /**
     * For bookmarklets, check that each option appears prefixed with %
     * in the URL, unless the URL contains a %SWITCH statement
     */
    public void visit(Bookmarklet b) {
        String msg = "";
        String argtemplate = b.getPostdata();
        if (argtemplate == null)
            argtemplate = b.getUrl();

        HashSet<String> missing = new HashSet<String>();
        for (String o : b.getOptions().split(";")) {
            if (!argtemplate.contains("%" + o))
                missing.add(o);
        }
        if (missing.size() > 0 && argtemplate.indexOf("%SWITCH") == -1) {
            String templateName = b.getPostdata() != null ? "Post Data" : "URL";
            msg += "Warning: Bookmarklet " + templateName + " Template must contain: ";
            for (String o : missing)
                msg += "%" + o + " ";
            
            msg += "<p>" +
                    "One or more selected option codes do not appear in the "
                    +templateName+" template. <b>This bookmarklet will not work.</b> "
                    +"The "+templateName+" template must contain the substring %code for "
                    +"each selected option code. "
                    +"For instance, if you choose the option labeled "
                    +"'Keyword', the template must contain the matching "
                    +"option code %Y. <br />"
                    +"Click Required Settings -&gt; Search Options -&gt; "
                    +"Change to see a list of defined options and their "
                    +"codes, or to add your own options.</p>";
            
            errors.add(msg);
        }
    }

    public void visit(Polaris p) {
        checkForBaseUrl(p.getUrl());
    }

    public void visit(Talisprism t) {
        checkForBaseUrl(t.getUrl());
    }

    public void visit(Voyager7 v) {
        checkForBaseUrl(v.getUrl());
    }

    public void visit(Vubis v) {
        checkForBaseUrl(v.getUrl());
    }

    public void visit(Worldcat w) {
        checkForBaseUrl(w.getUrl());
    }

    public void visit(Evergreen s) {
        checkForBaseUrl(s.getUrl());
    }

    public void visit(Sirsi s) {
        checkForBaseUrl(s.getUrl());
    }

    public void visit(Millenium m) {
        checkForBaseUrl(m.getUrl());
    }

    public void visit(Aleph a) {
        checkForBaseUrl(a.getUrl());
    }

    public void visit(Horizon h) {
        checkForBaseUrl(h.getUrl());
    }

    public void visit(Voyager v) {
        checkForBaseUrl(v.getUrl());
    }

    public void visit(Web2 w2) {
        checkForBaseUrl(w2.getUrl());
    }

    static Pattern allCapsOrDigits = Pattern.compile("[A-Z0-9]*");
    public void visit(Centralsearch cs) {
        String hash = cs.getSslibhash();
        if (!allCapsOrDigits.matcher(hash).matches()) {
            errors.add("Serials Solutions hashes consist of only uppercase "
                    +"letters and digits.  For instance, instead of "+hash
                    +", try " + hash.toUpperCase());
        }
    }

    public void visit(Custom w2) {
        String jsURL = w2.getJsimplurl();
        if (jsURL == null || "".equals(jsURL)) {
            errors.add("Warning: custom catalogs require a link to a piece "
                +"of JavaScript code you provide in the Custom Catalog Implementation URL field. "
                +"You must write and upload JavaScript code there. " 
                +"For most resources, consider using a bookmarklet instead. "
                +"For more information, " 
                  + makeURL("read the edition builder faq.", editionBuilderFaqUrl));
            return;
        }
        if (!jsURL.endsWith(".js")) {
            errors.add("Warning: your " 
                + makeURL("Custom Catalog Implementation URL", jsURL) 
                + " does not end in .js.  Check that it links to your JavaScript.");
        }
    }

    /**
     * Check that the URL does not include trailing path or slash, but
     * does include a protocol.  This is required for a number of catalogs.
     */
    private void checkForBaseUrl(String url) {
        Matcher m = noTrailingPathUrl.matcher(url);
        if (!m.matches()) {
            errors.add("URL does not match protocol://hostname.<br />"
              + "For this catalog, you may only specify the name of the host where your "
              + "catalog is located.  Do not include anything after the hostname and be sure "
              + "to include a protocol (usually http://) before the hostname. " 
              + "For example, http://addison.vt.edu would work, but http://addison.vt.edu/search/ "
              + " does not.  Read the help text for more information. "
              + "Check Optional Settings for catalogs that support non-standard paths.");
        }
    }

    /**
     * Check this catalog for consistency.
     */
    @Override
    void checkConsistency(Object cat) {
        // first let visitors run that check catalog-specific properties.
        super.checkConsistency(cat);

        // additional check for options which applies only here.
        try {
            /** 
             * For all catalogs, it's wrong to not specify any options.
             */
            String options = (String)Utils.getBeanProperty(cat, "options");
            if (options == "") {
                errors.add("Warning: no option selected");
            }
        } catch (Exception e) {
            Utils.logUnexpectedException(e);
        }
    }
}
