package org.strategoxt.imp.runtime.services;

import java.util.ArrayList;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

/**
 * @author Maartje de Jonge
 */
public class StrategoRefactoringPage extends UserInputWizardPage {
	
	public StrategoRefactoringPage() {
		super("SpoofaxRefactoringInputPage");
	}

	public void createControl(Composite parent) {
		Composite result= new Composite(parent, SWT.NONE);
		setControl(result);
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		result.setLayout(layout);
		ModifyListener modListener = new ModifyListener() {
			public void modifyText(ModifyEvent event) {
				handleInputChanged();
			}
		};
		ArrayList<IStrategoRefactoringInput> inputFields = 
			getStrategoRefactoring().getInputFields();
		for (IStrategoRefactoringInput input : inputFields) {
			input.setInputArea(result, modListener);
		}
		handleInputChanged();
	}

	private StrategoRefactoring getStrategoRefactoring() {
		return (StrategoRefactoring) getRefactoring();
	}

	void handleInputChanged() {
		RefactoringStatus status = validateUserInput();
		//set message
		int severity= status.getSeverity();
		String message= status.getMessageMatchingSeverity(severity);
		if (severity >= RefactoringStatus.INFO && message != "") {
			setMessage(message, severity);
		} else {
			setMessage("", NONE);
		}
		setPageComplete(!status.hasError());
	}

	private RefactoringStatus validateUserInput() {
		RefactoringStatus status = new RefactoringStatus();
		for (IStrategoRefactoringInput input : getStrategoRefactoring().getInputFields()) {
			status.merge(input.validateUserInput());
		}
		return status;
	}
}
