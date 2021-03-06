package org.metaborg.spoofax.eclipse.editor;

import org.apache.commons.vfs2.FileObject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.metaborg.spoofax.core.SpoofaxException;
import org.metaborg.spoofax.core.analysis.AnalysisException;
import org.metaborg.spoofax.core.analysis.AnalysisFileResult;
import org.metaborg.spoofax.core.analysis.AnalysisResult;
import org.metaborg.spoofax.core.analysis.IAnalysisService;
import org.metaborg.spoofax.core.context.IContext;
import org.metaborg.spoofax.core.context.IContextService;
import org.metaborg.spoofax.core.language.ILanguage;
import org.metaborg.spoofax.core.language.ILanguageIdentifierService;
import org.metaborg.spoofax.core.language.dialect.IDialectService;
import org.metaborg.spoofax.core.messages.IMessage;
import org.metaborg.spoofax.core.messages.MessageFactory;
import org.metaborg.spoofax.core.messages.MessageType;
import org.metaborg.spoofax.core.style.CategorizerValidator;
import org.metaborg.spoofax.core.style.ICategorizerService;
import org.metaborg.spoofax.core.style.IRegionCategory;
import org.metaborg.spoofax.core.style.IRegionStyle;
import org.metaborg.spoofax.core.style.IStylerService;
import org.metaborg.spoofax.core.syntax.ISyntaxService;
import org.metaborg.spoofax.core.syntax.ParseException;
import org.metaborg.spoofax.core.syntax.ParseResult;
import org.metaborg.spoofax.eclipse.job.ThreadKillerJob;
import org.metaborg.spoofax.eclipse.processing.AnalysisResultProcessor;
import org.metaborg.spoofax.eclipse.processing.ParseResultProcessor;
import org.metaborg.spoofax.eclipse.util.MarkerUtils;
import org.metaborg.spoofax.eclipse.util.StatusUtils;
import org.metaborg.spoofax.eclipse.util.StyleUtils;
import org.metaborg.util.iterators.Iterables2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spoofax.interpreter.terms.IStrategoTerm;

public class EditorUpdateJob extends Job {
    private static final Logger logger = LoggerFactory.getLogger(EditorUpdateJob.class);
    private static final long killTimeMillis = 3000;

    private final ILanguageIdentifierService languageIdentifierService;
    private final IDialectService dialectService;
    private final IContextService contextService;
    private final ISyntaxService<IStrategoTerm> syntaxService;
    private final IAnalysisService<IStrategoTerm, IStrategoTerm> analyzer;
    private final ICategorizerService<IStrategoTerm, IStrategoTerm> categorizer;
    private final IStylerService<IStrategoTerm, IStrategoTerm> styler;

    private final ParseResultProcessor parseResultProcessor;
    private final AnalysisResultProcessor analysisResultProcessor;

    private final IEditorInput input;
    private final IResource eclipseResource;
    private final FileObject resource;
    private final ISourceViewer sourceViewer;
    private final String text;
    private final PresentationMerger presentationMerger;
    private final boolean isNewEditor;

    private ThreadKillerJob threadKiller;


    public EditorUpdateJob(ILanguageIdentifierService languageIdentifierService, IDialectService dialectService,
        IContextService contextService, ISyntaxService<IStrategoTerm> syntaxService,
        IAnalysisService<IStrategoTerm, IStrategoTerm> analyzer,
        ICategorizerService<IStrategoTerm, IStrategoTerm> categorizer,
        IStylerService<IStrategoTerm, IStrategoTerm> styler, ParseResultProcessor parseResultProcessor,
        AnalysisResultProcessor analysisResultProcessor, IEditorInput input, IResource eclipseResource,
        FileObject resource, ISourceViewer sourceViewer, String text, PresentationMerger presentationMerger,
        boolean isNewEditor) {
        super("Updating Spoofax editor");
        setPriority(Job.SHORT);

        this.languageIdentifierService = languageIdentifierService;
        this.dialectService = dialectService;
        this.contextService = contextService;
        this.syntaxService = syntaxService;
        this.analyzer = analyzer;
        this.categorizer = categorizer;
        this.styler = styler;

        this.parseResultProcessor = parseResultProcessor;
        this.analysisResultProcessor = analysisResultProcessor;

        this.input = input;
        this.eclipseResource = eclipseResource;
        this.resource = resource;
        this.sourceViewer = sourceViewer;
        this.text = text;
        this.presentationMerger = presentationMerger;
        this.isNewEditor = isNewEditor;
    }


    @Override public boolean belongsTo(Object family) {
        return input.equals(family);
    }

    @Override protected IStatus run(final IProgressMonitor monitor) {
        logger.debug("Running editor update job for {}", resource);

        final IWorkspace workspace = ResourcesPlugin.getWorkspace();

        try {
            final IStatus status = update(workspace, monitor);
            if(threadKiller != null) {
                threadKiller.cancel();
            }
            return status;
        } catch(SpoofaxException | CoreException e) {
            if(monitor.isCanceled())
                return StatusUtils.cancel();

            try {
                final IWorkspaceRunnable parseMarkerUpdater = new IWorkspaceRunnable() {
                    @Override public void run(IProgressMonitor workspaceMonitor) throws CoreException {
                        if(workspaceMonitor.isCanceled())
                            return;
                        MarkerUtils.clearAll(eclipseResource);
                        MarkerUtils.createMarker(eclipseResource, MessageFactory.newErrorAtTop(resource,
                            "Failed to update editor", MessageType.INTERNAL, null));
                    }
                };
                workspace.run(parseMarkerUpdater, eclipseResource, IWorkspace.AVOID_UPDATE, monitor);
            } catch(CoreException e2) {
                final String message = "Failed to show internal error marker";
                logger.error(message, e2);
                return StatusUtils.silentError(message, e2);
            }

            final String message = String.format("Failed to update editor for %s", resource);
            logger.error(message, e);
            return StatusUtils.silentError(message, e);
        } catch(ThreadDeath e) {
            return StatusUtils.cancel();
        }
    }

    @Override protected void canceling() {
        final Thread thread = getThread();
        if(thread == null) {
            return;
        }

        logger.debug("Cancelling editor update job for {}, killing in {}ms", resource, killTimeMillis);
        threadKiller = new ThreadKillerJob(thread);
        threadKiller.schedule(killTimeMillis);
    }


    private IStatus update(IWorkspace workspace, final IProgressMonitor monitor) throws SpoofaxException, CoreException {
        final Display display = Display.getDefault();

        // Identify language
        final ILanguage parserLanguage = languageIdentifierService.identify(resource);
        if(parserLanguage == null) {
            throw new SpoofaxException("Language could not be identified");
        }
        ILanguage baseLanguage = dialectService.getBase(parserLanguage);
        final ILanguage language;
        if(baseLanguage == null) {
            language = parserLanguage;
        } else {
            language = baseLanguage;
        }

        // Parse
        if(monitor.isCanceled())
            return StatusUtils.cancel();
        final ParseResult<IStrategoTerm> parseResult;
        try {
            parseResultProcessor.invalidate(resource);
            parseResult = syntaxService.parse(text, resource, parserLanguage);
            parseResultProcessor.update(resource, parseResult);
        } catch(ParseException e) {
            parseResultProcessor.error(resource, e);
            throw e;
        } catch(ThreadDeath e) {
            parseResultProcessor.error(resource, new ParseException(resource, parserLanguage,
                "Editor update job killed", e));
            throw e;
        }

        // Style
        if(monitor.isCanceled())
            return StatusUtils.cancel();
        if(parseResult.result != null) {
            final Iterable<IRegionCategory<IStrategoTerm>> categories =
                CategorizerValidator.validate(categorizer.categorize(language, parseResult));
            final Iterable<IRegionStyle<IStrategoTerm>> styles = styler.styleParsed(language, categories);
            final TextPresentation textPresentation = StyleUtils.createTextPresentation(styles, display);
            presentationMerger.set(textPresentation);
            display.asyncExec(new Runnable() {
                public void run() {
                    if(monitor.isCanceled())
                        return;
                    // Also cancel if text presentation is not valid for current text any more.
                    final IDocument document = sourceViewer.getDocument();
                    if(document == null || !document.get().equals(text)) {
                        return;
                    }
                    sourceViewer.changeTextPresentation(textPresentation, true);
                }
            });
        }

        // Sleep before showing parse messages to prevent showing irrelevant messages while user is still typing.
        if(!isNewEditor) {
            try {
                Thread.sleep(300);
            } catch(InterruptedException e) {
                return StatusUtils.cancel();
            }
        }

        // Parse messages
        if(monitor.isCanceled())
            return StatusUtils.cancel();
        // Update markers atomically using a workspace runnable, to prevent flashing/jumping markers.
        final IWorkspaceRunnable parseMarkerUpdater = new IWorkspaceRunnable() {
            @Override public void run(IProgressMonitor workspaceMonitor) throws CoreException {
                if(workspaceMonitor.isCanceled())
                    return;
                MarkerUtils.clearInternal(eclipseResource);
                MarkerUtils.clearParser(eclipseResource);
                for(IMessage message : parseResult.messages) {
                    MarkerUtils.createMarker(eclipseResource, message);
                }
            }
        };
        workspace.run(parseMarkerUpdater, eclipseResource, IWorkspace.AVOID_UPDATE, monitor);

        // Stop if parsing failed completely, no AST.
        if(parseResult.result == null)
            return StatusUtils.silentError();

        // Sleep before analyzing to prevent running many analyses when small edits are made in succession.
        if(!isNewEditor) {
            try {
                Thread.sleep(300);
            } catch(InterruptedException e) {
                return StatusUtils.cancel();
            }
        }

        // Analyze
        if(monitor.isCanceled())
            return StatusUtils.cancel();
        final IContext context = contextService.get(resource, language);
        final AnalysisResult<IStrategoTerm, IStrategoTerm> analysisResult;
        synchronized(context) {
            analysisResultProcessor.invalidate(parseResult.source);
            try {
                analysisResult = analyzer.analyze(Iterables2.singleton(parseResult), context);
            } catch(AnalysisException e) {
                analysisResultProcessor.error(resource, e);
                throw e;
            } catch(ThreadDeath e) {
                analysisResultProcessor.error(resource, new AnalysisException(Iterables2.singleton(parseResult.source),
                    context, "Editor update job killed", e));
                throw e;
            }
            analysisResultProcessor.update(analysisResult);
        }

        // Analysis markers
        if(monitor.isCanceled())
            return StatusUtils.cancel();
        // Update markers atomically using a workspace runnable, to prevent flashing/jumping markers.
        final IWorkspaceRunnable analysisMarkerUpdater = new IWorkspaceRunnable() {
            @Override public void run(IProgressMonitor workspaceMonitor) throws CoreException {
                if(workspaceMonitor.isCanceled())
                    return;
                MarkerUtils.clearInternal(eclipseResource);
                MarkerUtils.clearAnalysis(eclipseResource);
                for(AnalysisFileResult<IStrategoTerm, IStrategoTerm> fileResult : analysisResult.fileResults) {
                    for(IMessage message : fileResult.messages) {
                        MarkerUtils.createMarker(eclipseResource, message);
                    }
                }
            }
        };
        workspace.run(analysisMarkerUpdater, eclipseResource, IWorkspace.AVOID_UPDATE, monitor);

        return StatusUtils.success();
    }
}
