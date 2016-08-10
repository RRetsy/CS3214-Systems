package org.libx.editionbuilder;

import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.util.DesktopCleanup;

public class LogDesktopCleanup implements DesktopCleanup {  
    public void cleanup(Desktop desktop) {
        Utils.printLog("Desktop cleaned up %s", desktop);
    }
}
