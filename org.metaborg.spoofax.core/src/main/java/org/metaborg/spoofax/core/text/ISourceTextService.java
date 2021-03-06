package org.metaborg.spoofax.core.text;

import java.io.IOException;

import org.apache.commons.vfs2.FileObject;

/**
 * Interface for retrieving text of resources.
 */
public interface ISourceTextService {
    /**
     * Retrieves the text for given resource.
     * 
     * @param resource
     *            Resource to retrieve text for.
     * @return Text for given resource.
     */
    public abstract String text(FileObject resource) throws IOException;
}
