package org.libx.editionbuilder;

import java.io.File;
import java.io.FileReader;

import java.lang.reflect.Field;

import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import java.util.concurrent.Future;

import org.exolab.castor.types.AnyNode;

import org.exolab.castor.xml.Unmarshaller;

import org.libx.xml.Resolver;

import org.libx.xml.oclcregistry.Records;
import org.libx.xml.oclcregistry.ResolverRegistryEntry;

import org.libx.xml.types.ResolverTypeType;

import org.xml.sax.InputSource;

/**
 *
 * Automatic detection of the OpenURL resolver.
 * Before we probe, we use OCLC's OpenURL resolver registry.
 * See http://www.oclc.org/productworks/gateway.doc
 *
 * @author Godmar Back
 */
public class OpenurlDetector 
{
    private String clientIP;
    List<Resolver> detect(String ip) throws Exception {
        this.clientIP = ip;
        URL u = new URL("http://worldcatlibraries.org/registry/lookup?IP=" + InetAddress.getAllByName(ip)[0].getHostAddress());
        URLConnection conn = u.openConnection();
        conn.connect();
        return detect(new InputSource(conn.getInputStream()));
    }

    private String institutionId;
    private Future<ArrayList<String>> opacBaseList;
    
    /**
     * If learned, return OCLC institution id.
     * @return
     */
    String getInstitutionId() {
        return institutionId;
    }
    
    /**
     * If learned, return future representing OCLC base opac list.
     * @return
     */
    Future<ArrayList<String>> getOpacBaseList() {
        return opacBaseList;
    }

    List<Resolver> detect(InputSource source) throws Exception {
        List<Resolver> l = new ArrayList<Resolver>();
        Records records = (Records)Unmarshaller.unmarshal(Records.class, source);
        Enumeration e = records.enumerateResolverRegistryEntry();
        while (e.hasMoreElements()) {
            ResolverRegistryEntry rre = (ResolverRegistryEntry)e.nextElement();
            Resolver r = new Resolver();
            r.setType(ResolverTypeType.GENERIC);

            org.libx.xml.oclcregistry.Resolver or = rre.getResolver();
            
            /* as a side-effect, we obtain the OCLC institution ID, which can be used to retrieve
             * the OCLC profile. */
            institutionId = rre.getInstitutionID();
            opacBaseList = CatalogDetector.getOpacBaseListFromOCLCInstitutionRepository(institutionId);
            
            AnyNode baseurl = (AnyNode)or.getBaseURL();
            if (baseurl != null) {
                String url = baseurl.getStringValue().trim();
                // remove trailing ? if given
                if (url.endsWith("?"))
                    url = url.substring(0, url.length() - 1);
                r.setUrl(url);
            }

            AnyNode linkicon = (AnyNode)or.getLinkIcon();
            if (linkicon != null)
                r.setImage(linkicon.getStringValue());

            AnyNode linktext = (AnyNode)or.getLinkText();
            if (linktext == null || linktext.getStringValue().trim().equals(""))
                r.setName(rre.getInstitutionName() + "'s resolver");
            else
                r.setName(linktext.getStringValue());

            if ("serialsSolutions".equalsIgnoreCase(or.getVendor()))
                r.setType(ResolverTypeType.SERSOL);
            else if ("SFX".equalsIgnoreCase(or.getVendor()))
                r.setType(ResolverTypeType.SFX);

            l.add(r);

            Utils.printLog("%s: found resolver: url= %s name=%s", clientIP, r.getUrl(), r.getName());
        }
        return l;
    }

    /* for off-line testing: supply either an IP address or a filename
     * as input.  The file would have the content from 
     * http://worldcatlibraries.org/registry/lookup?IP=...
     * in it.
     */
    public static void main(String []av) throws Exception {
        OpenurlDetector d = new OpenurlDetector();
        List<Resolver> l;
        if (new File(av[0]).exists())
            l = d.detect(new InputSource(new FileReader(av[0])));
        else
            l = d.detect(av[0]);
        for (Resolver r : l) {
            System.out.print(r + " [");
            for (Field f : Resolver.class.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.get(r) != null)
                    System.out.print(f.getName() + "='" + f.get(r) + "', ");
            }
            System.out.println("]");
        }
    }
}
