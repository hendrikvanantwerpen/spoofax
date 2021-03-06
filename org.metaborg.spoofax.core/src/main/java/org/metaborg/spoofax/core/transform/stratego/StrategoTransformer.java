package org.metaborg.spoofax.core.transform.stratego;

import java.util.Map;

import org.metaborg.spoofax.core.transform.ITransformerExecutor;
import org.metaborg.spoofax.core.transform.ITransformerGoal;
import org.metaborg.spoofax.core.transform.ITransformerResultHandler;
import org.metaborg.spoofax.core.transform.Transformer;
import org.spoofax.interpreter.terms.IStrategoTerm;

import com.google.inject.Inject;

/**
 * Typedef class for {@link Transformer} with {@link IStrategoTerm} as type arguments.
 */
public class StrategoTransformer extends Transformer<IStrategoTerm, IStrategoTerm, IStrategoTerm> {
    @Inject public StrategoTransformer(
        Map<Class<? extends ITransformerGoal>, ITransformerExecutor<IStrategoTerm, IStrategoTerm, IStrategoTerm>> executors,
        Map<Class<? extends ITransformerGoal>, ITransformerResultHandler<IStrategoTerm>> resultHandlers) {
        super(executors, resultHandlers);
    }
}
