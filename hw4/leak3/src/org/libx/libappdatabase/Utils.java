package org.libx.libappdatabase;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Hashtable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xquery.XQQueryException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.MutationEvent;
import org.xml.sax.InputSource;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.util.GenericAutowireComposer;

public class Utils {
    private static class AlertDisplay extends GenericAutowireComposer {
        public AlertDisplay (String msg) {
            alert (msg);
        }
    }
    public static void alert (Throwable t) {
        t.printStackTrace();
        new AlertDisplay(t.getMessage());
    }
    // For future use
    static String escapeHtmlEntities(String s) {
        return s.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    }
    public static String stripXmlTag (String xml, String tag) {
        return xml.replaceAll("\\<.?"+ tag +".*>", "");
    }
    public static String stripXmlTagAndContent (String xml, String tag) {
        return xml.replaceAll("<"+ tag +">.+?</"+ tag +">", "");
    }
    public static String deIndent (String str, int levels) {
        String leftSpaces = "                                       ".substring(0, levels);
        StringBuilder output = new StringBuilder();

        for (String line : str.split("\\n")) {
            if (line.startsWith(leftSpaces))
                output.append(line.substring(levels));
            else
                output.append(line);

            output.append('\n');
        }
        return output.toString();
    }
    public static Hashtable<String, Component> organizeComponents (Component... list) {
        Hashtable<String, Component> table = new Hashtable<String, Component>();
        for (Component c : list) {
            table.put(c.getId(), c);
        }
        return table;
    }
    public static String readFile(String path) throws java.io.IOException {
        byte[] buffer = new byte[(int)(new File(path)).length()];
        BufferedInputStream f = new BufferedInputStream(new FileInputStream(path));
        f.read(buffer);
        f.close();
        return new String(buffer);
    }
    public static Document parseXml (String xml) throws Exception {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            doc.setXmlStandalone(false);
            return doc;
        } catch (Exception ex) {
            System.out.println("Error during xml conversion for xml " + xml);
            throw ex;
        }
    }
    public static String xmlToString(Node node) {
        try {
            Source source = new DOMSource(node);
            StringWriter stringWriter = new StringWriter();
            Result result = new StreamResult(stringWriter);
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            transformer.transform(source, result);
            return stringWriter.getBuffer().toString();
        } 
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public static String getStackTrace (Throwable throwable) {
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        throwable.printStackTrace(printWriter);
        return writer.toString();
    }

    public static void xQQueryException (XQQueryException xquery, String query) {
        String [] lines = query.split("\n");
        String line = query;
        if (xquery.getLineNumber() > 0)
            line = lines[Math.min(xquery.getLineNumber() - 1, lines.length - 1)];
        System.out.println(String.format("errorcode=%s xqsequence=%s querystack=%s",
            xquery.getErrorCode(), xquery.getErrorObject(), xquery.getQueryStackTrace()));
        System.out.println(line);
        if (xquery.getColumnNumber() > 0)
            System.out.println("                                                                                      "
                .substring(0, xquery.getColumnNumber()) + "^");
        System.out.println(xquery);
    }

    private static String printIf(String label, Object obj) {
        if (obj != null)
            return label + obj + "\n";
        else
            return "";
    }

    /** Output information about a DOM MutationEvent */
    static void outputDOMEvent(Event event) {
        MutationEvent mEvent = (MutationEvent)event;
        System.out.println(
            (mEvent.getAttrChange() == 0 ? event.toString() : (" getAttrChange=" 
            + (mEvent.getAttrChange() == MutationEvent.ADDITION ? "ADDITION" :
              mEvent.getAttrChange() == MutationEvent.MODIFICATION ? "MODIFICATION" :
              mEvent.getAttrChange() == MutationEvent.REMOVAL ? "REMOVAL" : Short.toString(mEvent.getAttrChange()))))
            + printIf(" getTarget=", mEvent.getTarget())
            + printIf(" getCurrentTarget=", mEvent.getCurrentTarget())
            + printIf(" getRelatedNode=", mEvent.getRelatedNode())
            + printIf(" newValue=", mEvent.getNewValue())
            + printIf(" prevValue=", mEvent.getPrevValue())
            + printIf(" getAttrName=", mEvent.getAttrName())
        );
    }
}
