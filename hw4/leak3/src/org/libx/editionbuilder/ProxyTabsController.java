package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;

import org.libx.xml.Proxy;
import org.libx.xml.ProxyItem;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.DropEvent;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Button;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Html;
import org.zkoss.zul.Vbox;

public class ProxyTabsController extends Hbox {

    private ProxyList proxylist;

    /**
     * This method registers the listener to listen for any changes happening to the model
     * @param proxyList
     */
    public void initialize(ProxyList proxyList, Vbox proxyChoiceButtonVbox) {
        this.proxylist = proxyList;

        String [] proxyTypes = new String [] { "ezproxy", "wam" };
        String [] proxyDesc = new String [] { "EZ Proxy", "WAM     " };
        for (int i = 0; i < proxyTypes.length; i++) {
            Button b = new Button("Add New " + proxyDesc[i]);
            final String proxyType = proxyTypes[i];
            b.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                public void onEvent(Event e) {
                    try {
                        proxylist.addNewProxy(proxyType);
                    } catch (Exception ex) {
                        MainWindowController.showException(ex);
                    }
                }
            });
            proxyChoiceButtonVbox.appendChild(b);
        } 

        Model.addCurrentModelChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    loadModel((Model)evt.getNewValue());    
                } catch (Exception e) {
                    Utils.logUnexpectedException(e);
                }
            }
        });
    }


    private void loadModel(Model m) throws Exception {
        proxylist.loadModel(m);
    }

    /**
     * This class contains the list of all the proxies that have been appended to the view, 
     * either from the model or by the user.
     * @author tgaat
     *
     */
    public static class ProxyList extends Vbox {

        /**
         * Gets the list of proxies from the model and populates the view with them.
         * @param model
         * @throws Exception
         */
        private void populateList(Model model) throws Exception {
            getChildren().clear(); 
            Proxy proxy = (Proxy)model.getEdition().getProxy();
            for (ProxyItem i : proxy.getProxyItem()) {
                addProxyController(i.getChoiceValue());
            }
            this.setVisible(proxy.getProxyItemCount() > 0);
        }

        /**
         * 
         * @param proxyitem
         * @throws Exception
         */
        void addProxyController(Object proxyitem) throws Exception {
            appendChild(new ProxyController(this, proxyitem));
        }

        
        /**
         * Adds a new Proxy and sets the required attributes of the proxy to some default.
         * @param proxyitem
         * @throws Exception
         */
        void addProxy(Object proxyitem) throws Exception {
            this.setVisible(true);            
            addProxyController(proxyitem);
            Proxy proxy = (Proxy)Model.getCurrentModel().getEdition().getProxy();
            ProxyItem pitem = new ProxyItem();
            String pname = proxyitem.getClass().getName().replaceFirst(".*\\.", "");
            Method setter = Utils.beanPropertySetter(pitem, pname, proxyitem.getClass());
            setter.invoke(pitem, proxyitem);
            Utils.setRequiredAttributes(proxyitem);
            proxy.addProxyItem(pitem);
        }


        /**
         * Adds a new proxy object depending on the type selected by the user. 
         * @param ptype
         * @throws Exception
         */
        void addNewProxy(String ptype) throws Exception {
            Class c = Class.forName("org.libx.xml." + Utils.upperCaseName(ptype));          
            addProxy(c.newInstance());
        }

        /**
         * Loads the model and calls the method that populates the initial list of proxies.
         * @param model
         * @throws Exception
         */
        private void loadModel(final Model model) throws Exception {
            populateList(model);

            model.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("items".equals(evt.getPropertyName())) {
                        try {
                            populateList(model);
                        } catch (Exception e) {
                            Utils.logUnexpectedException(e);
                        }
                    }
                }
            });
        }

        /**
         * This method restores the model, when the list of proxies changes; as in
         * case of drag and drop of proxies. 
         * @throws Exception
         */
        void restoreModel() throws Exception {
            Proxy proxy = Model.getCurrentModel().getEdition().getProxy();
            int children = this.getChildren().size();

            ProxyItem[] pitems = new ProxyItem[children];

            for (int i = 0; i < children; i++) {

                ProxyItem pi = new ProxyItem();
                Object pro = ((ProxyController)this.getChildren().get(i)).getProxyObject();
                String pname = pro.getClass().getName().replaceFirst(".*\\.", "");

                Method setter = Utils.beanPropertySetter(pi, pname, pro.getClass());

                setter.invoke(pi, pro);
                pitems[i] = pi;
            }
            proxy.setProxyItem(pitems);
        }

    }

    /**
     * Represents the controller corresponding to each proxy 
     * @author tgaat
     *
     */
    public static class ProxyController extends Vbox {

        private Object pro;
        private ProxyList parent;
      
        /**
         * The delete button deletes a resolver from the view as well as the model
         * @author tgaat
         *
         */
        public static class Delbutton extends Button {
            private ProxyList clist;
            public Delbutton(ProxyList grandparent) {
                clist = grandparent;
            }    
            public void onClick(Event e) throws Exception {  
                getParent().setParent(null);
                clist.restoreModel();
                clist.setVisible(Model.getCurrentModel().getEdition().getProxy().getProxyItemCount() > 0);
            }
        }

        /**
         * Constructor draws the Grid corresponding to each proxy
         * @param parent
         * @param proxyitem
         * @throws Exception
         */
        ProxyController(ProxyList parent, Object proxyitem) throws Exception {
         
            this.pro = proxyitem;
            this.parent = parent;
            Html content = new Html();
            Utils.bindDependentBeanProperty(proxyitem, "name", content, "content", "<b>Proxy <i>%s</i></b>");
            appendChild(content);
            Vbox g = Utils.drawTable(proxyitem, Utils.acceptAll);
            content.setDraggable("true");
            setDroppable("true");
            appendChild(g);
            this.setWidth("100%");
            
            Delbutton d = new Delbutton(this.parent);
            Utils.bindDependentBeanProperty(proxyitem, "name", d, "label", "Delete Proxy %s");
            appendChild(d);
        }

        /**
         * Helper method to get the proxy model object linked to the controller 
         * @return
         */

        private Object getProxyObject() {
            return this.pro;
        }

        /**
         * Implementation of drag and drop of proxies
         * @param de
         * @throws Exception
         */

        public void onDrop(DropEvent de) throws Exception{
            Component draggedComp = de.getDragged().getParent();
            if (!(draggedComp instanceof ProxyController)) {
                return;
            }

            ProxyList proxylist = (ProxyList)getParent();
            ProxyController source = (ProxyController)draggedComp;
            int targetIndex = proxylist.getChildren().indexOf(this);
            int sourceIndex = proxylist.getChildren().indexOf(source);

            if (sourceIndex < targetIndex) {
                // moving down
                if (targetIndex == proxylist.getChildren().size() - 1)
                    proxylist.appendChild(source);
                else
                    proxylist.insertBefore(source, 
                            (Component)proxylist.getChildren().get(targetIndex+1));
            } else {
                // moving up
                proxylist.insertBefore(source, this);
            }

            proxylist.restoreModel();
        } 
    }
}
