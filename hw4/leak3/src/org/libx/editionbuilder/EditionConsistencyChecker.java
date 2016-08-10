package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.libx.xml.Catalogs;
import org.libx.xml.Edition;
import org.libx.xml.Ezproxy;
import org.libx.xml.ProxyItem;

import org.libx.xml.types.EzproxyDisableifcheckfailsType;

import org.zkoss.zul.Hbox;

/**
 * Perform whole edition consistency checking.
 * Results appear below the status line.
 */
public class EditionConsistencyChecker extends ConsistencyChecker {
    private Model model;
    EditionConsistencyChecker(final Hbox messageBox) {
        Model.addCurrentModelChangeListener(new PropertyChangeListener() {
            // a new model was loaded
            public void propertyChange(PropertyChangeEvent evt) {
                model = (Model)evt.getNewValue();
                model.addPropertyChangeListener(new PropertyChangeListener() {
                    // anything in the current model changed
                    public void propertyChange(PropertyChangeEvent evt) {
                        // perform check
                        // post auto enhancement, we will just call checkConsistency(model.getEdition()) 
                        // for hierarchical traversal.
                        // For now, visit every bean in the edition configuration manually.
                        clearErrors();
                        Edition e = model.getEdition();
                        checkConsistency(e.getCatalogs(), false);
                        for (ProxyItem pi : e.getProxy().getProxyItem()) {
                            checkConsistency(pi.getChoiceValue(), false);
                        }

                        // more here...

                        // publish results
                        boolean showWarningMessage = hasErrors();
                        if (showWarningMessage)
                            Utils.showWarningMessage(getErrorMessageHtml(), messageBox);
                        messageBox.setVisible(showWarningMessage);
                    }
                });
            }
        });
    }

    /**
     * Check that EZProxy has a valid rewrite pattern and that a password is given
     * if /proxy_url web service is to be used
     */
    @Override
    public void visit(Ezproxy proxy) {
        String url = proxy.getUrl();
        if (url == null || "".equals(url)) {
            errors.add("Must specify URL rewrite pattern for EZProxy " + proxy.getName());
        } else
        if (!url.contains("%S")) {
            errors.add("EZProxy URL rewrite pattern must contain '%S' - "
                    +"for instance http://ezproxy.lib.edu/login?url=%S");
        }

        if (proxy.getDisableifcheckfails() == EzproxyDisableifcheckfailsType.TRUE 
                && proxy.getUrlcheckpassword() == null) {
            errors.add("You must provide a proxy_url password if you wish to disable the "+
                    "menu item if proxyability test fails for EZProxy " + proxy.getName());
        }
    }

    @Override
    public void visit(Catalogs catalogs) {
        int numCatalogs = catalogs.getCatalogsItemCount();
        if (numCatalogs == 0) {
            errors.add("You have not added any catalogs to this revision. "
                      +"At least one catalog is required. "
                      +"Use the Catalogs &amp; Database tab to add a catalog.");
        }

        if (numCatalogs > 0) {
            Object primaryCatalog = catalogs.getCatalogsItem(0).getChoiceValue();
            try {
                String primaryOptions = (String)Utils.getBeanProperty(primaryCatalog, "options");
                if (!(";" + primaryOptions + ";").contains(";i;")) {
                    errors.add("Your first (or primary) catalog cannot search by ISBN.  Because of that, "
                            +"cues that are based on ISBN will not work. ");
                }
            } catch (Exception e) {
                Utils.logUnexpectedException(e);
            }
        }
    }
}
