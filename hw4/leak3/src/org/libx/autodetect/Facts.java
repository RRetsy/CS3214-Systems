package org.libx.autodetect;

import java.util.HashMap;

public class Facts {
    public interface ofInterest { }
    private static class HashMapAdapter extends HashMap<String, String> implements ofInterest {
        HashMapAdapter() {
        }

        HashMapAdapter(String ... kv) {
            for (int i = 0; i < kv.length; i += 2)
                put(kv[i], kv[i+1]);
        }
        public String toString() {
            return getClass().getName() + ": " + super.toString();
        }
    }

    public static class Millenium extends HashMapAdapter {
    }

    public static class OpenSearchDescription extends HashMapAdapter {
    }

    public static class Aleph extends HashMapAdapter {
    }

    public static class Horizon extends HashMapAdapter {
    }

    public static class Web2 extends HashMapAdapter {
    }

    public static class Sirsi extends HashMapAdapter {
    }
}
