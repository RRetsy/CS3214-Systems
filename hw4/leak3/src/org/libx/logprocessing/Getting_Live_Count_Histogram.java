package org.libx.logprocessing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class takes in the "live" file having the format, 
 * Email of the Edition Maintainer  Live Edition Count.
 * @author tgaat
 *
 */

public class Getting_Live_Count_Histogram {

    public static void main(String[] args) throws IOException {

        HashMap<String, Integer> live2count = new HashMap<String, Integer>();
        BufferedReader input = null;
        Writer output = null;

        try {
            input =  new BufferedReader(new FileReader(new File("/home/tgaat/live_count.txt")));
            Pattern p = Pattern.compile("(.*)\\s+(\\d+)");
            Matcher m;
            String line = null;

            while (( line = input.readLine()) != null) {
                m = p.matcher(line);
                String liveCount = "";
                if(m.find()) {
                    String email = m.group(1);


                    if(email.equals("libx.editions@gmail.com") || email.equals("godmar@gmail.com") || email.equals("tgaat@vt.edu")) {
                        System.out.println("$$$$$$"+email);
                        continue;                        
                    }


                    liveCount = m.group(2);
                
                    int count = 0;
                    if(live2count.containsKey(liveCount))
                        count = live2count.get(liveCount);

                    live2count.put(liveCount, count+1);
                }
            } 

            output = new BufferedWriter(new FileWriter(new File("/home/tgaat/final.txt")));
            output.write("# of Live Editions per user"+"\t"+"# of users having this Live Edition Count");

            System.out.println("$$$"+live2count.containsKey("libx.editions@gmail.com"));
            
            for(String key : live2count.keySet()) {
                
                output.write(key+"\t"+live2count.get(key)+"\n");
            }   
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            input.close();
            output.close();
        }
    }
}
