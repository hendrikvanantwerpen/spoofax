/**
 * 
 */
package org.strategoxt.imp.runtime.services.menus.model;

import static java.util.Collections.synchronizedMap;
import static org.spoofax.interpreter.core.Tools.termAt;
import static org.strategoxt.imp.runtime.dynamicloading.TermReader.collectTerms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.spoofax.interpreter.core.InterpreterErrorExit;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.core.InterpreterExit;
import org.spoofax.interpreter.core.UndefinedStrategyException;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoString;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.IStrategoTuple;
import org.spoofax.interpreter.terms.ITermFactory;
import org.strategoxt.imp.runtime.EditorState;
import org.strategoxt.imp.runtime.services.InputTermBuilder;
import org.strategoxt.imp.runtime.services.StrategoObserver;
import org.strategoxt.imp.runtime.stratego.SourceAttachment;

/**
 * @author Lennart Kats <lennart add lclnet.nl>
 */
public class CustomStrategyBuilder extends StrategoBuilder {
	
	public final static String CAPTION = "Apply Custom Rule...";
	
	private static final Map<String, String> initialValues =
		synchronizedMap(new HashMap<String, String>());
	
	private String inputtedBuilderRule;
	
	public CustomStrategyBuilder(List<String> path, StrategoObserver observer, EditorState derivedFromEditor) {
		// TODO: reconsider source = true here?
		super(observer, path, null, true, true, false, true, false, derivedFromEditor);
	}
	
	@Override
	public Job scheduleExecute(EditorState editor, IStrategoTerm node, IFile errorReportFile,
			boolean isRebuild) {
		
		String builderRule = inputBuilderRule(editor);
		if (builderRule != null) {
			setBuilderRule(builderRule);
			return super.scheduleExecute(editor, node, errorReportFile, isRebuild);
		} else {
			return null;
		}
	}
	
	@Override
	public Object getData() {
		return inputtedBuilderRule;
	}
	
	@Override
	public void setData(Object data) {
		inputtedBuilderRule = (String) data;
	}
	
	private String inputBuilderRule(EditorState editor) {
		if (inputtedBuilderRule != null)
			return inputtedBuilderRule;
		IInputValidator validator = 
			new IInputValidator() {
				public String isValid(String name) {
					return getObserver().getRuntime().lookupUncifiedSVar(name) == null
							? "No rule or strategy with this name"
							: null;
				}
			};
		
		getObserver().getLock().lock();
		try {
			InputDialog dialog = new InputDialog(null, "Apply Custom Rule", "Enter the name of the rewrite rule or strategy to apply", getInitialValue(editor), validator);
			if (dialog.open() == InputDialog.OK) {
				setInitialValue(editor, dialog.getValue());
				return inputtedBuilderRule = dialog.getValue();
			} else {
				return null;
			}
		} finally {
			getObserver().getLock().unlock();
		}
	}
	
	@Override
	protected IStrategoTerm invokeObserver(EditorState editor, IStrategoTerm node)
			throws UndefinedStrategyException, InterpreterErrorExit, InterpreterExit,
			InterpreterException {

		// Try invoke using (term)
		InputTermBuilder inputBuilder = getObserver().getInputBuilder();
		IStrategoTerm input = getDerivedFromEditor() == null
				? node
				: inputBuilder.implodeATerm(inputBuilder.getImplodableNode(node));
		IStrategoTerm result = getObserver().invoke(getBuilderRule(), input, SourceAttachment.getFile(node));
		if (result != null) return addFileName(result, SourceAttachment.getResource(node));
		String[] trace1 = getObserver().getRuntime().getCompiledContext().getTrace();
		
		// Try invoke using (term, ast, ...) tuple
		result = super.invokeObserver(editor, node);
		if (result != null) return result;
		String[] trace2 = getObserver().getRuntime().getCompiledContext().getTrace();
		
		// Report the previous stack trace if the new one is very short
		if (trace2.length == 1)
			getObserver().getRuntime().getCompiledContext().setTrace(trace1);

		return null;
	}
	
	private IStrategoTuple addFileName(IStrategoTerm result, IResource resource) {
		ITermFactory factory = getObserver().getRuntime().getFactory();
		IPath source = resource.getProjectRelativePath().removeFileExtension();
		String target = addCounter(source).addFileExtension("aterm").toPortableString();
		return factory.makeTuple(factory.makeString(target), result);
	}

	private IPath addCounter(IPath path) {
		if (getDerivedFromEditor() == null)
			return path;
		String counter = "2";
		if (path.getFileExtension() != null) {
			try {
				int prevCounter = Integer.parseInt(path.getFileExtension());
				path.removeFileExtension();
				counter = String.valueOf(prevCounter + 1);
			} catch (NumberFormatException e) {
				// Leave the counter at 2
			}
		}
		return path.addFileExtension(counter);
	}

	private void setInitialValue(EditorState editor, String value) {
		initialValues.put(editor.getLanguage().getName(), value);
	}

	private String getInitialValue(EditorState editor) {
		String language = editor.getLanguage().getName();
		String result = initialValues.get(language);
		if (result != null) return result;
		
		ArrayList<IStrategoAppl> allActions = collectTerms(editor.getDescriptor().getDocument(), "Action", "Builder");
		if (allActions.size() >= 1) {
			result = ((IStrategoString) termAt(termAt(allActions.get(0), 1), 0)).stringValue();
		}
		
		return result == null ? "" : result;
	}
}
