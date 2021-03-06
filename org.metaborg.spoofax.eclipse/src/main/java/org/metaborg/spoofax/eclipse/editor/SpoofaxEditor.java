package org.metaborg.spoofax.eclipse.editor;

import java.awt.Color;
import java.util.Collection;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.vfs2.FileObject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextViewerExtension4;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.source.DefaultCharacterPairMatcher;
import org.eclipse.jface.text.source.ICharacterPairMatcher;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;
import org.metaborg.spoofax.core.analysis.IAnalysisService;
import org.metaborg.spoofax.core.context.IContextService;
import org.metaborg.spoofax.core.language.ILanguage;
import org.metaborg.spoofax.core.language.ILanguageIdentifierService;
import org.metaborg.spoofax.core.language.dialect.IDialectService;
import org.metaborg.spoofax.core.style.ICategorizerService;
import org.metaborg.spoofax.core.style.IStylerService;
import org.metaborg.spoofax.core.syntax.FenceCharacters;
import org.metaborg.spoofax.core.syntax.ISyntaxService;
import org.metaborg.spoofax.eclipse.SpoofaxPlugin;
import org.metaborg.spoofax.eclipse.job.GlobalSchedulingRules;
import org.metaborg.spoofax.eclipse.processing.AnalysisResultProcessor;
import org.metaborg.spoofax.eclipse.processing.ParseResultProcessor;
import org.metaborg.spoofax.eclipse.resource.IEclipseResourceService;
import org.metaborg.spoofax.eclipse.util.Nullable;
import org.metaborg.spoofax.eclipse.util.StyleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spoofax.interpreter.terms.IStrategoTerm;

import com.google.common.collect.Lists;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

public class SpoofaxEditor extends TextEditor {
    private static final Logger logger = LoggerFactory.getLogger(SpoofaxEditor.class);

    public static final String id = SpoofaxPlugin.id + ".editor";

    private IEclipseResourceService resourceService;
    private ILanguageIdentifierService languageIdentifier;
    private IDialectService dialectService;
    private IContextService contextService;
    private ISyntaxService<IStrategoTerm> syntaxService;
    private IAnalysisService<IStrategoTerm, IStrategoTerm> analysisService;
    private ICategorizerService<IStrategoTerm, IStrategoTerm> categorizerService;
    private IStylerService<IStrategoTerm, IStrategoTerm> stylerService;

    private ParseResultProcessor parseResultProcessor;
    private AnalysisResultProcessor analysisResultProcessor;
    private GlobalSchedulingRules globalRules;

    private IJobManager jobManager;

    private final IPropertyListener editorInputChangedListener;
    private final PresentationMerger presentationMerger;

    private IEditorInput input;
    private FileObject resource;
    private IResource eclipseResource;
    private IDocument document;
    private ILanguage language;
    private ISourceViewer sourceViewer;
    private ITextViewerExtension4 textViewerExt;
    private DocumentListener documentListener;


    public SpoofaxEditor() {
        super();

        this.editorInputChangedListener = new EditorInputChangedListener();
        this.presentationMerger = new PresentationMerger();

        setDocumentProvider(new SpoofaxDocumentProvider());
    }


    /**
     * @return Current input, or null if the editor has not been initialized yet, or if it has been disposed.
     */
    public @Nullable IEditorInput input() {
        if(!checkInitialized()) {
            return null;
        }
        return input;
    }

    /**
     * @return Current resource, or null if the editor has not been initialized yet, or if it has been disposed.
     */
    public @Nullable FileObject resource() {
        if(!checkInitialized()) {
            return null;
        }
        return resource;
    }

    /**
     * @return Current Eclipse resource, or null if the editor has not been initialized yet, or if it has been disposed.
     */
    public @Nullable IResource eclipseResource() {
        if(!checkInitialized()) {
            return null;
        }
        return eclipseResource;
    }

    /**
     * @return Current document, or null if the editor has not been initialized yet, or if it has been disposed.
     */
    public @Nullable IDocument document() {
        if(!checkInitialized()) {
            return null;
        }
        return document;
    }

    /**
     * @return Language of the current input/document, or null if the editor has not been initialized yet, if it has
     *         been disposed, or if the editor was opened before languages were loaded.
     */
    public @Nullable ILanguage language() {
        if(!checkInitialized()) {
            return null;
        }
        return language;
    }

    /**
     * @return Source viewer, or null if the editor has not been initialized yet, or if it has been disposed.
     */
    public @Nullable ISourceViewer sourceViewer() {
        if(!checkInitialized()) {
            return null;
        }
        return getSourceViewer();
    }

    /**
     * @return Source viewer configuration, or null if the editor has not been initialized yet, or if it has been
     *         disposed.
     */
    public @Nullable SourceViewerConfiguration configuration() {
        if(!checkInitialized()) {
            return null;
        }
        return getSourceViewerConfiguration();
    }

    /**
     * Enables parsing, analysis, and editor services. Does nothing if editor has not been initialized, or if it has
     * been disposed, or if the editor is already enabled.
     */
    public void enable() {
        if(!checkInitialized()) {
            return;
        }
        if(documentListener == null) {
            logger.debug("Enabling editor for {}", input);
            documentListener = new DocumentListener();
            document.addDocumentListener(documentListener);
            scheduleJob(true);
        }
    }

    /**
     * Disables parsing, analysis, and editor services. Does nothing if editor has not been initialized, or if it has
     * been disposed, or if the editor is already disabled.
     */
    public void disable() {
        if(!checkInitialized()) {
            return;
        }
        if(documentListener != null) {
            logger.debug("Disabling editor for {}", input);
            document.removeDocumentListener(documentListener);
            documentListener = null;

            final Display display = Display.getDefault();
            final TextPresentation blackPresentation =
                StyleUtils.createTextPresentation(Color.BLACK, document.getLength(), display);
            presentationMerger.invalidate();
            display.asyncExec(new Runnable() {
                @Override public void run() {
                    sourceViewer.changeTextPresentation(blackPresentation, true);
                }
            });
        }
    }

    /**
     * Force a parser, analysis, and editor services update. Does nothing if editor has not been initialized, or if it
     * has been disposed.
     */
    public void forceUpdate() {
        if(!checkInitialized()) {
            return;
        }
        logger.debug("Force updating editor for {}", input);
        scheduleJob(true);
    }


    @Override protected void initializeEditor() {
        super.initializeEditor();

        SpoofaxEditorPreferences.setDefaults(getPreferenceStore());
        
        final Injector injector = SpoofaxPlugin.injector();
        this.resourceService = injector.getInstance(IEclipseResourceService.class);
        this.languageIdentifier = injector.getInstance(ILanguageIdentifierService.class);
        this.dialectService = injector.getInstance(IDialectService.class);
        this.contextService = injector.getInstance(IContextService.class);
        this.syntaxService = injector.getInstance(Key.get(new TypeLiteral<ISyntaxService<IStrategoTerm>>() {}));
        this.analysisService =
            injector.getInstance(Key.get(new TypeLiteral<IAnalysisService<IStrategoTerm, IStrategoTerm>>() {}));
        this.categorizerService =
            injector.getInstance(Key.get(new TypeLiteral<ICategorizerService<IStrategoTerm, IStrategoTerm>>() {}));
        this.stylerService =
            injector.getInstance(Key.get(new TypeLiteral<IStylerService<IStrategoTerm, IStrategoTerm>>() {}));

        this.parseResultProcessor = injector.getInstance(ParseResultProcessor.class);
        this.analysisResultProcessor = injector.getInstance(AnalysisResultProcessor.class);
        this.globalRules = injector.getInstance(GlobalSchedulingRules.class);

        this.jobManager = Job.getJobManager();

        setEditorContextMenuId("#SpoofaxEditorContext");
        setSourceViewerConfiguration(new SpoofaxSourceViewerConfiguration(this, syntaxService));
    }

    @Override protected ISourceViewer createSourceViewer(Composite parent, IVerticalRuler ruler, int styles) {
        // Store current input and document so we have access to them when the editor input changes.
        input = getEditorInput();
        document = getDocumentProvider().getDocument(input);
        documentListener = new DocumentListener();
        document.addDocumentListener(documentListener);

        // Store resources for future use.
        resource = resourceService.resolve(input);
        eclipseResource = resourceService.unresolve(resource);

        // Identify the language for future use. Will be null if this editor was opened when Eclipse opened, because
        // languages have not been discovered yet.
        language = languageIdentifier.identify(resource);

        // Create source viewer after input, document, resources, and language have been set.
        sourceViewer = super.createSourceViewer(parent, ruler, styles);
        textViewerExt = (ITextViewerExtension4) sourceViewer;

        // Register for changes in the editor input, to handle renaming or moving of resources of open editors.
        this.addPropertyListener(editorInputChangedListener);

        // Register for changes in text presentation, to merge our text presentation with presentations from other
        // sources, such as marker annotations.
        textViewerExt.addTextPresentationListener(presentationMerger);

        scheduleJob(true);

        return sourceViewer;
    }

    @Override protected void configureSourceViewerDecorationSupport(SourceViewerDecorationSupport support) {
        super.configureSourceViewerDecorationSupport(support);

        if(language == null) {
            logger.warn("Cannot get language-specific fences, identified language for {} is null, "
                + "bracket matching is disabled", resource);
            return;
        }

        // Implementation based on:
        // http://www.sigasi.com/content/how-implement-highlight-matching-brackets-your-custom-editor-eclipse
        final Iterable<FenceCharacters> fenceCharacters = syntaxService.fenceCharacters(language);
        final Collection<Character> pairMatcherChars = Lists.newArrayList();
        for(FenceCharacters fenceChars : fenceCharacters) {
            final String open = fenceChars.open;
            final String close = fenceChars.close;
            if(open.length() > 1 || close.length() > 1) {
                logger.debug("Multi-character fences {} {} are not supported in Eclipse", open, close);
                continue;
            }
            pairMatcherChars.add(open.charAt(0));
            pairMatcherChars.add(close.charAt(0));
        }

        final ICharacterPairMatcher matcher =
            new DefaultCharacterPairMatcher(ArrayUtils.toPrimitive(pairMatcherChars.toArray(new Character[0])));
        support.setCharacterPairMatcher(matcher);
        SpoofaxEditorPreferences.setPairMatcherKeys(support);
    }

    @Override public void dispose() {
        cancelJobs(input);

        if(documentListener != null) {
            document.removeDocumentListener(documentListener);
        }
        this.removePropertyListener(editorInputChangedListener);
        textViewerExt.removeTextPresentationListener(presentationMerger);

        input = null;
        document = null;
        sourceViewer = null;
        textViewerExt = null;
        documentListener = null;

        super.dispose();
    }


    private boolean checkInitialized() {
        if(input == null || document == null || sourceViewer == null) {
            logger.error("Attempted to use editor before it was initialized");
            return false;
        }
        return true;
    }

    private void scheduleJob(boolean instantaneous) {
        if(!checkInitialized()) {
            return;
        }

        cancelJobs(input);

        // THREADING: invalidate text styling here on the main thread (instead of in the editor update job), to prevent
        // race conditions.
        presentationMerger.invalidate();

        final Job job =
            new EditorUpdateJob(languageIdentifier, dialectService, contextService, syntaxService, analysisService,
                categorizerService, stylerService, parseResultProcessor, analysisResultProcessor, input,
                eclipseResource, resource, sourceViewer, document.get(), presentationMerger, instantaneous);
        job.setRule(new MultiRule(new ISchedulingRule[] { globalRules.startupReadLock(), eclipseResource }));
        job.schedule(instantaneous ? 0 : 100);
    }

    private void cancelJobs(IEditorInput specificInput) {
        logger.trace("Cancelling editor update jobs for {}", specificInput);
        final Job[] existingJobs = jobManager.find(specificInput);
        for(Job job : existingJobs) {
            job.cancel();
        }
    }

    private void editorInputChanged() {
        final IEditorInput oldInput = input;
        final IDocument oldDocument = document;

        logger.debug("Editor input changed from {} to {}", oldInput, input);

        // Unregister old document listener and register a new one, because the input changed which also changes the
        // document.
        if(documentListener != null) {
            oldDocument.removeDocumentListener(documentListener);
        }
        input = getEditorInput();
        document = getDocumentProvider().getDocument(input);
        documentListener = new DocumentListener();
        document.addDocumentListener(documentListener);

        // Store new resource and language, because these have changed as a result of the input change.
        resource = resourceService.resolve(input);
        eclipseResource = resourceService.unresolve(resource);
        language = languageIdentifier.identify(resource);

        cancelJobs(oldInput);
        scheduleJob(true);
    }


    private final class DocumentListener implements IDocumentListener {
        @Override public void documentAboutToBeChanged(DocumentEvent event) {

        }

        @Override public void documentChanged(DocumentEvent event) {
            scheduleJob(false);
        }
    }

    private final class EditorInputChangedListener implements IPropertyListener {
        @Override public void propertyChanged(Object source, int propId) {
            if(propId == IEditorPart.PROP_INPUT) {
                editorInputChanged();
            }
        }
    }
}