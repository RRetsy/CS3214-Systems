package org.libx.logprocessing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exolab.castor.xml.Unmarshaller;
import org.libx.editionbuilder.DbUtils;
import org.libx.editionbuilder.Model;
import org.libx.xml.Edition;


public class Log_Processing {

    static Pattern p = Pattern.compile("^(Sun|Mon|Tue|Wed|Thu|Fri|Sat)");
    static Pattern pDate = Pattern.compile("[Sun|Mon|Tue|Wed|Thu|Fri|Sat]\\s(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s(\\d+) (\\d+):(\\d+):(\\d+)");
    static Pattern pLoggedIn = Pattern.compile("logged in successfully");

    static Pattern pMadeLive = Pattern.compile("Made Live edition (.+) revision (\\d+)");
    static Pattern pHelp = Pattern.compile("user read (.*)");
    static Pattern pAllEditionsSearch = Pattern.compile("alleditiontabsearch hits=\\d+ term=%(.*)%");
    static Pattern pWorldcatRegistrySearch = Pattern.compile("worldcatregistry search hits=(.*) term=(.*)");
    static Pattern pcleanHelpNames = Pattern.compile("/src/docs/(.*)\\.html");
    static Pattern pAutoDetectionUrl = Pattern.compile("user added detected catalog Found (.*) catalog '(.*)' at http://(.*)");
    static Pattern pAutoDetectionDatabase = Pattern.compile("user added detected catalog Found catalog '(.*)' in database.*");
    static Pattern pNewModelLoaded = Pattern.compile("new model loaded edition=(.+) revision=.*");
    static Pattern pDesktopCleaned = Pattern.compile("Desktop cleaned up");
    static Pattern pDesktopCleaned2 = Pattern.compile("Desktop cleaned up \\[Desktop (.+)\\]");
    static Pattern pNewDesktop = Pattern.compile("new desktop");
    static Pattern pMadeFromScratch = Pattern.compile("The edition (.+) is started from scratch");
    static Pattern pCloned = Pattern.compile("The edition (.+) is a clone of (.+)");
    static Pattern pOpenUrl = Pattern.compile("user added detected openurl resolver http://(.+)");

    static DateFormat df = new SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy");
    static String epoch = "Wed Dec 31 19:00:00 EST 1969";

    static Date epochDate = getEpochDate();


    static HashMap<String, Set<String>> email2session = new HashMap<String, Set<String>>();
    static HashMap<String, Set<String>> session2desktop = new HashMap<String, Set<String>>();

    static HashMap<String,DesktopInfo> desktop2desktopInfo = new HashMap<String, DesktopInfo>();
    static HashMap<String,DesktopInfo> liveEditionSessions = new HashMap<String, DesktopInfo>();
    static HashMap<String,Integer> month2monthIndex = new HashMap<String, Integer>();
    static HashMap<String,Date> desktop2LastDate = new HashMap<String,Date>();
    static Vector<ExamineLinePlugin> examinePluginVector = new Vector<ExamineLinePlugin>();
    static Vector<PostProcessingPlugin> postProcessingPluginVector = new Vector<PostProcessingPlugin>();
    static HashMap<String,Integer> repeatedDesktops2LastIndex = new HashMap<String, Integer>();

    static String[] excludedEmails = {"godmar@gmail.com","tgaat@vt.edu","afbailey@vt.edu","libx.editions@gmail.com","kgoldbec@vt.edu"};
    static Set<String> excludedEmailSet = new HashSet<String>(Arrays.asList(excludedEmails));

    public static Date getEpochDate() {
        Date date = null;
        try {
            date = df.parse(epoch);
        } catch(Exception exc) {
            exc.printStackTrace();
        }
        return date;
    }

    public static class ClonedEditionInfo {
        String editionId;
        String clonedFrom;
        public ClonedEditionInfo(String editionId, String clonedFrom) {
            this.editionId = editionId;
            this.clonedFrom = clonedFrom;
        }
    }

    public static class LiveEditionInfo {
        String editionId;
        int revision;

        public LiveEditionInfo(String editionId, int revision) {
            this.editionId = editionId;
            this.revision = revision;
        }
    }


    public static class DesktopInfo {
        boolean madeLive;
        String liveEdition;
        int liveEditionRev;

        String id;
        String month;

        Date date=null;
        long duration = 0;
        boolean isClosed = false;

        List<String> helpReadSet = new ArrayList<String>();
        List<String> searchTermSet = new ArrayList<String>();
        List<String> worldCatSearchTermSet = new ArrayList<String>();
        List<String> urlAutoDetectionSet = new ArrayList<String>();  
        List<String> typeAutoDetectionSet = new ArrayList<String>();
        List<String> dbAutoDetectionSet = new ArrayList<String>();
        HashSet<String> newEditionSet = new HashSet<String>();
        HashSet<String> liveEditionSet = new HashSet<String>();
        List<String> madeFromScratch = new ArrayList<String>();
        List<String> openUrl = new ArrayList<String>();
        List<ClonedEditionInfo> clonedEditions = new ArrayList<ClonedEditionInfo>();


        DesktopInfo() {
            this.date = new Date();
            this.date.setTime(0);
        }

        public void setMadeLive(boolean madeLive) {
            this.madeLive = madeLive;
        }

        public void setEdition(String edition) {
            this.liveEdition = edition;
        }      

        public void addToHelpRead(String helpRead) {
            helpReadSet.add(helpRead);
        }

        public void addToWorldCat(String worldCatSearchTerm) {
            worldCatSearchTermSet.add(worldCatSearchTerm);
        }
    }

    public static enum SearchEnum {
        AllEditions,
        WorldCatRegistry,
        AutoDetectionUrl,
        AutoDetectionType,
        AutoDetectionDb,
        OpenUrl
    }

    public interface ExamineLinePlugin {
        public void examine(String session, String desktop, String message);
    }

    public interface PostProcessingPlugin {
        public void process() throws Exception;
    }

    static ExamineLinePlugin loginPlugin = new ExamineLinePlugin () {
        {
            examinePluginVector.add(this);
        }
        public void examine(String session, String desktop, String message) {
            examineForLoggedIn(session, desktop, message);   
        }
    };


    static ExamineLinePlugin readHelpPlugin = new ExamineLinePlugin () {
        {
            examinePluginVector.add(this);
        }
        public void examine(String session, String desktop, String message) {
            examineForReadHelp(session, desktop, message);   
        }
    };


    static ExamineLinePlugin autoDetectUrlExamineLinePlugin = new ExamineLinePlugin () {
        {
            examinePluginVector.add(this);
        }
        public void examine(String session, String desktop, String message) {
            examineForAutoDetectionUrl(session, desktop, message);           
        }
    };

    static ExamineLinePlugin autoDetectDbExamineLinePlugin = new ExamineLinePlugin () {
        {
            examinePluginVector.add(this);
        }
        public void examine(String session, String desktop, String message) {
            examineForAutoDetectionDb(session, desktop, message);                  
        }
    };


    static ExamineLinePlugin worldcatSearchExamineLinePlugin = new ExamineLinePlugin () {
        {
            examinePluginVector.add(this);
        }
        public void examine(String session, String desktop, String message) {
            examineForWorldCatSearch(session, desktop, message);                          
        }
    };

    static ExamineLinePlugin allEditionSearchExamineLinePlugin = new ExamineLinePlugin () {
        {
            examinePluginVector.add(this);
        }
        public void examine(String session, String desktop, String message) {
            examineForAllEditionsSearch(session, desktop, message);
        }
    };

    static ExamineLinePlugin newModelExamineLinePlugin = new ExamineLinePlugin () {
        {
            examinePluginVector.add(this);
        }
        public void examine(String session, String desktop, String message) {
            examineForNewModel(session, desktop, message);        
        }
    };

    static ExamineLinePlugin madeLiveExamineLinePlugin = new ExamineLinePlugin () {
        {
            examinePluginVector.add(this);
        }
        public void examine(String session, String desktop, String message) {
            examineForMadeLive(session, desktop, message);                
        }
    };

    static ExamineLinePlugin madeFromScratch = new ExamineLinePlugin () {
        {
            examinePluginVector.add(this);
        }
        public void examine(String session, String desktop, String message){

            Matcher mMadeFromScratch = pMadeFromScratch.matcher(message);


            if(mMadeFromScratch.find()) {

                DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
                if(desInfo == null) {
                    desInfo = new DesktopInfo();
                }

                desInfo.madeFromScratch.add(mMadeFromScratch.group(1));

                desktop2desktopInfo.put(desktop, desInfo);
            }         
        }       
    };

    static ExamineLinePlugin clonedEditions = new ExamineLinePlugin () {
        {
            examinePluginVector.add(this);
        }
        public void examine(String session, String desktop, String message){

            Matcher mCloned = pCloned.matcher(message);

            if(mCloned.find()) {

                DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
                if(desInfo == null) {
                    desInfo = new DesktopInfo();
                }

                desInfo.clonedEditions.add(new ClonedEditionInfo(mCloned.group(1),mCloned.group(2)));                
                desktop2desktopInfo.put(desktop, desInfo);
            }         
        }       
    };

    static ExamineLinePlugin openUrlExaminePlugin = new ExamineLinePlugin () {
        {
            examinePluginVector.add(this);
        }
        public void examine(String session, String desktop, String message) {
            Matcher mOpenUrl = pOpenUrl.matcher(message);


            if(mOpenUrl.find()) {
                DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
                if(desInfo == null) {
                    desInfo = new DesktopInfo();
                }
                desInfo.openUrl.add(mOpenUrl.group(1));              
                desktop2desktopInfo.put(desktop, desInfo);
            }   
        }
    };


    static PostProcessingPlugin openUrlProcessing = new PostProcessingPlugin () {
        {
            postProcessingPluginVector.add(this);
        }

        public void process() throws Exception {
            getMonthWiseStatisticsOfOpenUrls();
        }
    };

    static PostProcessingPlugin editionComplexity = new PostProcessingPlugin () {
        {
            postProcessingPluginVector.add(this);
        }
        public void process() {
            try {
                measureEditionComplexity();
            } catch (Exception exc) {
                exc.printStackTrace();
            }
        }
        private void measureEditionComplexity() throws Exception {

            Writer outputSearch = new BufferedWriter(new FileWriter(new File(Config.complexityPath)));
            outputSearch.write("#Edition_id\tNumber_of_links\tNumber_of_catalogs\tNumber of proxies\tNumber of Additional Files\n");


            for(String desktop : desktop2desktopInfo.keySet()) {

                try {
                    DesktopInfo desInfo = desktop2desktopInfo.get(desktop);

                    for(String editionId : desInfo.liveEditionSet) {

                        String path = Model.id2RelPath(editionId) + "." + desInfo.liveEditionRev;

                        Edition edition = null;

                        FileReader r = new FileReader(Config.editionsDirPath + path + "/" + "config.xml");
                        edition = (Edition)Unmarshaller.unmarshal(Edition.class, r);
                        outputSearch.write(edition.getId()+"\t"+edition.getLinks().getUrlCount()+"\t"+edition.getCatalogs().getCatalogsItemCount()+"\t"+edition.getProxy().getProxyItemCount()+"\t"+edition.getAdditionalfiles().getFileCount()+"\n");
                    }
                } catch(Exception exc) {
                    exc.printStackTrace();
                }
            }

            outputSearch.close();
        } 
    };

    static Pattern pDumpFileName = Pattern.compile("n20.+\\.sql");
    static Pattern pDumpFileName1 = Pattern.compile("20.+\\.sql");
    static Pattern pDumpFileDetailPattern = Pattern.compile("(n?)(\\d{4})(\\d{2})(\\d{2})");

    static PostProcessingPlugin publicEditionCountProcessing = new PostProcessingPlugin() {
        String dbName = "temp_db";

        {
            postProcessingPluginVector.add(this);

        }

        public void process() throws Exception {
            InputStream stderr=null;
            InputStream stdout=null;

            Writer outputSearch = new BufferedWriter(new FileWriter(new File(Config.publicEditionsCountPath)));
            outputSearch.write("#Year\tMonth\tDay\tEdition_id\tNumber_of_links\tNumber_of_catalogs\tNumber of proxies\tNumber of Additional Files\n");

            FilenameFilter filter = new FilenameFilter() {          
                public boolean accept(File f, String name) {
                    return pDumpFileName.matcher(name).find() || pDumpFileName1.matcher(name).find();
                }
            };          

            File f = new File(Config.databaseDumpsPath);  
            File[] dbDumps = f.listFiles(filter);
            String script = Config.dbRestoreCommand+" "+dbName+" ";
            String dumpsDir = Config.databaseDumpsPath;

            script = script+dumpsDir;

            for(int i = 0; i < dbDumps.length; i++) {
                File dumpFile = dbDumps[i];

                String fname = dumpFile.getName();
                String newScript = script+fname;

                Matcher mDumpFileDetailMatcher = pDumpFileDetailPattern.matcher(fname);
                if(mDumpFileDetailMatcher.find()) {

                    String isNew = mDumpFileDetailMatcher.group(1);
                    if(!isNew.equals("n")) {

                        continue;
                    }


                    String year = mDumpFileDetailMatcher.group(2);
                    String month = mDumpFileDetailMatcher.group(3);
                    String date = mDumpFileDetailMatcher.group(4);

                    System.out.println("**"+fname);

                    runDatabaseRestoreScript(newScript, stderr, stdout);

                    int publicEditionCount = DbUtils.getTableCount("editionInfo", "isPublic = 1");
                    int editionCount = DbUtils.getTableCount("editionInfo");
                    int catalogCount = DbUtils.getTableCount("catalogInfo");
                    int userCount = DbUtils.getTableCount("userInfo");

                    outputSearch.write(year+"\t"+month+"\t"+date+"\t"+publicEditionCount+"\t"+editionCount+"\t"+catalogCount+"\t"+userCount+"\n");
                }
            }

            outputSearch.close(); 
        }


    };

    private static void runDatabaseRestoreScript(String script,
            InputStream stderr, InputStream stdout) {
        try {

            Process p = Runtime.getRuntime().exec(script);

            stderr = p.getErrorStream();
            stdout = p.getInputStream();

            int status = p.waitFor();

            if (status != 0)
                throw new Exception(" failed, exit status= " + status);

            System.out.println(" successful."+stdout.toString());

        } catch (Exception e) { 
            e.printStackTrace();
            System.out.println(stderr.toString() + "\n(STDOUT was:)\n" + stdout.toString());

        }
    }


    static PostProcessingPlugin liveCountProcessing = new PostProcessingPlugin() {
        {
            postProcessingPluginVector.add(this);
        }
        public void process() throws Exception {
            getLiveCountHistogram();                    
        }
    };

    static PostProcessingPlugin helpCountProcessing = new PostProcessingPlugin() {
        {
            postProcessingPluginVector.add(this);
        }
        public void process() throws Exception {
            getHelpCountHistogram();                           
        }
    };

    static PostProcessingPlugin allEditionsSearchProcessing = new PostProcessingPlugin() {
        {
            postProcessingPluginVector.add(this);
        }
        public void process() throws Exception {
            getMonthWiseStatisticsOfAllEditionsSearch();                           
        }
    };

    static PostProcessingPlugin worldCatSearchProcessing = new PostProcessingPlugin() {
        {
            postProcessingPluginVector.add(this);
        }
        public void process() throws Exception {
            getMonthWiseStatisticsWorldCatRegistrySearch();                           
        }
    };


    static PostProcessingPlugin autoDetectionProcessing= new PostProcessingPlugin() {
        {
            postProcessingPluginVector.add(this);
        }
        public void process() throws Exception {

            getMonthWiseStatisticsAutoDetectionUrl();

            getMonthWiseStatisticsAutodetectionDatabase();

            getMostCommonlyDetectedCatalogTypes();

            getAutoDetectionUsagePerUser();

        }
    };  


    static PostProcessingPlugin liveEditionsTimeProcessing = new PostProcessingPlugin() {
        {
            postProcessingPluginVector.add(this);
        }
        public void process() throws Exception {
            printDesktopDurations();
            printTimesOfAllLiveEditions();        
        }
    };

    static PostProcessingPlugin scratchVsClonedProcessing = new PostProcessingPlugin() {
        {
            postProcessingPluginVector.add(this);          
        }
        public void process() throws Exception {

            Writer output = new BufferedWriter(new FileWriter(new File(Config.scratchVsClonedPath)));
            //Should be represented as pie chart 
            output.write("#Total count of Editions Made from Scratch\tTotal count of Editions Cloned\n");

            int countScratch = 0;
            int countCloned = 0;
            for(String desktop : desktop2desktopInfo.keySet()) {
                DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
                countScratch += desInfo.madeFromScratch.size();
                countCloned += desInfo.clonedEditions.size();
            }
            output.write(countScratch+"\t"+countCloned);
            output.close();

            //Represent as histogram
            output = new BufferedWriter(new FileWriter(new File(Config.clonedFromDetailsPath)));
            output.write("#EditionId\tClonedFrom\n");
            for(String desktop : desktop2desktopInfo.keySet()) {
                DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
                for(ClonedEditionInfo clonedEditionInfo : desInfo.clonedEditions){
                    output.write(clonedEditionInfo.editionId+"\t"+clonedEditionInfo.clonedFrom+"\n");
                }
            } 
            output.close();
        }
    };

    static PostProcessingPlugin dbVsUrlProcessing = new PostProcessingPlugin() {
        {
            postProcessingPluginVector.add(this);          
        }
        public void process() throws Exception {

            Writer output = new BufferedWriter(new FileWriter(new File(Config.dbVsUrlAutoDetectionPath)));
            //Should be represented as pie chart 
            output.write("#Total count of Catalogs Imported From Database\tTotal count of Catalogs Autodetected by heuristics\n");

            int countDb = 0;
            int countUrl = 0;
            for(String desktop : desktop2desktopInfo.keySet()) {
                DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
                countDb += desInfo.dbAutoDetectionSet.size();
                countUrl += desInfo.urlAutoDetectionSet.size();
            }
            output.write(countDb+"\t"+countUrl);
            output.close();
        }
    };

    public static void main(String[] args) throws Exception {


        BufferedReader input = null;

        try {

            String line=null;
            input =  new BufferedReader(new FileReader(new File(Config.logFilePath)));

            while (( line = input.readLine()) != null) {

                Matcher m = p.matcher(line);
                if(m != null && !m.find()) {
                    continue;
                } 

                String datePart = "";
                String secondElement="";
                String session = "";
                String desktop = "";
                String message = "";
                String month = "";

                String[] arr = line.split(": ");

                if(arr.length >= 2) {              
                    datePart = arr[0];
                    secondElement = arr[1];
                }

                if(arr.length >= 3) {
                    message = arr[2];
                    if(arr.length > 3) {
                        int k = 3;
                        while (k < arr.length) {
                            message +=" "+arr[k++];
                        }
                    }
                }


                Matcher mDate = pDate.matcher(datePart);

                if(mDate.find()) {
                    month = mDate.group(1);
                }

                Date cur_date = df.parse(datePart);
                String[] ipAddress_Session_arr = secondElement.split(":");

                if(ipAddress_Session_arr.length == 3) {
                    session = ipAddress_Session_arr[1];
                    desktop = ipAddress_Session_arr[2];

                    if(desktop.split(" ").length > 1 || desktop.length() > 5) {
                        continue;
                    }

                    Set<String> desktopList = new HashSet<String>();
                    if(session2desktop.containsKey(session)) 
                        desktopList = session2desktop.get(session);

                    desktopList.add(desktop);

                    session2desktop.put(session, desktopList);

                    desktop = session+";"+desktop;

                    DesktopInfo desInfo = new DesktopInfo();

                    if(desktop2desktopInfo.containsKey(desktop))
                        desInfo = desktop2desktopInfo.get(desktop);

                    desInfo.month = month;                    

                    desktop2desktopInfo.put(desktop, desInfo);

                    desInfo.duration = cur_date.getTime() - desInfo.date.getTime();
                }

                examineForNewDesktop(session, datePart, desktop, message);

                examineForDesktopCleaned(session, line, datePart, desktop);

                for(int i=0;i < examinePluginVector.size();i++) {
                    examinePluginVector.get(i).examine(session, desktop, message);
                }                        
            }

            for(int i=0;i< postProcessingPluginVector.size(); i++) {
                postProcessingPluginVector.get(i).process();
            }
        }catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            input.close();
        }        
    } 

    private static void examineForMadeLive(String session, String desktop, String message) {
        Matcher mMadeLive = pMadeLive.matcher(message);


        if(mMadeLive != null && mMadeLive.find()) {

            DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
            if(desInfo == null) {
                desInfo = new DesktopInfo();
            }                


            String editionId = mMadeLive.group(1);

            if(liveEditionSessions.containsKey(editionId)) {
                //System.out.println("This is an upgrade to "+editionId);
                return;
            }

            desInfo.setMadeLive(true);
            desInfo.liveEditionSet.add(editionId);  
            desInfo.liveEditionRev = Integer.parseInt(mMadeLive.group(2));
            desktop2desktopInfo.put(desktop, desInfo);
            liveEditionSessions.put(editionId, desInfo);
        }
    }

    private static void examineForNewModel(String session, String desktop, String message) {
        Matcher mNewModelLoaded = pNewModelLoaded.matcher(message); 

        if(mNewModelLoaded.find()){

            DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
            if(desInfo == null) {
                desInfo = new DesktopInfo(); 
            }                

            desInfo.newEditionSet.add(mNewModelLoaded.group(1));                    
            desktop2desktopInfo.put(desktop, desInfo);

        }
    }

    private static void examineForAllEditionsSearch(String session, String desktop,
            String message) {
        Matcher mSearchEditions = pAllEditionsSearch.matcher(message);

        if(mSearchEditions != null && mSearchEditions.find()) {
            DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
            if(desInfo == null) {
                desInfo = new DesktopInfo();
            }                
            desInfo.searchTermSet.add(mSearchEditions.group(1));                                    
            desktop2desktopInfo.put(desktop, desInfo);
        }
    }

    private static void examineForWorldCatSearch(String session, String desktop,
            String message) {
        Matcher mworldcatSearch = pWorldcatRegistrySearch.matcher(message);


        if(mworldcatSearch != null && mworldcatSearch.find()) {

            DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
            desInfo.addToWorldCat(mworldcatSearch.group(2));                   
            desktop2desktopInfo.put(desktop, desInfo);                
        }
    }

    private static void examineForAutoDetectionDb(String session, String desktop,
            String message) {
        Matcher mAutoDetectionDb = pAutoDetectionDatabase.matcher(message);

        if(mAutoDetectionDb.find()){
            DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
            desInfo.dbAutoDetectionSet.add(mAutoDetectionDb.group(1));                   
            desktop2desktopInfo.put(desktop, desInfo);      
        }
    }

    private static void examineForAutoDetectionUrl(String session, String desktop,
            String message) {
        Matcher mAutoDetectionUrl = pAutoDetectionUrl.matcher(message);



        if(mAutoDetectionUrl.find()) {
            DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
            desInfo.urlAutoDetectionSet.add(mAutoDetectionUrl.group(3));
            desInfo.typeAutoDetectionSet.add(mAutoDetectionUrl.group(1));
            desktop2desktopInfo.put(desktop, desInfo);   
        }
    }

    private static void examineForReadHelp(String session, String desktop, String message) {
        Matcher mReadBlurb = pHelp.matcher(message);


        if(mReadBlurb != null && mReadBlurb.find()) {
            DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
            if(desInfo == null) {
                desInfo = new DesktopInfo();
            }                
            desInfo.addToHelpRead(mReadBlurb.group(1));                                    
            desktop2desktopInfo.put(desktop, desInfo);
        }
    }

    private static void examineForLoggedIn(String session, String desktop, String message) {
        String email;

        Matcher mLoggedIn = pLoggedIn.matcher(message);

        if(mLoggedIn != null && mLoggedIn.find()) {
            String[] email_arr = message.split(" ");
            if(email_arr.length > 0) {
                email = email_arr[0];

                if(excludedEmailSet.contains(email)) {
                    return;
                }

                Set<String> session_list = email2session.get(email);

                if(session_list == null)
                    session_list = new HashSet<String>();

                session_list.add(session);
                email2session.put(email,session_list);
            }
        }
    }

    private static void examineForDesktopCleaned(String session, String line,
            String datePart, String desktop)
    throws Exception {
        Matcher mDesktopCleaned2 = pDesktopCleaned2.matcher(line);

        if(mDesktopCleaned2.find()) {
            String desktopId = mDesktopCleaned2.group(1);
            desktopCleanedAction(session, datePart, desktopId, df);            
        }

        else { 
            Matcher mDesktopCleaned = pDesktopCleaned.matcher(line);

            if(mDesktopCleaned.find()) {

                desktopCleanedAction(session, datePart, desktop, df);
            }
        }
    }

    private static void examineForNewDesktop(String session, String datePart,
            String desktop, String message)
    throws Exception {


        Matcher mNewDesktop = pNewDesktop.matcher(message);

        if(mNewDesktop.find()) {                   

            DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
            desInfo.date = df.parse(datePart);   
            desktop2desktopInfo.put(desktop, desInfo);
        }
    }

    private static void getLiveCountHistogram() throws IOException {

        Writer output = new BufferedWriter(new FileWriter(new File(Config.liveCountPath)));

        HashSet<String> editionsSeenSoFarSet = new HashSet<String>();



        for (String key : email2session.keySet()) {
            int count = 0;

            Set<String> sess_set = email2session.get(key);

            for(String sess : sess_set) {

                Set<String> desktop_set = session2desktop.get(sess);

                for(String desktop : desktop_set) {

                    desktop = sess+";"+desktop;

                    DesktopInfo desInfo = desktop2desktopInfo.get(desktop);

                    for(String edition : desInfo.liveEditionSet) {
                        if (editionsSeenSoFarSet.contains(edition))
                            continue;
                        else {
                            count++;
                            editionsSeenSoFarSet.add(edition);
                        }
                    }          
                }
            }

            output.write(key+"\t"+count+"\n");
        }

        output.close();
    }

    private static void getMostCommonlyDetectedCatalogTypes()
    throws IOException, Exception {
        Writer outputSearch;
        outputSearch = new BufferedWriter(new FileWriter(new File(Config.autoDetectionTypeMonthwiseCountPath)));
        outputSearch.write("#Month\tYear\tNumber_of_times_AutoDetection_From_Type_was_used\n");
        Writer outputSearchTermCount = new BufferedWriter(new FileWriter(new File(Config.autoDetectionTypeCountPath)));
        getSearchCountPerMonth(SearchEnum.AutoDetectionType, outputSearch, outputSearchTermCount);
        outputSearch.close();
        outputSearchTermCount.close();
    }

    private static void getMonthWiseStatisticsAutodetectionDatabase()
    throws IOException, Exception {
        Writer outputSearch;
        outputSearch = new BufferedWriter(new FileWriter(new File(Config.autoDetectionDbMonthWiseCountPath)));
        outputSearch.write("#Month\tYear\tNumber_of_times_AutoDetection_From_DB_was_used\n");
        Writer outputSearchTermCount = new BufferedWriter(new FileWriter(new File(Config.autoDetectionDbTypeCount)));
        getSearchCountPerMonth(SearchEnum.AutoDetectionDb, outputSearch,outputSearchTermCount);
        outputSearchTermCount.close();
        outputSearch.close();
    }

    private static void getMonthWiseStatisticsAutoDetectionUrl()
    throws IOException, Exception {
        Writer outputSearch;
        outputSearch = new BufferedWriter(new FileWriter(new File(Config.autoDetectionUrlMonthwiseCountPath)));
        outputSearch.write("#Month\tYear\tNumber_of_times_AutoDetection_From_Url_was_used\n");
        Writer outputSearchTermCount = new BufferedWriter(new FileWriter(new File(Config.autoDetectionUrlTypeCount)));
        getSearchCountPerMonth(SearchEnum.AutoDetectionUrl, outputSearch, outputSearchTermCount);
        outputSearch.close();
        outputSearchTermCount.close();
    }

    private static void getMonthWiseStatisticsWorldCatRegistrySearch()
    throws IOException, Exception {
        Writer outputSearch;
        outputSearch = new BufferedWriter(new FileWriter(new File(Config.worldcatSearchCountMonthwisePath)));
        outputSearch.write("#Month\tYear\tNumber_of_times_WorldCatRegistrySearch_was_used\n");
        Writer outputSearchTermCount = new BufferedWriter(new FileWriter(new File(Config.worldCatSearchCount)));
        getSearchCountPerMonth(SearchEnum.WorldCatRegistry,outputSearch,outputSearchTermCount);
        outputSearch.close();
        outputSearchTermCount.close();
    }

    private static void getMonthWiseStatisticsOfAllEditionsSearch()
    throws IOException, Exception {
        Writer outputSearch;
        outputSearch = new BufferedWriter(new FileWriter(new File(Config.allEditionsSearchCountMonthwisePath)));
        outputSearch.write("#Month\tYear\tNumber_of_times_alleditionsListSearch_was_used\n");
        Writer outputSearchTermCount = new BufferedWriter(new FileWriter(Config.alleditionsSearchCount));
        getSearchCountPerMonth(SearchEnum.AllEditions, outputSearch,outputSearchTermCount);        
        outputSearch.close();
        outputSearchTermCount.close();
    }

    private static void getMonthWiseStatisticsOfOpenUrls()
    throws IOException, Exception {
        Writer outputSearch;
        outputSearch = new BufferedWriter(new FileWriter(new File(Config.openUrlMonthwiseCountPath)));
        outputSearch.write("#Month\tYear\tNumber_of_times_alleditionsListSearch_was_used\n");
        Writer outputSearchTermCount = new BufferedWriter(new FileWriter(Config.openUrlPopularSearchesPath));
        getSearchCountPerMonth(SearchEnum.OpenUrl, outputSearch,outputSearchTermCount);        
        outputSearch.close();
        outputSearchTermCount.close();
    }


    private static void getHelpCountHistogram()
    throws Exception {

        Writer outputHelp =  new BufferedWriter(new FileWriter(new File(Config.helpCountPath)));

        HashMap<String,Integer> helpCount = new  HashMap<String,Integer>();
        for (String key : email2session.keySet()) {

            Set<String> sess_set = email2session.get(key);

            for(String sess : sess_set) {

                Set<String> desktop_set = session2desktop.get(sess);


                for(String des : desktop_set) {

                    DesktopInfo desInfo = desktop2desktopInfo.get(des);

                    if(desInfo != null) {

                        for(String obj : desInfo.helpReadSet) {

                            int help_count=0;

                            if(helpCount.containsKey(obj.toString())) {
                                help_count = helpCount.get(obj);
                            }
                            helpCount.put(obj.toString(), ++help_count);
                        }
                    }
                }
            }
        }

        outputHelp.write("# Help_Text_Name    number_of_times_read\n");
        for(String key: helpCount.keySet()) {
            Matcher m = pcleanHelpNames.matcher(key);
            String cleanedKey="";
            if(m!=null&&m.find()) {
                cleanedKey = m.group(1);
            }
            if(helpCount.get(key) > 0)
                outputHelp.write(cleanedKey+"\t"+helpCount.get(key)+'\n');
        }

        outputHelp.close();
    }

    private static void desktopCleanedAction(String session, String datePart, String desktop,
            DateFormat df) throws ParseException {

        if(desktop2desktopInfo.containsKey(desktop) && desktop2desktopInfo.get(desktop) != null) {


            DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
            desInfo.isClosed = true;  
            desktop2desktopInfo.put(desktop,desInfo);
        }
    }

    private static void printDesktopDurations() throws Exception{

        HashSet<String> discardDesktops = new HashSet<String>();

        Writer output = new BufferedWriter(new FileWriter(new File(Config.sessionDurationPath)));
        output.write("#Desktop_Id\tTime_In_Milliseconds\n");

        for(String desktop : desktop2desktopInfo.keySet()) {

            String[] desArr = desktop.split(";");
            if(desArr.length != 2) {
                continue;
            }

            DesktopInfo desInfo  = desktop2desktopInfo.get(desktop);

            if(isDesktopInvalid(desInfo)) {         
                discardDesktops.add(desktop);
                continue;
            }

            if(desktop2desktopInfo.get(desktop).duration == 0) {
                System.out.println("This desktop has zero duration -->"+desktop);               
            }
            output.write(desktop+"\t"+desInfo.duration+"\n");
        }  

        for(String desktop : discardDesktops) {
            desktop2desktopInfo.remove(desktop);
        }

        output.close();
    }

    private static boolean isDesktopInvalid(DesktopInfo desInfo) {
        return desInfo.date.equals(epochDate) || desInfo.duration - desInfo.date.getTime() == 0;
    }



    private static void printTimesOfAllLiveEditions() throws Exception {

        int[] time2editionCount =  new int[100000];
        int[] time2cumulativeEditionCount = new int[time2editionCount.length];

        Writer output = new BufferedWriter(new FileWriter(new File(Config.editionDurationPath)));
        output.write("#Edition_Id\tTime_In_Minutes_Till_Live\n");

        HashMap<String,Integer> edition2timeToLive = new HashMap<String,Integer>();
        //System.out.println("number of live editions "+liveEditionSessions.size());

        for(String edition : liveEditionSessions.keySet()) {
            long timeToLive = 0;

            for(String desktop : desktop2desktopInfo.keySet()) {
                DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
                if(desInfo.newEditionSet.contains(edition)) {    
                    timeToLive += desInfo.duration;
                }    
            }

            int finalTimeToLive = (int)(timeToLive/60000);
            edition2timeToLive.put(edition,finalTimeToLive);

            if(finalTimeToLive > 100000)
                continue;

            time2editionCount[finalTimeToLive]++;
        }


        for (String edition : edition2timeToLive.keySet()) {
            long time = (edition2timeToLive.get(edition));
            output.write(edition+"\t"+time+"\n");
        }

        output.close();
        output = new BufferedWriter(new FileWriter(new File(Config.editionCumulativeTimesPath)));
        output.write("#Time in minutes\tNumber of editions made live in time less than that time\n");

        int k=1;

        while(k < time2editionCount.length) {
            time2cumulativeEditionCount[k] = time2cumulativeEditionCount[k-1]+time2editionCount[k];
            output.write(k+1+"\t"+time2cumulativeEditionCount[k]+"\n");
            k++;    
        }
        output.close();
    }

    private static void getSearchCountPerMonth(SearchEnum typeOfSearch, Writer outputSearchMonthCount, Writer outputSearchTermCount) throws Exception {

        //date2searchCount is a bit of a misnomer, since it is actually a map of the string "month/year" 
        //to the search count.

        HashMap<String, Integer> date2searchCount = new HashMap<String,Integer>();
        HashMap<String, Integer> term2searchCount = new HashMap<String,Integer>();


        populateDataStructures(typeOfSearch, date2searchCount,
                term2searchCount,desktop2desktopInfo.keySet());

        for(String key : date2searchCount.keySet()) {
            String[] month_year = key.split("/");

            outputSearchMonthCount.write(month_year[0]+"\t"+month_year[1]+"\t"+date2searchCount.get(key)+"\n");
        }

        outputSearchTermCount.write("#Term\tNumber_of_times_searched\n");

        for(String key : term2searchCount.keySet()) {
            outputSearchTermCount.write(key+"\t"+term2searchCount.get(key)+"\n");
        }
    }

    static Pattern pEmailCount = Pattern.compile("(.*)\\s+(\\d+)");

    private static void getAutoDetectionUsagePerUser() throws Exception {

        Writer outputSearch = new BufferedWriter(new FileWriter(new File(Config.autoDetectionPerUserCountPath)));
        outputSearch.write("#email\tNumber_of_times_autodetection_used\n");

        HashMap<String,Integer> email2usageCount = new HashMap<String, Integer>();

        for (String key : email2session.keySet()) {
            int count = 0;

            if(email2usageCount.containsKey(key)) {
                count = email2usageCount.get(key);
            }

            Set<String> sess_set = email2session.get(key);

            for(String sess : sess_set) {

                Set<String> desktop_set = session2desktop.get(sess);

                for(String desktop : desktop_set) {

                    desktop = sess+";"+desktop;
                    DesktopInfo desInfo = desktop2desktopInfo.get(desktop);
                    if(desInfo != null) { 
                        count += /*desInfo.dbAutoDetectionSet.size()+*/desInfo.urlAutoDetectionSet.size();
                    }                           
                }
            }
            email2usageCount.put(key, count);            
        }

        for(String key : email2usageCount.keySet()) {
            outputSearch.write(key+"\t"+email2usageCount.get(key)+"\n");
        }
        outputSearch.close();
    }

    private static void populateDataStructures (SearchEnum typeOfSearch,
            HashMap<String, Integer> month2searchCount,
            HashMap<String, Integer> term2searchCount,
            Set<String> desktopSet) {

        for(String sess : desktopSet) {
            DesktopInfo desInfo = desktop2desktopInfo.get(sess);

            if(desInfo != null) { 
                List<String> termSet = new ArrayList<String>();

                switch(typeOfSearch) {
                case AllEditions: 
                    termSet = desInfo.searchTermSet;
                    break;
                case WorldCatRegistry:
                    termSet = desInfo.worldCatSearchTermSet;
                    break;
                case AutoDetectionUrl:
                    termSet = desInfo.urlAutoDetectionSet;
                    break;
                case AutoDetectionDb:
                    termSet = desInfo.dbAutoDetectionSet;
                    break;
                case AutoDetectionType:
                    termSet = desInfo.typeAutoDetectionSet;
                    break;
                case OpenUrl:
                    termSet = desInfo.openUrl;
                    break;
                }

                for(String term : termSet) {
                    int termCount = 0;
                    if(term2searchCount.containsKey(term)) {
                        termCount = term2searchCount.get(term);
                    }
                    term2searchCount.put(term,++termCount);                       
                }

                int currentSessionCount = termSet.size(); 
                int count = 0;

                Calendar c = Calendar.getInstance();
                c.setTime(desInfo.date);

                //This deals with the desktops which are of the form in the logfiles ...:gcsu: Desktop cleaned up [Desktop gcsu]
                if(c.get(Calendar.YEAR) < 2007) {
                    continue;
                }

                String keyString = c.get(Calendar.MONTH)+"/"+c.get(Calendar.YEAR);
                if(month2searchCount.containsKey(keyString)) {
                    count = month2searchCount.get(keyString);
                }

                month2searchCount.put(keyString,currentSessionCount+count);
            }
        }
    }
}
