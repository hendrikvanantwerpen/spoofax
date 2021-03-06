package org.metaborg.spoofax.core.stratego.strategies;

import java.io.IOException;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileType;
import org.metaborg.spoofax.core.language.ILanguage;
import org.metaborg.spoofax.core.language.ILanguageIdentifierService;
import org.metaborg.spoofax.core.resource.IResourceService;
import org.metaborg.spoofax.core.syntax.ISyntaxService;
import org.metaborg.spoofax.core.syntax.ParseException;
import org.metaborg.spoofax.core.syntax.ParseResult;
import org.metaborg.spoofax.core.text.ISourceTextService;
import org.spoofax.interpreter.core.Tools;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.strategoxt.lang.Context;
import org.strategoxt.lang.StrategoException;
import org.strategoxt.lang.Strategy;

import com.google.inject.Inject;

public class ParseFileStrategy extends Strategy {
    private final IResourceService resourceService;
    private final ILanguageIdentifierService languageIdentifierService;
    private final ISourceTextService sourceTextService;
    private final ISyntaxService<IStrategoTerm> syntaxService;


    @Inject public ParseFileStrategy(IResourceService resourceService,
        ILanguageIdentifierService languageIdentifierService, ISourceTextService sourceTextService,
        ISyntaxService<IStrategoTerm> syntaxService) {
        this.resourceService = resourceService;
        this.languageIdentifierService = languageIdentifierService;
        this.sourceTextService = sourceTextService;
        this.syntaxService = syntaxService;
    }


    @Override public IStrategoTerm invoke(Context context, IStrategoTerm current) {
        if(!Tools.isTermString(current))
            return null;

        try {
            final String path = Tools.asJavaString(current);
            final FileObject resource = resourceService.resolve(path);
            if(resource.getType() != FileType.FILE) {
                return null;
            }
            final ILanguage language = languageIdentifierService.identify(resource);
            if(language == null) {
                return null;
            }
            final String text = sourceTextService.text(resource);
            final ParseResult<IStrategoTerm> result = syntaxService.parse(text, resource, language);
            return result.result;
        } catch(ParseException | IOException e) {
            throw new StrategoException("Parsing failed", e);
        }
    }
}
