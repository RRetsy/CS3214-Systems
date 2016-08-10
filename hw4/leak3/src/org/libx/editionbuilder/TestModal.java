
package org.libx.editionbuilder;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.DropEvent;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zul.Button;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Grid;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Row;
import org.zkoss.zul.Rows;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Vbox;

public class TestModal extends Vbox {


    Hashtable<String, String > code2label = new Hashtable<String, String>();
    Hashtable <Row, String> row2code  = new Hashtable<Row, String>();
    Grid gridOptions;
    String searchOptionString;
    String optionStringView;
    Label optionStringLabel;

    public TestModal() {
       
        Execution exec = Executions.getCurrent();
        Map map = exec.getArg();
        this.optionStringLabel = (Label)map.get("optionString");
    }
    
    
    public void initialize() {
        for(SearchOptions so : SearchOptions.values()) {
            switch(so) {
            case Y:
                code2label.put(so.toString(),"Keyword");
                break;
            case t:
                code2label.put(so.toString(), "Title");
                break;
            case a:
                code2label.put(so.toString(),"Author");
                break;
            case d:
                code2label.put(so.toString(),"Subject");
                break;
            case c:
                code2label.put(so.toString(),"Call Number");
                break;
            case at:
                code2label.put(so.toString(),"Article Title");
                break;
            case jt:
                code2label.put(so.toString(), "Journal Title");
                break;
            case i:
                code2label.put(so.toString(), "ISSN/ISBN");
                break;
            }
        } 
         
        this.gridOptions = drawGrid();
        this.appendChild(gridOptions);
    }

    public String getSearchoptionString() {
        return this.searchOptionString;
    }


    public Grid drawGrid() {

        System.out.println("DRAW GRID CALLED");
        Grid g = new Grid();
        Rows rows = new Rows();
        rows.setDraggable("true");
        rows.setDroppable("true");

        for(SearchOptions so : SearchOptions.values()) {         
            OptionsRow r = new OptionsRow(so);
            rows.appendChild(r);
        }       
        g.appendChild(rows);
        return g;
    }

    public static class FakeOptionBean {
        String value;

        public String getValue() {
            return value;
        }

        public void setValue(String val) {
            this.value = val;
        }
    }
    
    public String[] splitOptionString(String optionString) {

        String[] strarr = optionString.split(",");
        for(int i = 0; i < strarr.length; i++) {
            System.out.println("#####"+strarr[i]);
        }
        return strarr;
    }

    public void setOptionString(String optionString) {
        System.out.println("SET OPTIONS STRING CALLED");
        searchOptionString = optionString;
        System.out.println("SEARCH OPTION STRING"+this.searchOptionString);
    }


    public class OptionsRow extends Row {
        public OptionsRow(SearchOptions so) {
            this.setDraggable("true");
            this.setDroppable("true");
            SearchoptionsCheckbox c = new SearchoptionsCheckbox();
            Label optLabel = new Label();
            optLabel.setValue(code2label.get(so.toString())); 
            
            this.appendChild(c);
            Hbox changeBox = new Hbox();
            changeBox.appendChild(optLabel);
            this.appendChild(changeBox);
                      
            ChangeButton cb = new ChangeButton(c, optLabel, changeBox);
            this.appendChild(cb);
            row2code.put(this, so.toString());
            
            this.addEventListener("onDrop", new Utils.EventListenerAdapter() {
                public void onEvent(Event e) {
                    
                    System.out.println("###############");
                    DropEvent de = (DropEvent )e;
                    Component draggedComp = de.getDragged();
                    if (!(draggedComp instanceof OptionsRow)) {
                        return;
                    }

                    Component rows = OptionsRow.this.getParent();
                    OptionsRow source = (OptionsRow)draggedComp;
                    int targetIndex = rows.getChildren().indexOf(OptionsRow.this);
                    int sourceIndex = rows.getChildren().indexOf(source);

                    if (sourceIndex < targetIndex) {
                        if (targetIndex == rows.getChildren().size() - 1)
                            rows.appendChild(source);
                        else
                            rows.insertBefore(source,
                                    (Component)rows.getChildren().get(targetIndex+1));
                    } else {
                        rows.insertBefore(source, OptionsRow.this);
                    }                
                }      
            });
            
        }

        public void onDrop(DropEvent de) throws Exception{
            Component draggedComp = de.getDragged();
            if (!(draggedComp instanceof OptionsRow)) {
                return;
            }

            Component rows = getParent();
            OptionsRow source = (OptionsRow)draggedComp;
            int targetIndex = rows.getChildren().indexOf(this);
            int sourceIndex = rows.getChildren().indexOf(source);

            if (sourceIndex < targetIndex) {
                if (targetIndex == rows.getChildren().size() - 1)
                    rows.appendChild(source);
                else
                    rows.insertBefore(source,
                            (Component)rows.getChildren().get(targetIndex+1));
            } else {
                rows.insertBefore(source, this);
            }
        }
    }



    public class SearchoptionsCheckbox extends Checkbox {
        public void onCheck(Event e) throws Exception {
            searchOptionString = "";
            optionStringView = "";
            Rows rows = gridOptions.getRows();
            List rowList = rows.getChildren();

            for(int i=0;i<rowList.size();i++) {
                Row r = (Row)rowList.get(i);
                List rowChildren = r.getChildren();

                for(int j= 0; j<rowChildren.size();j++) {
                    if(rowChildren.get(j) instanceof Checkbox) {
                        Checkbox check = (Checkbox)rowChildren.get(j);
                        if(check.isChecked()) {
                            String code = row2code.get(r);
                            if(code != null) {
                                TestModal.this.searchOptionString += code+";";
                                TestModal.this.optionStringView += code2label.get(code)+",";
                                TestModal.this.optionStringLabel.setValue(optionStringView);
                            }
                        }
                        
                        
                    }
                }
            }
            System.out.println("The searchoption string is "+searchOptionString); 
        }
    }

    public class ChangeButton extends Button {

        Checkbox check;
        Hbox parent;
        Label lab; 
        
        public ChangeButton(Checkbox c, Label l, Hbox prnt) {
            this.check = c;
            this.parent = prnt;
            this.lab = l;
            this.setLabel("Change");
        }        
        public void onClick(Event e) throws Exception {
          
            //final Label lab = new Label("Enter the new Label here");
            this.setDisabled(true);
            final Textbox tb = new Textbox();
            tb.setValue(lab.getValue());
            lab.setValue("");
           // final Button submit = new Button();
           // submit.setLabel("Submit");
            tb.addEventListener("onChange", new Utils.EventListenerAdapter() {
                public void onEvent(Event e) {                
                    String code = row2code.get((Row)check.getParent());
                    lab.setValue(tb.getValue());
                    code2label.put(code, tb.getValue());
                    parent.getChildren().clear();
                    parent.appendChild(lab);
                    //parent.appendChild(new ChangeButton(check,parent));
                    ChangeButton.this.setDisabled(false);
                    
                }      
            });   
            //parent.appendChild(lab);
            parent.appendChild(tb);
            //parent.appendChild(submit);
        }       
    }


    public enum SearchOptions {
        Y, t, a, d, i, c, jt, at         
    }    
}