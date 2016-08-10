package org.libx.editionbuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;

/**
 * Edition Builder
 * This class provides the necessary methods to invoke the build script.
 *
 * @author Godmar Back
 */
public class Builder {
    /**
     * A threaded stream reader class.
     *
     * A process may write to its stdout and stderr in any order.
     * Since these streams are backed by pipes by the underlying system,
     * and since pipes are bounded buffers, we must ensure that we
     * read from those streams whenever data is ready. Otherwise, we
     * may block the process we run, leading to deadlock. The only way to 
     * do that reliably is by spawning a separate thread for each stream.
     */
    static class ThreadedStreamReader extends Thread {
        StringBuffer streamContent = new StringBuffer();
        BufferedReader streamReader;
        
        /**
         * Create a new stream reader that reads concurrently from the given inputstream.
         * @param is
         */
        ThreadedStreamReader(InputStream is) {
            streamReader = new BufferedReader(new InputStreamReader(is));
        }
    
        /**
         * Return content read so far.
         */
        public String toString() {
            return streamContent.toString();
        }

        public void run() {
            try {
                while (true) {
                    String line = streamReader.readLine();
                    if (line == null)
                        return;
                    streamContent.append(line);
                    streamContent.append("\n");
                }
            } catch (IOException ioe) {
                streamContent.append(ioe.getMessage());
            }
        }
    }

    /**
     * Run Scripts. Return true if successful.
     * Return false otherwise.  In either case, update MainWindowController's status box.
     */
    public static boolean runScript(String info, String script, String workingdir) {
        ThreadedStreamReader stderr = null, stdout = null;

        try {
            Process p = Runtime.getRuntime().exec(
                script,
                null,   // null inherits parent env
                new File(workingdir));

            stderr = new ThreadedStreamReader(p.getErrorStream());
            stdout = new ThreadedStreamReader(p.getInputStream());
            stderr.start();
            stdout.start();
            stderr.join();
            stdout.join();

            int status = p.waitFor();
            
            /*
            System.out.println(info + "\nstdout: " + stdout.toString());
            System.out.println("stderr: " + stderr.toString());
            */
            
            if (status != 0)
                throw new Exception(info + " failed, exit status= " + status);
            
            MainWindowController.showStatus(StatusCode.OK, info + " successful.", stdout.toString());
            return true;
        } catch (Exception e) {
            if (stderr != null)
                MainWindowController.showStatus(StatusCode.ERROR, e.getMessage(), stderr.toString() + "\n(STDOUT was:)\n" + stdout.toString());
            else
                MainWindowController.showException(e);
        }
        return false;
    }

    public static boolean build(String mpath, boolean ieFullInstall) {
     //   String mpath = Model.getCurrentModelFSPath();
        String commandLineSwitch = "";
        if(ieFullInstall)
            commandLineSwitch = " -ie_full_installer";
        return runScript("build", Config.buildcommand+commandLineSwitch+" "+ mpath, Config.buildworkingdir);
    }

    static boolean makeNewRevision(String mpath) {
        return runScript("making new revision...", 
                  Config.makenewrevisioncommand + " " + mpath, Config.buildworkingdir);
    }

    static boolean copyEdition(String srcId, String dstId, int dstRevision) {
        String dstdir = Config.editionpath + "/" + Model.id2RelPath(dstId) + "." + dstRevision;
        return runScript("copying edition " + srcId + "...", 
                  Config.copyeditioncommand + " " + 
                  Config.editionpath + "/" + Model.id2RelPath(srcId) + " " + dstdir,
                  Config.buildworkingdir);
    }
}
