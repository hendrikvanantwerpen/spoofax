/**
 * 
 */
package org.strategoxt.imp.runtime.services;

import static org.spoofax.interpreter.core.Tools.asJavaString;
import static org.spoofax.interpreter.core.Tools.isTermString;
import static org.spoofax.interpreter.core.Tools.isTermTuple;
import static org.spoofax.interpreter.core.Tools.termAt;

import java.io.File;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.imp.language.ILanguageService;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocumentListener;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.strategoxt.imp.runtime.EditorState;
import org.strategoxt.imp.runtime.Environment;
import org.strategoxt.imp.runtime.parser.ast.AstNode;
import org.strategoxt.imp.runtime.stratego.RefreshResourcePrimitive;

/**
 * @author Lennart Kats <lennart add lclnet.nl>
 */
public class OnSaveService implements IDocumentListener, ILanguageService {

	private final StrategoObserver runtime;
	
	private final String function;
	
	private EditorState editor;
	
	public OnSaveService(StrategoObserver runtime, String function) {
		this.runtime = runtime;
		this.function = function;
	}
	
	public void initialize(EditorState editor) {
		this.editor = editor;
	}

	public void documentAboutToBeChanged(DocumentEvent event) {
		// Unused
	}

	public void documentChanged(DocumentEvent event) {
		if (function == null) return;
		
		synchronized (Environment.getSyncRoot()) {
			AstNode ast = editor.getCurrentAst();
			// IStrategoTerm result = runtime.invokeSilent(function, runtime.makeInputTerm(ast, false), ast.getResource());
			IStrategoTerm result = runtime.invokeSilent(function, ast);
			if (result == null) {
				runtime.reportRewritingFailed();
				String log = runtime.getLog();
				Environment.logException(log.length() == 0 ? "Analysis failed" : "Analysis failed:\n" + log);
			} else if (isTermString(result)) {
				// Function's returning a filename
				String file = asJavaString(termAt(result, 0));
				if (new File(file).exists())
					RefreshResourcePrimitive.call(runtime.getRuntime().getContext(), file);	
			} else if (isTermTuple(result) && result.getSubtermCount() == 2 && isTermString(termAt(result, 0)) && isTermString(termAt(result, 1))) {
				// Function's returning a tuple like a builder
				// let's be friendly and try to refresh the file
				String file = asJavaString(termAt(result, 0));
				String contents = asJavaString(termAt(result, 1));
				IResource resource = RefreshResourcePrimitive.getResource(runtime.getRuntime().getContext(), file);
				// TODO: write contents to file
				try {
					StrategoBuilder.setFileContentsDirect((IFile) resource, contents);
				} catch (CoreException e) {
					Environment.logException("Problem when handling on save event", e);
				}
			}
		}
	}

}