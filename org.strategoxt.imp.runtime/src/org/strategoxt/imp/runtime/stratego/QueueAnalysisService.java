package org.strategoxt.imp.runtime.stratego;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.imp.language.Language;
import org.eclipse.imp.language.LanguageRegistry;
import org.spoofax.interpreter.library.language.INotificationService;
import org.spoofax.interpreter.library.language.NotificationCenter;
import org.spoofax.interpreter.library.language.SemanticIndexManager;
import org.strategoxt.imp.runtime.Debug;
import org.strategoxt.imp.runtime.Environment;
import org.strategoxt.imp.runtime.services.StrategoAnalysisQueueFactory;

/**
 * A {@link INotificationService} that uses {@link StrategoAnalysisQueueFactory}.
 * 
 * Receives notifications sent by {@link FileNotificationServer} to the {@link NotificationCenter}.
 * 
 * @author Lennart Kats <lennart add lclnet.nl>
 */
public class QueueAnalysisService implements INotificationService {

	public void notifyFileChanges(URI file, String subfile) {
		assert file.isAbsolute();
		try {
			IProject project = EditorIOAgent.getProject(new File(file));
			IPath path = new Path(file.getPath());
			
			if (LanguageRegistry.findLanguage(path, null) != null) {
				IPath relPath = path.removeFirstSegments(path.matchingFirstSegments(project.getLocation()));
				assert !relPath.isAbsolute();
				StrategoAnalysisQueueFactory.getInstance().queueAnalysis(relPath, project);
			}
		} catch (FileNotFoundException e) {
			Environment.logException("Background language service failed", e);
		} catch (RuntimeException e) {
			Environment.logException("Background language service failed", e);
		}
	}

	/**
	 * Notify changes of all files in a project,
	 * as long as they are known to have an index associated with them.
	 */
	public void notifyNewProject(URI project) {
		Debug.log("Loading uninitialized project ", project);
		notifyNewProjectFiles(new File(project));
	}

	private void notifyNewProjectFiles(File file) {
		if (file.isFile()) {
			if (isIndexedFile(new Path(file.getAbsolutePath())))
				notifyFileChanges(file.toURI(), null);
		} else {
			for (File child : file.listFiles()) {
				notifyNewProjectFiles(child);
			}
		}
	}

	public static boolean isIndexedFile(IPath path) {
		Language language = LanguageRegistry.findLanguage(path, null);
		return language != null && SemanticIndexManager.isKnownIndexingLanguage(language.getName());
	}
	
}
