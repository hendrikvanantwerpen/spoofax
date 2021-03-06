package org.strategoxt.imp.runtime.dynamicloading;

import static org.spoofax.interpreter.terms.IStrategoTerm.APPL;
import static org.spoofax.interpreter.terms.IStrategoTerm.STRING;
import static org.spoofax.terms.Term.tryGetName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.spoofax.interpreter.core.Tools;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;

/**
 * Term reading utility class, extending {@link org.spoofax.interpreter.core.Tools}
 * with new methods specific for reading descriptors.
 * 
 * @author Lennart Kats <lennart add lclnet.nl>
 */
public class TermReader extends Tools {
	private TermReader() {
		// TermReader cannot be constructed
	}
	
	public static IStrategoAppl findTerm(IStrategoTerm term, String constructor) {
		if (term.getTermType() == IStrategoTerm.APPL && cons(term).equals(constructor))
			return (IStrategoAppl) term;
		
		IStrategoTerm[] subterms = term.getAllSubterms();
		for (int i = subterms.length - 1; i >= 0; i--) {
			IStrategoAppl result = findTerm(subterms[i], constructor);
			if (result != null) return result;
		}
		
		return null;
	}
	
	public static ArrayList<IStrategoAppl> collectTerms(IStrategoTerm term, String... constructors) {
		Set<String> constructorSet = new HashSet<String>(Arrays.asList(constructors));
		ArrayList<IStrategoAppl> result = new ArrayList<IStrategoAppl>();
		collectTerms(term, constructorSet, result);
		return result;
	}
	
	private static void collectTerms(IStrategoTerm term, Set<String> constructors, List<IStrategoAppl> results) {
		if (term.getTermType() == IStrategoTerm.APPL && constructors.contains(cons(term))) {
			results.add((IStrategoAppl) term);
		}
		else {
			// TODO: optimize: use TermVisitor, avoid indexed access to long lists
			for (int i = 0; i < term.getSubtermCount(); i++) {
				collectTerms(termAt(term, i), constructors, results);
			}
		}
	}
	
	public static String termContents(IStrategoTerm t) {
		if (t == null) return null;
		
		String result;
		
		if (t.getTermType() == STRING) {
			result = asJavaString(t);
		} else if (t.getSubtermCount() == 1 && "Values".equals(tryGetName(t))) {
			return concatTermStrings(listAt(t, 0));
		} else if (t.getTermType() == APPL && t.getSubtermCount() == 1 && termAt(t, 0).getTermType() == STRING) {
			result = asJavaString(termAt(t, 0));
		} else if (t.getTermType() == APPL && t.getSubtermCount() == 1) {
			return termContents(termAt(t, 0));
		} else {
			return null;
		}
		
		if (result.startsWith("\"") && result.endsWith("\"") && result.length() > 1)
			result = result.substring(1, result.length() - 1).replace("\\\\", "\"");
		
		return result;
	}

	public static String concatTermStrings(IStrategoList values) {
		StringBuilder results = new StringBuilder();
		
		if (values.getSubtermCount() > 0)
			results.append(termContents(termAt(values, 0)));
		
		for (int i = 1; i <  values.getSubtermCount(); i++) {
			results.append(',');
			results.append(termContents(termAt(values, i)));
		}
		return results.toString();
	}
	
	public static int parseIntAt(IStrategoTerm t, int index) {
		return Integer.parseInt(termContents(t.getSubterm(index)));
	}
	
	public static String cons(IStrategoTerm t) {
		if (t == null || t.getTermType() != APPL)
			return null;
		return ((IStrategoAppl) t).getConstructor().getName();
	}
}
