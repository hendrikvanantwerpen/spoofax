package org.strategoxt.imp.metatooling.stratego;

import static org.spoofax.interpreter.terms.IStrategoTerm.LIST;
import static org.spoofax.interpreter.terms.IStrategoTerm.STRING;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spoofax.interpreter.library.IOAgent;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoString;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.strategoxt.lang.Context;
import org.strategoxt.lang.StrategoException;
import org.strategoxt.lang.compat.NativeCallHelper;
import org.strategoxt.lang.compat.SSL_EXT_call;
import org.strategoxt.stratego_xtc.xtc_command_1_0;

/**
 * Overrides the xtc-command strategy to use sdf2table from the nativebundle plugin.
 */
public class NativeBundleCommand extends xtc_command_1_0 {
    private static final Logger logger = LoggerFactory.getLogger(NativeBundleCommand.class);
    private static final String NATIVE_PATH = "native/";
    private static final boolean ENABLED = true;

    private final xtc_command_1_0 proceed = xtc_command_1_0.instance;
    private final String[] windowsEnvironment = createWindowsEnvironment();
    private String binaryPath;
    private String binaryExtension;
    private boolean initialized;

    
    public void init() throws IOException {
        if(initialized)
            return;
        binaryPath = getBinaryPath();
        binaryExtension = getBinaryExtension();

        if(isLinuxOS() || isMacOS()) {
            IOAgent agent = new IOAgent();
            boolean success = makeExecutable(agent, "sdf2table") && makeExecutable(agent, "implodePT");
            if(!success)
                logger.error("chmod of native tool bundle executables failed");
        }
        logger.debug("Initialized the native tool bundle in " + getBinaryPath());
        initialized = true;
    }

    public String getBinaryPath() throws IOException, UnsupportedOperationException {
        if(System.getenv("SPOOFAX_NATIVE_PATH") != null)
            return System.getenv("SPOOFAX_NATIVE_PATH");

        String subdir;
        if(isLinuxOS()) {
            subdir = "linux";
        } else if(isWindowsOS()) {
            subdir = "cygwin";
        } else if(isMacOS()) {
            subdir = "macosx";
        } else {
            throw new UnsupportedOperationException("Platform is not supported"); // TODO: print platform
        }

        File result = null;
        final Bundle nativeBundle = Platform.getBundle("org.strategoxt.imp.nativebundle");
        if(nativeBundle != null) {
            final IPath path = new Path(NATIVE_PATH + subdir);
            final URL url = FileLocator.find(nativeBundle, path, null);
            result = new File(FileLocator.toFileURL(url).getPath());
        } else {
            // Fallback in the case that OSGI is not initialized
            // Query classpath
            String classpath = System.getProperty("java.class.path");
            java.util.List<String> classpathEntries =
                new java.util.ArrayList<>(Arrays.asList(classpath.split(File.pathSeparator)));
            for(String entry : classpathEntries) {
                if(entry.contains("org.strategoxt.imp.nativebundle")) {
                    result = new File(entry + "/" + NATIVE_PATH + subdir);
                    break;
                }
            }
            if(result == null) {
                throw new IOException("Unable to find nativebundle");
            }
        }

        if(!result.exists())
            throw new FileNotFoundException(result.getAbsolutePath());
        return result.getAbsolutePath() + File.separator;
    }

    public String getBinaryExtension() {
        return isWindowsOS() ? ".exe" : "";
    }

    public static NativeBundleCommand getInstance() {
        if(!(instance instanceof NativeBundleCommand))
            instance = new NativeBundleCommand();

        return (NativeBundleCommand) instance;
    }

    @Override public IStrategoTerm invoke(Context context, IStrategoTerm args,
        org.strategoxt.lang.Strategy commandStrategy) {

        try {
            init();
        } catch(IOException e) {
            logger.error(
                "Could not determine the binary path for the native tool bundle (" + System.getProperty("os.name")
                    + "/" + System.getProperty("os.arch") + ")", e);
            return proceed.invoke(context, args, commandStrategy);
        } catch(RuntimeException e) {
            logger.error(
                "Failed to initialize the native tool bundle (" + System.getProperty("os.name") + "/"
                    + System.getProperty("os.arch") + ")", e);
            return proceed.invoke(context, args, commandStrategy);
        }

        IStrategoTerm commandTerm = commandStrategy.invoke(context, args);
        if(!ENABLED || commandTerm.getTermType() != STRING)
            return proceed.invoke(context, args, commandStrategy);

        String command = ((IStrategoString) commandTerm).stringValue();
        if(!new File(binaryPath + command + binaryExtension).exists()) {
            if(command.equals("sdf2table") || command.equals("implodePT")) {
                throw new StrategoException("Could not find the native tool bundle command " + command + " in "
                    + binaryPath + command + binaryExtension);
            }
            return proceed.invoke(context, args, commandStrategy);
        }

        if(args.getTermType() != LIST)
            return null;

        if(isLinuxOS() || isMacOS()) {
            if(!makeExecutable(context.getIOAgent(), command)) {
                logger.error("chmod of " + binaryPath + command + binaryExtension + " failed");
                return proceed.invoke(context, args, commandStrategy); // (already logged)
            }
        }

        return invoke(context, command, ((IStrategoList) args).getAllSubterms()) ? args : null;
    }

    public boolean invoke(Context context, String command, IStrategoTerm[] argList) {
        String[] commandArgs = SSL_EXT_call.toCommandArgs(binaryPath + command, argList);
        // Disabled this check since Windows x64 might identify differently?
        // String[] environment = isWindowsOS()
        // ? createWindowsEnvironment()
        // : null;
        String[] environment = windowsEnvironment;
        IOAgent io = context.getIOAgent();

        try {
            if(commandArgs == null)
                return false;
            Writer out = io.getWriter(IOAgent.CONST_STDOUT);
            Writer err = io.getWriter(IOAgent.CONST_STDERR);

            err.write("Invoking native tool " + binaryPath + command + binaryExtension + " " + Arrays.toString(argList)
                + "\n");
            int result = new NativeCallHelper().call(commandArgs, environment, new File(io.getWorkingDir()), out, err);
            if(result != 0) {
                logger.error("Native tool " + command + " exited with error code " + result + "\nCommand:\n  "
                    + Arrays.toString(commandArgs) + "\nEnvironment:\n " + Arrays.toString(environment)
                    + "\nWorking dir:\n  " + io.getWorkingDir());
            }
            return result == 0;
        } catch(IOException e) {
            throw new StrategoException("Could not call native tool " + command + "\nCommand:\n  "
                + Arrays.toString(commandArgs) + "\nEnvironment:\n " + Arrays.toString(environment)
                + "\nWorking dir:\n  " + io.getWorkingDir(), e);
        } catch(InterruptedException e) {
            throw new StrategoException("Could not call " + command, e);
        } catch(RuntimeException e) {
            throw new StrategoException("Could not call native tool " + command + "\nCommand:\n  "
                + Arrays.toString(commandArgs) + "\nEnvironment:\n " + Arrays.toString(environment)
                + "\nWorking dir:\n  " + io.getWorkingDir(), e);
        }
    }

    private static String[] createWindowsEnvironment() {
        Map<String, String> envp = new HashMap<String, String>(System.getenv());
        envp.put("CYGWIN", "nodosfilewarning");
        String[] result = new String[envp.size()];
        int i = 0;
        for(Entry<String, String> entry : envp.entrySet()) {
            result[i++] = entry.getKey() + "=" + entry.getValue();
        }
        return result;
    }

    private boolean makeExecutable(IOAgent io, String command) {
        try {
            Writer out = io.getWriter(IOAgent.CONST_STDOUT);
            Writer err = io.getWriter(IOAgent.CONST_STDERR);
            command = binaryPath + command + binaryExtension;
            // /bin/sh should exist even on NixOS
            String[] commandArgs = { "/bin/sh", "-c", "chmod +x \"" + command + "\"" };
            int result = new NativeCallHelper().call(commandArgs, new File(binaryPath), out, err);
            return result == 0;
        } catch(InterruptedException e) {
            logger.error("chmod failed: /bin/sh -c \"chmod +x " + command + "\"", e);
            return false;
        } catch(IOException e) {
            logger.error("chmod failed: /bin/sh -c \"chmod +x " + command + "\"", e);
            return false;
        }
    }

    private boolean isLinuxOS() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("nix") || os.contains("nux");
    }

    private boolean isWindowsOS() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private boolean isMacOS() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }
}
