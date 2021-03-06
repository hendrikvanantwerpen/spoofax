package org.strategoxt.imp.runtime.dynamicloading;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;

import org.eclipse.imp.language.Language;
import org.eclipse.imp.language.LanguageRegistry;
import org.eclipse.imp.runtime.RuntimePlugin;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IFileEditorMapping;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.UIPlugin;
import org.eclipse.ui.internal.registry.EditorDescriptor;
import org.eclipse.ui.internal.registry.EditorRegistry;
import org.eclipse.ui.internal.registry.FileEditorMapping;
import org.strategoxt.imp.runtime.editor.SpoofaxEditor;

/**
 * Registers dynamically loaded descriptors and languages.
 * 
 * @author Lennart Kats <lennart add lclnet.nl>
 */
@SuppressWarnings("restriction") // (also seen in org.eclipse.imp.language.LanguageRegistry)
public class DescriptorRegistry {
	
	private final EditorRegistry editorRegistry;
	
	private final EditorDescriptor spoofaxEditor;
	
	/**
	 * A complete list of all dynamically loaded languages,
	 * of which we ensure they exist in the Eclipse editor registry.
	 */
	private final List<Language> asyncAddedLanguages = new ArrayList<Language>();
	
	public DescriptorRegistry() {
		editorRegistry = (EditorRegistry) PlatformUI.getWorkbench().getEditorRegistry();
		spoofaxEditor = getSpoofaxEditor(editorRegistry);
	}
	
	public static EditorDescriptor getSpoofaxEditor(EditorRegistry editorRegistry) {
		for (IEditorDescriptor editor : editorRegistry.getSortedEditorsFromPlugins()) {
			if (editor.getId().equals(SpoofaxEditor.EDITOR_ID))
				return (EditorDescriptor) editor;
		}

		
		throw new IllegalStateException("Could not find editor descriptor for universal editor");
	}
	
	public void register(Descriptor descriptor) throws BadDescriptorException {
		// TODO: Don't re-register languages that were already declared (possibly using XML)?
		//       there should be some accessor in Descriptor that gives me this info
		
		Language language = descriptor.getLanguage();
		registerWithImp(language);
		
		// After IMP modifies the Eclipse editor registry, we queue another job
		// to make sure it's all okay.
		synchronized (asyncAddedLanguages) {
			asyncAddedLanguages.add(language);
		}
		Display.getDefault().asyncExec(new AsyncEditorRegisterer());
	}

	private void registerWithImp(Language language) {
		final int TRIES = 10;
		final int SLEEP = 500;
		
		for (int i = 0; i < TRIES; i++) {
			try {
				LanguageRegistry.registerLanguage(language);
				return;
			} catch (ConcurrentModificationException e) {
				// Loop
				try {
					Thread.sleep(SLEEP);
				} catch (InterruptedException e2) {
					throw new RuntimeException(e2);
				}
			}
		}
	}

	/**
	 * Binds a UniversalEditor to each extension of a given language.
	 * 
	 * @author Lennart Kats <lennart add lclnet.nl>
	 */
	private class AsyncEditorRegisterer implements Runnable {
		
		public void run() {
			synchronized (asyncAddedLanguages) {
				for (Language language : asyncAddedLanguages) {
					register(language);
				}
			}
		}

		private void register(Language language) {
			List<IFileEditorMapping> mappings = getMappings();
			
			for (String extension : language.getFilenameExtensions()) {
				addMapping(mappings, extension);
			}
			
			editorRegistry.setFileEditorMappings(mappings.toArray(new FileEditorMapping[mappings.size()]));
			editorRegistry.saveAssociations(); // like IMP, we persist
		}
	
		private List<IFileEditorMapping> getMappings() {
			IFileEditorMapping[] mappings = editorRegistry.getFileEditorMappings();
			List<IFileEditorMapping> results = new ArrayList<IFileEditorMapping>();
			Collections.addAll(results, mappings);
			return results;
		}
		
		private void addMapping(List<IFileEditorMapping> mappings, String extension) {
			FileEditorMapping mapping = getMapping(mappings, extension);
			
			boolean existing = mapping != null;
			if (!existing)
	            mapping = new DynamicEditorMapping(extension); // TODO: create something like a IMPFileEditorMapping, linking to our favorite image 
			
	        /* UNDONE: no longer setting default editor; breaks icon
	        IEditorDescriptor defaultEditor = mapping.getDefaultEditor();
	        
	        if (defaultEditor == null || defaultEditor.getId().equals("")
	        		|| "str".equals(extension) || "sdf".equals(extension)) {
	        	mapping.setDefaultEditor(universalEditor);
	        } else {*/
	        	if (!isUniversalEditorIncluded(mapping))
	        		mapping.addEditor(spoofaxEditor);
	        //}
	        
	        if (!existing)
	        	mappings.add(mapping);
	        
	        replaceUniversalEditorWithSpoofaxEditor(mapping);
		}
		
		private boolean isUniversalEditorIncluded(IFileEditorMapping mapping) {
			for (IEditorDescriptor editor : mapping.getEditors()) {
				if (editor == spoofaxEditor) return true;
			}
			return false;
    	}
		
		private FileEditorMapping getMapping(List<IFileEditorMapping> mappings, String extension) {
			for (IFileEditorMapping mapping : mappings) {
				if (extension.equals(mapping.getExtension())) {
					if (mapping instanceof FileEditorMapping)
						return (FileEditorMapping) mapping;
				}
			}
			return null;
		}
	}
	
	/**
	 * Maps an extension to an editor and provides editors with an icon.
	 */
	private static class DynamicEditorMapping extends FileEditorMapping {
		
		private final ImageDescriptor imageDescriptor =
			UIPlugin.imageDescriptorFromPlugin(
					RuntimePlugin.getInstance().getID(), Descriptor.DEFAULT_ICON);

		public DynamicEditorMapping(String extension) {
			super(extension);
		}
		
		@Override
		public ImageDescriptor getImageDescriptor() {
			return imageDescriptor;
		}
		
	}
	
	private void replaceUniversalEditorWithSpoofaxEditor(FileEditorMapping mapping) {
		List<IEditorDescriptor> descriptors = new ArrayList<IEditorDescriptor>();
		
		descriptors.add(spoofaxEditor);
		mapping.setEditorsList(descriptors);
		mapping.setDefaultEditor(spoofaxEditor);
	}
}
