package org.strategoxt.imp.runtime.dynamicloading;

import java.util.ArrayList;

import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoTerm;
import static org.spoofax.interpreter.terms.IStrategoTerm.*;

/**
 * Term reading utility class.
 * 
 * @see org.spoofax.interpreter.core.Tools
 * 	    A similar utility class, not specific for reading descriptors.
 * 
 * @author Lennart Kats <lennart add lclnet.nl>
 */
public class TermReader {
	private TermReader() {
		// TermReader cannot be constructed
	}
	
	public static IStrategoAppl findTerm(IStrategoTerm term, String constructor) {
		if (term instanceof IStrategoAppl && cons(term).equals(constructor))
			return (IStrategoAppl) term;
		
		for (int i = 0; i < term.getSubtermCount(); i++) {
			IStrategoAppl result = findTerm(termAt(term, i), constructor);
			if (result != null) return result;
		}
		
		return null;
	}
	
	public static ArrayList<IStrategoAppl> collectTerms(IStrategoAppl term, String... constructors) {
		ArrayList<IStrategoAppl> results = new ArrayList<IStrategoAppl>();
		for (String constructor : constructors) {
			collectTerms(term, constructor, results);
		}
		return results;
	}
	
	private static void collectTerms(IStrategoTerm term, String constructor, ArrayList<IStrategoAppl> results) {
		if (term instanceof IStrategoAppl && cons(term).equals(constructor))
			results.add((IStrategoAppl) term);
		
		for (int i = 0; i < term.getSubtermCount(); i++) {
			collectTerms(termAt(term, i), constructor, results);
		}
	}
	
	public static String termContents(IStrategoTerm t) {
		if (t == null) return null;
		
		String result;
		
		if (t.getTermType() == STRING) {
			result = t.toString();
		} else if (t.getTermType() == APPL && cons((IStrategoAppl) t).equals("String")) {
			return termContents(termAt(t, 0));
		} else {
			if (t.getSubtermCount() == 0 || termAt(t, 0).getTermType() == APPL)
				return null;
			result = termAt(t, 0).toString();
		}
		
		if (result.startsWith("\"") && result.endsWith("\""))
			result = result.substring(1, result.length() - 1);
		
		return result;
	}

	public static String concatTermStrings(IStrategoTerm list) {
		IStrategoTerm values = termAt(list, 0);
		StringBuilder results = new StringBuilder();
		
		if (values.getSubtermCount() > 0)
			results.append(termContents(termAt(values, 0)));
		
		for (int i = 1; i <  values.getSubtermCount(); i++) {
			results.append(',');
			results.append(termContents(termAt(values, i)));
		}
		return results.toString();
	}
	
	public static int intAt(IStrategoTerm t, int index) {
		return Integer.parseInt(termContents(t.getSubterm(index)));
	}
	
	public static String cons(IStrategoTerm t) {
		if (t == null || t.getTermType() != APPL)
			return null;
		return ((IStrategoAppl) t).getConstructor().getName();
	}

    @SuppressWarnings("unchecked") // casting is inherently unsafe, but doesn't warrant a warning here
    public static<T extends IStrategoTerm> T termAt(IStrategoTerm t, int index) {
        return (T) t.getSubterm(index);
    }
}