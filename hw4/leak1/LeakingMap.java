// Run with java -XX:+HeapDumpOnOutOfMemoryError -Xmx16m LeakingMap

import java.util.*;

public class LeakingMap 
{
    static HashMap<Integer, String> theMap = new HashMap<Integer, String>();

    public static void main(String []av) {
        for (int i = 0; ; i++) {
            theMap.put(i, Integer.toString(i));
        }
    }
}
