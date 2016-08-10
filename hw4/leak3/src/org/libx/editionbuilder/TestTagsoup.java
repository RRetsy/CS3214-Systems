package org.libx.editionbuilder;

import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Nodes;
import nu.xom.XPathContext;

import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import org.xml.sax.helpers.XMLReaderFactory;

/*
 * Testing TagSoup + XOM
 */

public class TestTagsoup {
    static XPathContext context = new XPathContext("html", "http://www.w3.org/1999/xhtml");
    public static void main(String []argv) throws Exception {
        try {      
            XMLReader tagsoup = XMLReaderFactory.createXMLReader("org.ccil.cowan.tagsoup.Parser");
            Builder bob = new Builder(tagsoup);
            boolean dumpXML = false;
            for (int i = 0; i < argv.length; i++) {
                if (argv[i].equals("-d")) {
                    dumpXML = true;
                    continue;
                }
                Document doc = bob.build(argv[i]);
                if (dumpXML) {
                    System.out.println(doc.toXML());
                } else {
                    printForms(doc);
                    printTitle(doc);
                }
            }
        } catch (SAXException ex) {
            System.out.println("Could not load Xerces.");
            System.out.println(ex.getMessage());
        }
    }

    public static void printTitle(Document doc) {
        Nodes titles = doc.query("//html:title", context);
        System.out.println("found " + titles.size() + " title elements");
        for (int i = 0; i < titles.size(); i++) {
            System.out.println("Title #" + (i+1) + " " + ((Element)titles.get(0)).getValue());
        }
    }

    private static void dumpAttributes(Element control) {
        for (int k = 0; k < control.getAttributeCount(); k++) {
            Attribute attr = control.getAttribute(k);
            System.out.print(" ." + attr.getLocalName() + "=" + attr.getValue());
        }
        System.out.println();
    }

    /**
     * Study http://www.w3.org/TR/html401/interact/forms.html#h-17.3
     * and http://www.w3.org/TR/html401/interact/forms.html#submit-format
     * In particular discussion about "successful controls"
     */
    public static void printForms(Document doc) {
        String baseURL = doc.getBaseURI();
        System.out.println("parsing " + baseURL);
        Nodes forms = doc.query("//html:form", context);
        System.out.println("found " + forms.size() + " forms");
        for (int i = 0; i < forms.size(); i++) {
            System.out.println("Form #" + (i+1));
            Element form = (Element)forms.get(i);
            System.out.println("  .action=" + form.getAttribute("action"));
            System.out.println("  .method=" + form.getAttribute("method"));
            // note: form.query doesn't make "form" the document root
            // it's the current node (.) - so .// means any number of levels
            // removed from the 'form' node.
            Nodes controls = form.query(".//html:input", context);
            for (int j = 0; j < controls.size(); j++) {
                Element control = (Element)controls.get(j);
                // Note: getAttribute() may return null
                // use attr.getValue() to retrieve actual value
                System.out.print("   localname=" + control.getLocalName());
                dumpAttributes(control);
            }
            Nodes select = form.query(".//html:select", context);
            for (int j = 0; j < select.size(); j++) {
                Element control = (Element)select.get(j);
                dumpAttributes(control);
                Nodes options = control.query(".//html:option", context);
                for (int k = 0; k < options.size(); k++) {
                    Element option = (Element)options.get(k);
                    System.out.print("   option value=" + option.getAttributeValue("value"));
                    if (option.getAttributeValue("selected") != null)
                        System.out.print(" SELECTED");
                    System.out.println();
                }
            }
        }
    }
}
