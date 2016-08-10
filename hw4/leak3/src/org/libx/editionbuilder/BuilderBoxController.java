package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.libx.xml.Edition;
import org.zkoss.zul.Button;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Html;
import org.zkoss.zul.Vbox;


/**
 * Control the vbox for builder functions.
 */
public class BuilderBoxController extends Vbox {
    private Html infoHtml;
    Button buildButton;
    
    public void initialize(Hbox buttonRow, Html infoHtml) {
        this.infoHtml = infoHtml;

        Model.addCurrentModelChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                buildButton.setVisible(true);
                
                final Model m = (Model)evt.getNewValue();
                displayModelInformation(m);
                m.addPropertyChangeListener(new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent evt) {
                        displayModelInformation(m);
                    }
                });
            }
        });
    }

    public void registerBuildbutton(Button button) {
        this.buildButton = button;
    }
    
    /**
     * Update relevant information here.
     */
    private void displayModelInformation(Model m) {
        Edition e = m.getEdition();
        infoHtml.setContent("Information about edition: " + e.getId() + "<br />"
            + "<a target=\"_new\" href=\"" + m.getHttpPath() + "\">edition directory</a>"
        );
    }
}
