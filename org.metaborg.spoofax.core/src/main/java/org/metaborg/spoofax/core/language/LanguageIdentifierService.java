package org.metaborg.spoofax.core.language;

import java.util.Set;

import org.apache.commons.vfs2.FileObject;
import org.metaborg.spoofax.core.SpoofaxException;
import org.metaborg.spoofax.core.SpoofaxRuntimeException;
import org.metaborg.spoofax.core.language.dialect.IDialectIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

public class LanguageIdentifierService implements ILanguageIdentifierService {
    private static final Logger logger = LoggerFactory.getLogger(LanguageIdentifierService.class);

    private final ILanguageService languageService;
    private final IDialectIdentifier dialectIdentifier;


    @Inject public LanguageIdentifierService(ILanguageService languageService, IDialectIdentifier dialectIdentifier) {
        this.languageService = languageService;
        this.dialectIdentifier = dialectIdentifier;
    }


    @Override public ILanguage identify(FileObject resource) {
        try {
            final ILanguage dialect = dialectIdentifier.identify(resource);
            if(dialect != null) {
                return dialect;
            }
        } catch(SpoofaxException e) {
            logger.error("Error identifying dialect", e);
            return null;
        } catch(SpoofaxRuntimeException e) {
            // Ignore
        }

        final Set<String> identifiedLanguageNames = Sets.newLinkedHashSet();
        ILanguage identifiedLanguage = null;
        for(ILanguage language : languageService.getAllActive()) {
            if(identify(resource, language)) {
                identifiedLanguageNames.add(language.name());
                identifiedLanguage = language;
            }
        }

        if(identifiedLanguageNames.size() > 1) {
            throw new IllegalStateException("Resource " + resource + " identifies to multiple languages: "
                + Joiner.on(", ").join(identifiedLanguageNames));
        }

        return identifiedLanguage;
    }

    @Override public boolean identify(FileObject resource, ILanguage language) {
        final IdentificationFacet identification = language.facet(IdentificationFacet.class);
        if(identification == null) {
            logger.error("Cannot identify resources of {}, language does not have an identification facet", language);
            return false;
        }
        return identification.identify(resource);
    }
}
