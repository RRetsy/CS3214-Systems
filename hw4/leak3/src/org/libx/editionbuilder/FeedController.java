package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.libx.xml.Edition;
import org.libx.xml.Feed;
import org.libx.xml.Localizationfeeds;
import org.libx.xml.Whitelist;

import org.libx.xml.types.FeedTypeType;

import org.zkoss.zk.ui.Component;

import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;

import org.zkoss.zul.A;
import org.zkoss.zul.Button;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Vbox;

/**
 * Controller class for managing the feeds in an edition.
 *
 * @author Godmar Back
 */
public class FeedController
{
    public static String defaultWhiteList = "http://libx.org/libx/src/feeds/whitelist";
    public static String defaultRootFeed = "http://libx.org/libx/src/feeds/root.js";
    public static String defaultRootDescription = "LibX 1.5 Standard Cues (legacy)";

    public static String defaultRootFeedLibX2 = "http://libx.org/libx2/libapps/libxcore";
    public static String defaultRootDescriptionLibX2 = "LibX 2.0 Core Package";

    // see feeds.zul for meaning of these variables
    private Hbox whiteListBox;
    private Vbox feedList;
    @SuppressWarnings("unused")
    private Vbox feedBottomBox;
    private Checkbox haveFeeds;
    private Component feedBox;

    private Checkbox wlCheckBox;

    private Feed makeFeed(String url, String desc, FeedTypeType type) {
        Feed feed = new Feed();
        feed.setUrl(url);
        feed.setDescription(desc);
        feed.setType(type);
        return feed;
    }

    private Feed makeDefaultFeed() {
        return makeFeed(defaultRootFeed, defaultRootDescription, FeedTypeType.LEGACY);
    }

    private Feed makeDefaultFeedLibX2() {
        return makeFeed(defaultRootFeedLibX2, defaultRootDescriptionLibX2, FeedTypeType.PACKAGE);
    }

    public FeedController(Checkbox haveFeeds, Component feedBox, 
            Hbox whiteListBox, Vbox feedList, Vbox feedBottomBox) {
        this.haveFeeds = haveFeeds;
        this.feedBox = feedBox;
        this.whiteListBox = whiteListBox;
        this.feedList = feedList;
        this.feedBottomBox = feedBottomBox;

        /* add/remove all feed configuration */
        haveFeeds.addEventListener(Events.ON_CHECK, new EventListener() {
            public void onEvent(Event e) {
                Edition ed = Model.getCurrentModel().getEdition();
                boolean wantFeeds = FeedController.this.haveFeeds.isChecked();
                Localizationfeeds lfeed = null;
                if (wantFeeds) {
                    lfeed = new Localizationfeeds();
                    lfeed.addFeed(makeDefaultFeed());
                    lfeed.addFeed(makeDefaultFeedLibX2());
                    Whitelist wl = new Whitelist();
                    wl.setUrl(defaultWhiteList);
                    lfeed.setWhitelist(wl);
                }
                ed.setLocalizationfeeds(lfeed);
                showFeeds(lfeed);
            }
        });

        /* add/remove a white list */
        wlCheckBox = new Checkbox("Use a white list ");
        wlCheckBox.addEventListener(Events.ON_CHECK, new EventListener() {
            public void onEvent(Event e) {
                Localizationfeeds lfeed;
                lfeed = Model.getCurrentModel().getEdition().getLocalizationfeeds();

                Whitelist wl = null;
                if (wlCheckBox.isChecked()) {
                    wl = new Whitelist();
                    wl.setUrl(defaultWhiteList);
                }
                lfeed.setWhitelist(wl);
                showWhiteList(lfeed.getWhitelist());
            }
        });

        /* load a new model and adjust display */
        Model.addCurrentModelChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                Model m = (Model)evt.getNewValue();
                Localizationfeeds lfeeds = m.getEdition().getLocalizationfeeds();
                FeedController.this.haveFeeds.setChecked(lfeeds != null);
                showFeeds(lfeeds);
            }
        });

        feedBottomBox.appendChild(new Label("More information may go here...."));
    }

    /**
     * Show white list textbox, if whitelist exists
     */
    private void showWhiteList(Whitelist wlist) {
        whiteListBox.getChildren().clear();
        whiteListBox.appendChild(wlCheckBox);
        wlCheckBox.setChecked(wlist != null);
        if (wlist == null)
            return;

        try {
            whiteListBox.appendChild(
                Utils.createDefaultComponentForBeanProperty(wlist, "url"));
        } catch (Exception ex) {
            Utils.logUnexpectedException(ex);
        }
    }

    /**
     * Show list of current feeds.
     */
    private void showFeeds(Localizationfeeds lfeeds) {
        boolean haveFeeds = lfeeds != null;
        feedBox.setVisible(haveFeeds);
        feedList.getChildren().clear();
        if (!haveFeeds)
            return;

        for (Feed f : lfeeds.getFeed()) {
            feedList.appendChild(new FeedUrlController(f));
        }
        showWhiteList(lfeeds.getWhitelist());
    }

    /**
     * Add a new feed plus controller
     */
    public void newFeed() {
        Feed f = new Feed();
        f.setDescription("Enter a description for this feed");
        f.setUrl("http://");
        feedList.appendChild(new FeedUrlController(f));
        Model.getCurrentModel().getEdition().getLocalizationfeeds().addFeed(f);
    }

    /**
     * Add a new feed plus controller
     */
    public void addDefaultRootFeed() {
        Feed f = makeDefaultFeed();
        feedList.appendChild(new FeedUrlController(f));
        Model.getCurrentModel().getEdition().getLocalizationfeeds().addFeed(f);

        f = makeDefaultFeedLibX2();
        feedList.appendChild(new FeedUrlController(f));
        Model.getCurrentModel().getEdition().getLocalizationfeeds().addFeed(f);
    }

    /**
     * FeedUrlController.
     */
    private static class FeedUrlController extends Vbox {
        FeedUrlController(final Feed feed) {
            Hbox firstRow = new Hbox();
            try {
                for (String prop : new String [] { "description", "url" }) {
                    firstRow.appendChild(Utils.createHelpTextComponent("edition.localizationfeeds.feed." 
                                + prop));
                    firstRow.appendChild(Utils.createDefaultComponentForBeanProperty(feed, prop));
                }
            } catch (Exception ex) {
                Utils.logUnexpectedException(ex);
            }
            Hbox secondRow = new Hbox();

            try {
                if (feed.getType() == null)
                    feed.setType(FeedTypeType.LEGACY);

                Component typeBox = Utils.createDefaultComponentForBeanProperty(feed, "type");

                Hbox typeHbox = new Hbox();
                typeHbox.appendChild(new Label("Type"));
                typeHbox.appendChild(typeBox);
                secondRow.appendChild(typeHbox);
            } catch (Exception ex) {
                Utils.logUnexpectedException(ex);
            }

            final A tb = new A();
            tb.setTarget("_new");
            tb.setHref(feed.getUrl());
            tb.setLabel(feed.getDescription());
            feed.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    tb.setHref(feed.getUrl());
                    tb.setLabel(feed.getDescription());
                }
            });
            secondRow.appendChild(tb);

            Button removeButton = new Button("Remove");
            removeButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                public void onEvent(Event e) {
                    Model.getCurrentModel().getEdition().getLocalizationfeeds().removeFeed(feed);
                    FeedUrlController.this.setParent(null);
                }
            });
            secondRow.appendChild(removeButton);

            firstRow.setStyle("table-layout: fixed");
            Utils.setHflexOnChildren(firstRow, "15", "35", "15", "35");
            this.appendChild(firstRow);

            Utils.setHflexOnChildren(secondRow, "30", "60", "10");
            secondRow.setStyle("table-layout: fixed");
            this.appendChild(secondRow);
            Utils.setHflexOnAllChildren(this, "1");
            this.setHflex("1");
        }
    }
}
