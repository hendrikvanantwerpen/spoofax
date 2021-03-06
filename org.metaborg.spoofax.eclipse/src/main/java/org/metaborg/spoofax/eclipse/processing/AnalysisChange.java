package org.metaborg.spoofax.eclipse.processing;

import org.apache.commons.vfs2.FileObject;
import org.metaborg.spoofax.core.analysis.AnalysisException;
import org.metaborg.spoofax.core.analysis.AnalysisFileResult;
import org.metaborg.spoofax.core.analysis.AnalysisResult;
import org.metaborg.spoofax.eclipse.util.Nullable;
import org.spoofax.interpreter.terms.IStrategoTerm;

public class AnalysisChange {
    public final UpdateKind kind;
    public final FileObject resource;
    @Nullable public final AnalysisFileResult<IStrategoTerm, IStrategoTerm> result;
    @Nullable public final AnalysisResult<IStrategoTerm, IStrategoTerm> parentResult;
    @Nullable public final AnalysisException exception;


    /**
     * Creates an analysis change that represents an update to the analysis result.
     * 
     * @param resource
     *            Changed resource.
     * @param result
     *            Updated analysis result.
     * @param parentResult
     *            Parent of the updated analysis result.
     * @return Analysis change.
     */
    public static AnalysisChange update(FileObject resource, AnalysisFileResult<IStrategoTerm, IStrategoTerm> result,
        AnalysisResult<IStrategoTerm, IStrategoTerm> parentResult) {
        return new AnalysisChange(UpdateKind.Update, result.source, result, parentResult, null);
    }

    /**
     * Creates an analysis change that represents an invalidation of given resource.
     * 
     * @param resource
     *            Resource to invalidate.
     * @return Analysis change.
     */
    public static AnalysisChange invalidate(FileObject resource) {
        return new AnalysisChange(UpdateKind.Invalidate, resource, null, null, null);
    }

    /**
     * Creates an analysis change that represents an error that occurred while updating an analysis result.
     * 
     * @param resource
     *            Changed resource.
     * @param exception
     *            Error that occurred.
     * @return Analysis change.
     */
    public static AnalysisChange error(FileObject resource, AnalysisException exception) {
        return new AnalysisChange(UpdateKind.Error, resource, null, null, exception);
    }

    /**
     * Creates an analysis change that represents removal of an analysis result.
     * 
     * @param resource
     *            Resource that was removed.
     * @return Analysis change.
     */
    public static AnalysisChange remove(FileObject resource) {
        return new AnalysisChange(UpdateKind.Remove, resource, null, null, null);
    }


    /*
     * Use static methods to create instances.
     */
    protected AnalysisChange(UpdateKind kind, FileObject resource,
        AnalysisFileResult<IStrategoTerm, IStrategoTerm> result,
        AnalysisResult<IStrategoTerm, IStrategoTerm> parentResult, AnalysisException exception) {
        this.kind = kind;
        this.resource = resource;
        this.result = result;
        this.parentResult = parentResult;
        this.exception = exception;
    }
}
