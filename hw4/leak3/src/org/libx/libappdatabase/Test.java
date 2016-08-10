package org.libx.libappdatabase;

public class Test {
    public static void main (String [] args) {
        try {
            XMLDataModel m = new XMLDataModel();
            System.out.println(Utils.xmlToString(m.getTreeAsDomDocument("libx2_feed").getDocumentElement()));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

    }
}
