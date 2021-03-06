package org.metaborg.spoofax.core.language.dialect;

import java.io.IOException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.metaborg.spoofax.core.SpoofaxException;
import org.metaborg.spoofax.core.SpoofaxRuntimeException;
import org.metaborg.spoofax.core.language.ILanguage;
import org.metaborg.spoofax.core.language.ILanguageService;
import org.metaborg.spoofax.core.language.IdentificationFacet;
import org.metaborg.spoofax.core.terms.ITermFactoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spoofax.interpreter.core.Tools;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.terms.ParseError;
import org.spoofax.terms.io.binary.TermReader;

import com.google.inject.Inject;

public class StrategoDialectIdentifier implements IDialectIdentifier {
    private static final Logger logger = LoggerFactory.getLogger(StrategoDialectIdentifier.class);

    private final ILanguageService languageService;
    private final IDialectService dialectService;
    private final ITermFactoryService termFactoryService;


    @Inject public StrategoDialectIdentifier(ILanguageService languageService, IDialectService dialectService,
        ITermFactoryService termFactoryService) {
        this.languageService = languageService;
        this.dialectService = dialectService;
        this.termFactoryService = termFactoryService;
    }


    @Override public ILanguage identify(FileObject resource) throws SpoofaxException {
        final ILanguage strategoLanguage = languageService.get("Stratego-Sugar");
        if(strategoLanguage == null) {
            final String message = "Could not find Stratego language, Stratego dialects cannot be identified";
            logger.debug(message);
            throw new SpoofaxRuntimeException(message);
        }

        if(!strategoLanguage.facet(IdentificationFacet.class).identify(resource)) {
            return null;
        }

        try {
            final FileObject metaResource = metaResource(resource);
            if(metaResource == null) {
                return null;
            }
            final TermReader termReader = new TermReader(termFactoryService.getGeneric());
            final IStrategoTerm term = termReader.parseFromStream(metaResource.getContent().getInputStream());
            final String name = getSyntaxName(term.getSubterm(0));
            if(name == null) {
                return null;
            }
            final ILanguage dialect = dialectService.getDialect(name);
            if(dialect == null) {
                final String message =
                    String.format("Resource %s requires dialect %s, but that dialect does not exist", resource, name);
                throw new SpoofaxException(message);
            }
            return dialect;
        } catch(ParseError | IOException e) {
            throw new SpoofaxException("Unable to open or parse .meta file", e);
        }
    }

    @Override public boolean identify(FileObject resource, ILanguage dialect) throws SpoofaxException {
        final ILanguage identified = identify(resource);
        return dialect.equals(identified);
    }


    public static FileObject metaResource(FileObject resource) {
        try {
            final String path = resource.getName().getPath();
            final String fileName = FilenameUtils.getBaseName(path);
            if(fileName.isEmpty()) {
                return null;
            }
            final String metaResourceName = fileName + ".meta";
            final FileObject parent = resource.getParent();
            if(parent == null) {
                return null;
            }
            final FileObject metaResource = parent.getChild(metaResourceName);
            if(metaResource == null || !metaResource.exists()) {
                return null;
            }
            return metaResource;
        } catch(FileSystemException e) {
            return null;
        }
    }

    private static String getSyntaxName(IStrategoTerm entries) {
        for(IStrategoTerm entry : entries.getAllSubterms()) {
            final String cons = ((IStrategoAppl) entry).getConstructor().getName();
            if(cons.equals("Syntax")) {
                return Tools.asJavaString(entry.getSubterm(0));
            }
        }
        return null;
    }
}
