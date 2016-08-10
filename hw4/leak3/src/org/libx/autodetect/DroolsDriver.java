package org.libx.autodetect;

import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.drools.FactHandle;
import org.drools.ObjectFilter;
import org.drools.RuleBase;

import org.drools.RuleBaseConfiguration.AssertBehaviour;

import org.drools.RuleBaseConfiguration;
import org.drools.RuleBaseFactory;
import org.drools.StatefulSession;

import org.drools.compiler.PackageBuilder;

import org.drools.rule.Package;

public class DroolsDriver {
    public class Host {
        protected final RuleBase ruleBase;
        {
            RuleBaseConfiguration conf = new RuleBaseConfiguration();
            conf.setAssertBehaviour( AssertBehaviour.EQUALITY );
            conf.setRemoveIdentities( true );
            conf.setShadowProxy ( false );
            ruleBase = RuleBaseFactory.newRuleBase( conf );
        }

        private StatefulSession session;

        /**
         * Add a fact to the host's working memory
         */
        public void addFact(Object fact) {
            session.insert(fact);
        }

        /**
         * Check for the presence of a fact
         */
        public boolean hasFact(Object fact) {
            return session.getFactHandle(fact) != null;
        }

        public void startNewSession() {
            if (session != null)
                session.dispose();
            session = ruleBase.newStatefulSession();
        }

        public void endSession() {
            session.dispose();
        }

        public void fireAllRules() {
            session.fireAllRules();
        }

        /*
        public void removeRetractableFacts() {
            removeFacts(new ObjectFilter() {
                public boolean accept(Object obj) {
                    return obj instanceof Facts.RetractAfterCompilation;
                }
            });
        }
        */

        public void removeFacts(ObjectFilter filter) {
            Iterator it = session.iterateFactHandles(filter);
            ArrayList<FactHandle> retractableFacts = new ArrayList<FactHandle>();
            while (it.hasNext())
                retractableFacts.add((FactHandle)it.next());

            for (FactHandle fact : retractableFacts) {
                session.retract(fact);
            }
        }

        public void dumpWorkingMemory() {
            dumpWorkingMemory(Object.class);
        }

        public void dumpWorkingMemory(Class type) {
            System.out.println("---------------------------------");
            System.out.println("Current state of working memory, only showing facts of type: " + type);
            Iterator it = session.iterateObjects();
            while (it.hasNext()) {
                Object o = it.next();
                if (type.isInstance(o))
                    System.out.println(o);
            }
            System.out.println("---------------------------------");
        }

        /**
         * Add a drool package to the host's rulebase.
         */
        public void addPackage(Package pkg) throws Exception {
            ruleBase.addPackage( pkg );
        }

        void readRuleFromFile(String ruleFile) throws Exception {
            final Reader source = new FileReader( ruleFile );
            readRule(source);
        }

        void readRuleFromResource(String ruleResource) throws Exception {
            final Reader source = new InputStreamReader(getClass().getResourceAsStream(ruleResource));
            readRule(source);
        }

        void readRule(Reader source) throws Exception {
            final PackageBuilder builder = new PackageBuilder();
            builder.addPackageFromDrl( source );
            final Package pkg = builder.getPackage();
            addPackage(pkg);
        }
    }
    Host host = new Host();

    public DroolsDriver(List<String> args) throws Exception {
        System.out.println("Using MVEL " + org.mvel.MVEL.VERSION + "." + org.mvel.MVEL.VERSION_SUB);
        String ruleFile;
        while ((ruleFile = findAndRemoveParm("-rules", args)) != null) {
            host.readRuleFromFile(ruleFile);
        }
    }

    public static void main(String []av) throws Exception {
        List<String> args = new ArrayList<String>(Arrays.asList(av));
        DroolsDriver driver = new DroolsDriver(args);
        driver.host.startNewSession();
        driver.host.fireAllRules();
    }

    /**
     * Find an element such as "-rules" and remove it and the following
     * element.  Used to remove items from the command line.
     */
    private String findAndRemoveParm(String what, List<String> alist) {
        int p = alist.indexOf(what);
        if (p == -1)
            return null;
        alist.remove(p);
        return alist.remove(p);
    }
}
