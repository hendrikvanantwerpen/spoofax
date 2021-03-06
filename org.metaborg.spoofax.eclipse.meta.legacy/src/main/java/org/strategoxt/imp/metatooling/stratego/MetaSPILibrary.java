package org.strategoxt.imp.metatooling.stratego;

import java.io.IOException;

import org.eclipse.core.runtime.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.strategoxt.stratego_xtc.xtc_command_1_0;

public class MetaSPILibrary {
    private static final Logger logger = LoggerFactory.getLogger(MetaSPILibrary.class);


    public static void init() {
        NativeBundleCommand nativeBundle = new NativeBundleCommand();
        xtc_command_1_0.instance = nativeBundle;

        try {
            nativeBundle.init();
        } catch(IOException e) {
            logger.error("Could not determine the binary path for the native tool bundle (" + Platform.getOS() + "/"
                + Platform.getOSArch() + ")", e);
        } catch(RuntimeException e) {
            logger.error(
                "Failed to initialize the native tool bundle (" + Platform.getOS() + "/" + Platform.getOSArch() + ")",
                e);
        }
    }
}
