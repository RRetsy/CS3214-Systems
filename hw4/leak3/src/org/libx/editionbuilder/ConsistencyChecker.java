package org.libx.editionbuilder;

import java.lang.reflect.Method;

import java.util.ArrayList;

import java.util.regex.Pattern;

import org.libx.xml.EditionVisitor;
import org.libx.xml.EditionVisitorAdapter;

/**
 * Baseclass for consistency checks related to editions.
 * Implemented as Visitor.
 * Subclasses are CatalogConsistencyChecker and EditionConsistencyChecker
 */
public abstract class ConsistencyChecker extends EditionVisitorAdapter {
    public static String editionBuilderFaqUrl = "http://libx.org/editionbuilderfaq.html";

    protected ArrayList<String> errors = new ArrayList<String>();
    protected boolean wasRun = false;

    /**
     * Return true if check showed an error.
     */
    boolean hasErrors() {
        return errors.size() > 0;
    }

    /**
     * Return true if this checker was run at least once 
     * after errors were cleared.
     */
    boolean wasRun() {
        return wasRun;
    }

    protected void clearErrors() {
        errors.clear();
        wasRun = false;
    }

    /**
     * Return an informative error message that lists
     * problems with this catalogs, if any.
     */
    String getErrorMessageHtml() {
        if (errors.size() == 1)
            return errors.get(0);

        StringBuffer sb = new StringBuffer();
        sb.append("Multiple problems were found with this configuration:<ol>");
        for (int i = 0; i < errors.size(); i++) {
            sb.append("<li>" + errors.get(i) + "</li>");
        }
        sb.append("</ol>");
        return sb.toString();
    }

    protected String makeURL(String anchortext, String url) {
        return "<a href=\"" + url + "\" target=\"_new\">" + anchortext + "</a>";
    }

    protected static Pattern noTrailingPathUrl = Pattern.compile("[a-z]+://[^/]*", Pattern.CASE_INSENSITIVE);

    /**
     * Check this bean for consistency.
     */
    void checkConsistency(Object bean, boolean clearErrors) {
        if (clearErrors)
            clearErrors();

        wasRun = true;
        try {
            // later: bean.accept(this);
            // for now, let's use reflection to find correct this.visit(bean) overload.
            Method m = EditionVisitor.class.getDeclaredMethod("visit", new Class [] { bean.getClass() });
            m.invoke(this, bean);
        } catch (Exception e) {
            Utils.logUnexpectedException(e);
        }
    }

    /**
     * Check this bean for consistency.
     * Clear accumulated errors.
     */
    void checkConsistency(Object bean) {
        checkConsistency(bean, true);   // true -> clearErrors
    }
}
