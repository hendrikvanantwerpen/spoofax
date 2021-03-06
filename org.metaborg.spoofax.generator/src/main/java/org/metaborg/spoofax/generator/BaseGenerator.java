package org.metaborg.spoofax.generator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.io.IOUtils;
import org.metaborg.spoofax.generator.util.MustacheWriter;

public abstract class BaseGenerator {
    
    private final File root;
    private final String sdfMainModule;
    protected final MustacheWriter writer;

    public BaseGenerator(File root, String sdfMainModule) {
        this.root = root;
        this.sdfMainModule = sdfMainModule;
        this.writer = new MustacheWriter(root, this, getClass());
    }

    public File getRoot() {
        return root;
    }

    public String sdfMainModule() {
        return sdfMainModule;
    }

    public String transModuleName() {
        return sdfMainModule.toLowerCase();
    }

    protected void unpack(String name) throws IOException {
        String dst = new File(name).getParent();
        unpack(name, dst);
    }

    protected void unpack(String srcName, String dstDir) throws IOException {
        File tmp = File.createTempFile("generator-unpack", "tmp");
        tmp.deleteOnExit();
        try (InputStream is = getClass().getResourceAsStream(srcName)) {
            try (OutputStream os = new FileOutputStream(tmp)) {
                IOUtils.copy(is,os);
            }
        }
        File dst = new File(root, dstDir);
        dst.mkdirs();
        ZipFile z;
        try {
            z = new ZipFile(tmp.getPath());
            z.extractAll(dst.getPath());
        } catch (ZipException ex) {
            throw new IOException("Failed te unpack archive "+srcName, ex);
        }
    }

}
