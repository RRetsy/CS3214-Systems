package org.libx.editionbuilder;

import org.zkoss.zul.Menupopup;
import org.zkoss.zul.Menuitem;
import org.zkoss.zul.Menu;
import org.zkoss.zul.Menuseparator;
import org.zkoss.zk.ui.Component;
import org.zkoss.image.AImage;
import org.libx.xml.*;
import org.libx.xml.types.OptionKeyType;
import java.io.File;
import java.beans.*;

/**
 * Mockup ToolbarController class.
 *
 * @author Godmar Back
 */
public class ToolbarController extends org.zkoss.zul.Toolbar
{
    private Menu libxmenu;
    private Menupopup libxmenupopup;
    private Menuseparator libxmenuseparator;

    /**
     * Initialize mockup toolbar, register for new model changes.
     * For each model, make sure to receive updates
     */
    public void initialize(Menu libxmenu) {
        this.libxmenu = libxmenu;
        this.libxmenupopup = (Menupopup)libxmenu.getChildren().get(0);

        // separator is assumed to be first child.
        this.libxmenuseparator = (Menuseparator)libxmenupopup.getChildren().get(0);

        Model.addCurrentModelChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                Model newmodel = (Model)evt.getNewValue();
                rebuildUrlList(newmodel.getEdition().getLinks());
                ToolbarController.this.libxmenu.setLabel(newmodel.getEdition().getName().getShort());
                showIcon(newmodel.getEdition().getOptions());
                newmodel.addPropertyChangeListener(new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent e) {
                        if ("urlList".equals(e.getPropertyName()))
                            rebuildUrlList(Model.getCurrentModel().getEdition().getLinks());
                        if ("value".equals(e.getPropertyName()))
                            showIcon(Model.getCurrentModel().getEdition().getOptions());
                    }
                });
            }
        });
    }

    /**
     * display current edition icon
     */
    private void showIcon(Options options) {
        String c = null;
        for (Option o : options.getOption()) {
            if (o.getKey() == OptionKeyType.ICON) {
                c = o.getValue().replaceFirst(Utils.imgPrefix, "");
                break;
            }
        }
        if (c == null)
            return;

        //String iconsrc = Model.getCurrentModelHttpPath() + c;
        String iconsrc = Model.getCurrentModelFSPath() + c;
        System.out.println("Setting new icon to: " + iconsrc);
        try {
            this.libxmenu.setImageContent(new AImage(new File(iconsrc)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        //this.libxmenu.setSrc(iconsrc);
    }

    /**
     * rebuild url link list from model
     */
    private void rebuildUrlList(Links links) {
        // clear all existing children up until libxmenuseparator
        while (libxmenupopup.getChildren().size() > 0 &&
               libxmenupopup.getChildren().get(0) != libxmenuseparator)
        ((Component)libxmenupopup.getChildren().get(0)).setParent(null);
    
        // create menuitems for all urls
        for (Url u : links.getUrl()) {
            final Menuitem m = new Menuitem();
            m.setLabel(u.getLabel());
            m.setHref(u.getHref());
            u.addPropertyChangeListener(
                new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent e) {
                        if (e.getPropertyName().equals("label"))
                            m.setLabel((String)e.getNewValue());

                        if (e.getPropertyName().equals("href"))
                            m.setHref((String)e.getNewValue());
                    }
                });
            libxmenupopup.insertBefore(m, libxmenuseparator);
        }
    }
}
