package org.strategoxt.imp.runtime.stratego.adapter;

import org.spoofax.interpreter.terms.IStrategoInt;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermPrinter;
import org.strategoxt.imp.runtime.parser.ast.IntAstNode;

public class WrappedAstNodeInt extends WrappedAstNode implements IStrategoInt {

	private final IntAstNode wrappee;
	
	protected WrappedAstNodeInt(WrappedAstNodeFactory factory, IntAstNode node) {
		super(factory, node);
		this.wrappee = node;
	}

	public int intValue() {
		return wrappee.getValue();
	}
	
	public IStrategoTerm[] getArguments() {
		return getAllSubterms();
	}
	
	@Override
	protected boolean doSlowMatch(IStrategoTerm second, int commonStorageType) {
		return second.getTermType() == IStrategoTerm.INT
			&& ((IStrategoInt) second).intValue() == intValue()
			&& second.getAnnotations().equals(getAnnotations());
	}

    public void prettyPrint(ITermPrinter pp) {
    	pp.print("" + wrappee.getValue());
    	printAnnotations(pp);
    }

	public int getTermType() {
		return IStrategoTerm.INT;
	}
	
	@Override
	public int hashFunction() {
		return 449 * intValue() ^ 7841;
	}
}
