package org.libx.autodetect;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

import java.net.URLConnection;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    /* Helper:
       reads content of entire web page at given URLConnection */
    static String slurpURL(URLConnection conn) throws IOException
    {
        DataInputStream dis = null;
        try { 
            dis = new DataInputStream(conn.getInputStream());

            int l = conn.getContentLength();    // can be -1 if unknown
            byte []b = new byte[l < 0 ? 100000 : l];
            int offset = 0;
            try {
                int bread = 0;
                do {
                    bread = dis.read(b, offset, b.length - offset);
                    offset += bread;
                } while (bread > 0);
            } catch (EOFException _) {
                ; // ignore
            }
            return new String(b, 0, offset);
        } finally {
            if (dis != null)
                dis.close();
        }
    }

    public static String getMatchGroup(String regexp, String matchstr) {
        return getMatchGroup(regexp, matchstr, 1);
    }

    public static String getMatchGroup(String regexp, String matchstr, int i) {
        Pattern pa = Pattern.compile(regexp);
        Matcher m = pa.matcher(matchstr);
        if (!m.find())
            throw new Error("string '" + matchstr + "' does not match " + regexp);
        return m.group(i);
    }
}
