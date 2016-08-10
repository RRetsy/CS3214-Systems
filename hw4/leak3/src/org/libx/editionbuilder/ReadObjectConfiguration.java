package org.libx.editionbuilder;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Read in configuration files that describe a set of objects.
 * They are listed line by line, classname first, then constructor arguments.
 * 
 * @author gback
 *
 */
public class ReadObjectConfiguration {
    /**
     * Read a single typed token and append it to 'tokens'
     */
    private static Pattern quotedString = Pattern.compile("\\G\\s*\\\"[^\"]*\\\"");
    private void readOneTypedToken(ArrayList<Object> tokens, Scanner scanner, Class type) {
        if (type == int.class) {
            tokens.add(scanner.nextInt());
        } else if (type == long.class) {
            tokens.add(scanner.nextLong());
        } else if (type == double.class) {
            tokens.add(scanner.nextDouble());
        } else if (type == boolean.class) {
            tokens.add(scanner.nextBoolean());
        } else if (type == String.class) {
            String qstr = scanner.findInLine(quotedString);
            if (qstr != null) {
                qstr = qstr.trim();
                tokens.add(qstr.substring(1, qstr.length()-1));
            } else                    
                tokens.add(scanner.next());
        } else if (type.isArray()) {
            if (!scanner.next().equals("["))
                throw new Error("expected [ for array argument");
            ArrayList<Object> elements = new ArrayList<Object>();
            Class eltype = type.getComponentType();
            while (!scanner.hasNext("]"))
                readOneTypedToken(elements, scanner, eltype);
            scanner.next();
            Object arr = Array.newInstance(eltype, elements.size());
            int i = 0;
            for (Object e : elements)
                Array.set(arr, i++, e);
            tokens.add(arr);
        } else {
            throw new Error("Can't handle type '" + type + "'");
        }
    }

    /**
     * Read a single object line: Type plus arguments
     * @param scanner
     * @return
     */
    Object readObjectLine(Scanner scanner) throws Exception {
        String className = scanner.next();
        Class clazz = Class.forName(className);
        if (clazz.getDeclaredConstructors().length > 1)
            throw new Error(clazz + " must not have more than 1 constructor");
        
        Constructor constructor = clazz.getDeclaredConstructors()[0];
        ArrayList<Object> argtypes = new ArrayList<Object>();
        for (Class parmtype : constructor.getParameterTypes()) {
            readOneTypedToken(argtypes, scanner, parmtype);
        }
        return constructor.newInstance(argtypes.toArray());
    }
    
    /**
     * Read a list of objects.
     * 
     * @param scanner
     * @return
     * @throws Exception
     */
    ArrayList<Object> read(Scanner scanner) throws Exception {
        ArrayList<Object> objects = new ArrayList<Object>();
        Pattern comment = Pattern.compile("^#.*", Pattern.MULTILINE);
        while (scanner.hasNext()) {
            if (scanner.hasNext(comment)) {
              // Ignore comment lines starting with #:
              scanner.nextLine();
              continue;
            }
            objects.add(readObjectLine(scanner));
        }
        return objects;
    }
    
    /**
     * Internal test.
     * 
     * @param av
     */
    public static void main(String av[]) throws Exception {
        Scanner scanner = new Scanner(
                "org.libx.editionbuilder.ReadObjectConfiguration$Test 1 2 hello true 4.0 [ 3 4 2 ] [ [ \"a\" \"b\" \"c\" ] [ \"A\" \"B\" ] ]\n" +
                "org.libx.editionbuilder.ReadObjectConfiguration$Test 0 -2 \"string with spaces\" false 4.0 [ 2 ] [ [ ] [ \"C\" \"B\" ] ]\n"
        );
        ReadObjectConfiguration conf = new ReadObjectConfiguration();
        ArrayList<Object> r = conf.read(scanner);
        System.out.println(r);
    }
    
    /**
     * Test class. Sole constructor must match string constants in main()
     */
    public static class Test {
        private HashMap<String, Object> map = new HashMap<String, Object>();
        Test(int a, int b, String c, boolean d, double e, int []f, String [][]g) {
            map.put("a", a);
            map.put("b", b);
            map.put("c", c);
            map.put("d", d);
            map.put("e", e);
            map.put("f", Arrays.toString(f));
            map.put("g", Arrays.deepToString(g));
        }
        public String toString() {
            return map.toString();
        }
    }
}
