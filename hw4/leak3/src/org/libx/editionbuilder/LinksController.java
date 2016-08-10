package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.libx.xml.Links;
import org.libx.xml.Url;

import org.zkoss.zk.ui.event.DropEvent;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;

import org.zkoss.zul.A;
import org.zkoss.zul.Button;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Vbox;

/**
 * Controller class for managing the Links in an edition.
 * This class supports the contents of a Vbox in the "Manage Links" tab
 *
 * @author Godmar Back
 */
public class LinksController extends Vbox
{
    // the vbox element that contains an array of UrlControllers.
    private Vbox urlbox;

    /** Store a reference to the Rows element managing the 
     * rows of UrlControllers. 
     * Register for new model notifications.
     */
    public void initialize(Vbox urlbox, Hbox addHomePageLinkBox) {
        this.urlbox = urlbox;

        Button b = new Button("Add A Link To This Edition's Homepage");
        b.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
            public void onEvent(Event e) {
                Model m = Model.getCurrentModel();
                m.getEdition().getLinks().addUrl(Model.createLinkToEditionHomePage(m.getEdition().getId()));
            }
        });
        addHomePageLinkBox.appendChild(b);
        addHomePageLinkBox.appendChild(Utils.createHelpTextComponent("links.addhomepagelink"));

        Model.addCurrentModelChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                loadModel((Model)evt.getNewValue());
            }
        });
    }

    /* Repopulate url list from model after a model change
     * or when a new model is loaded.
     * We renumber the UrlControllers here and make sure that all
     * model content is displayed.
     */
    private void populateUrlBox(Model m) {
        // remove all existing children
        urlbox.getChildren().clear();

        Links links = (Links)m.getEdition().getLinks();
        int i = 0;
        for (Url u : links.getUrl()) {
            UrlController lc = addUrlController(u);
            lc.updateIndex(++i);
            lc.updateLabel(u.getLabel());
            lc.updateHref(u.getHref());
        }
    }

    /**
     * A new model instance is loaded.
     * Update the GUI accordingly, removing existing and 
     * creating new UrlControllers as necessary.
     */
    private void loadModel(final Model m) {
        populateUrlBox(m);

        m.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                /* The only model change that warrants an entire view
                 * update is when the urlList changed, as a result of
                 * remove/add or rearrange after drag-n-drop. */
                if ("urlList".equals(e.getPropertyName()))
                    populateUrlBox(m);
            }
        });
    }

    //* helper method to add a new UrlController
    private UrlController addUrlController(Url u) {
        UrlController r = new UrlController(this);
        urlbox.appendChild(r);
        r.setUrl(u);
        return r;
    }

    //* helper method to remove a Url from model
    private void removeUrl(Url url) {
        Model.getCurrentModel().getEdition().getLinks().removeUrl(url);
    }

    /**
     * add a new, empty link and create a controller for it and
     * update the model.
     */
    public void newLink() {
        Url u = new Url();
        addUrlController(u);
        u.setHref("http://");
        u.setLabel("Enter Label...");

        Model.getCurrentModel().getEdition().getLinks().addUrl(u);
    }

    /**
     * Restore the model content from the displayed structure.
     * This is called when there are substantial changes to the content,
     * such as when the order has changed after a drag-n-drop.
     * This function builds a new links[] arrays and installs
     * it into the current model.
     */
    private void restoreModel() {
        Links links = Model.getCurrentModel().getEdition().getLinks();
        int nChildren = urlbox.getChildren().size();
        Url [] urls = new Url[nChildren];
        for (int i = 0; i < nChildren; i++) {
            urls[i] = ((UrlController)urlbox.getChildren().get(i)).getUrl();
        }
        links.setUrl(urls);
    }

    /**
     * Controller class for UrlController.
     * This class controls a single row in the grid contained in a 
     * LinkController.
     */
    public static class UrlController extends Hbox {
        //* the model's Url instance
        Url link;
        final Label numberLabel;
        final Textbox labelTextbox;
        final Textbox hrefTextbox;
        final A toolbarButton;

        //* LinksController instance in which we are contained.
        private LinksController linksController;    

        /**
         * Create a new row and its children.
         */
        UrlController(LinksController linksController) {
            this.linksController = linksController;

            setDroppable("true");
            this.numberLabel = new Label();
            numberLabel.setDraggable("true");

            this.labelTextbox = new Textbox();
            this.labelTextbox.addEventListener(Events.ON_CHANGE, new Utils.EventListenerAdapter(true) {
                public void onEvent(Event e) {
                    link.setLabel(labelTextbox.getValue());
                }
            });
            this.hrefTextbox = new Textbox();
            this.hrefTextbox.addEventListener(Events.ON_CHANGE, new Utils.EventListenerAdapter(true) {
                public void onEvent(Event e) {
                    link.setHref(hrefTextbox.getValue());
                }
            });
            this.toolbarButton = new A();
            this.toolbarButton.setTarget("_new");

            Button removeButton = new Button("Remove");
            removeButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                public void onEvent(Event e) {
                    UrlController.this.linksController.removeUrl(getUrl());
                }
            });
            appendChild(this.numberLabel);
            appendChild(this.labelTextbox);
            appendChild(this.hrefTextbox);
            appendChild(this.toolbarButton);
            appendChild(removeButton);

            this.numberLabel.setHflex("3");
            this.labelTextbox.setHflex("30");
            this.hrefTextbox.setHflex("30");
            this.toolbarButton.setHflex("30");
            removeButton.setHflex("7");

            try {
                Utils.setPropertyOnAllChildren(this, "align", "left");
            } catch (Exception e) {
                e.printStackTrace();
            }

            this.setHflex("1");
            this.setStyle("table-layout: fixed");
        }

        /**
         * drag-and-drop implementation.
         * The idea is to rearrange the order of the Row object
         * that are children of the urlbox.
         * Then the model is recreated from the new order.
         */
        public void onDrop(DropEvent de) {
            try {
                Utils.dragAndDrop(de.getDragged().getParent(), this, getParent());
            } catch (Exception e) {
                Utils.logUnexpectedException(e);
            }
        
            linksController.restoreModel();
        }

        private void updateIndex(int i) {
            numberLabel.setValue(Integer.toString(i));
        }

        private void updateLabel(String label) {
            labelTextbox.setValue(label);
            toolbarButton.setLabel(label);
        }

        private void updateHref(String href) {
            hrefTextbox.setValue(href);
            toolbarButton.setHref(href);
        }

        public Url getUrl() {
            return link;
        }

        /**
         * Associate this UrlController with model's Url instance.
         * Make sure that dependent elements are updated whenever
         * the Url's content changes.
         */
        public void setUrl(Url link) {
            this.link = link;

            this.link.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent e) {
                    if (e.getPropertyName().equals("label"))
                        updateLabel((String)e.getNewValue());

                    if (e.getPropertyName().equals("href"))
                        updateHref((String)e.getNewValue());
                }
            });
        }
    }
}
