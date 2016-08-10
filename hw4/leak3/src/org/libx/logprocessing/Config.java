package org.libx.logprocessing;


public class Config {
    
    /**
     * The first 5 values have to be specified by the tester.
     */
    public static String path = System.getProperty("source.dir");
    public static String logFilePath = System.getProperty("log.file");
    public static String databaseDumpsPath = System.getProperty("dumps.dir");
    public static String editionsDirPath = System.getProperty("editions.dir");
    public static String dbRestoreCommand = System.getProperty("dbrestore.command");
    
    public static String complexityPath = path+"edition_complexity.txt";
    
    public static String openUrlMonthwiseCountPath = path+"openUrl_monthwise_count.txt";
    public static String openUrlPopularSearchesPath = path+"openUrl_popular_searches.txt";
    
    public static String publicEditionsCountPath = path+"public_edition_count.txt";
    
    public static String scratchVsClonedPath = path+"scratchVsCloned.txt";
    public static String clonedFromDetailsPath = path+"clonedFromDetails.txt";
    
    public static String dbVsUrlAutoDetectionPath = path+"dbVsUrlAutoDetection.txt";
    public static String liveCountPath = path+"live_count.txt";
    
    public static String autoDetectionTypeCountPath = path+"dbVsUrlAutoDetection_type_count.txt";
    public static String autoDetectionTypeMonthwiseCountPath = path+"autodetection_type_count_monthwise.txt";
    public static String autoDetectionDbMonthWiseCountPath = path+"autodetection_db_count_monthwise.txt";
    public static String autoDetectionUrlMonthwiseCountPath = path+"autodetection_url_count_monthwise.txt";
    public static String autoDetectionPerUserCountPath = path+"autodetection_per_user_count.txt";
    public static String autoDetectionDbTypeCount = path+"autodetection_db_count.txt";
    public static String autoDetectionUrlTypeCount = path+"autodetection_url_count.txt";
    
    public static String worldcatSearchCountMonthwisePath = path+"worldcat_search_count_monthwise.txt";
    public static String worldCatSearchCount = path+"worldcat_search_count.txt";
    
    public static String allEditionsSearchCountMonthwisePath = path+"alleditions_search_count_monthwise.txt";
    public static String alleditionsSearchCount = path+"alleditions_search_count.txt";
    
    public static String helpCountPath = path+"help_count.txt";
    
    public static String sessionDurationPath = path+"session_duration.txt";
    public static String editionDurationPath = path+"edition_in_session_duration.txt";
    public static String editionCumulativeTimesPath = path+"edition_cdf.txt";
    
  
    
  
    
    
    
    
    
    
}