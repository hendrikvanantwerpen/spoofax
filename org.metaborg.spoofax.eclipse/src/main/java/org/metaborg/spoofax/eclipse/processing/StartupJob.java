package org.metaborg.spoofax.eclipse.processing;

import java.io.File;

import org.apache.commons.vfs2.FileObject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;
import org.metaborg.spoofax.core.language.ILanguageDiscoveryService;
import org.metaborg.spoofax.eclipse.resource.IEclipseResourceService;
import org.metaborg.spoofax.eclipse.util.StatusUtils;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartupJob extends Job {
    private static final Logger logger = LoggerFactory.getLogger(StartupJob.class);

    private final IEclipseResourceService resourceService;
    private final ILanguageDiscoveryService languageDiscoveryService;


    public StartupJob(IEclipseResourceService resourceService, ILanguageDiscoveryService languageDiscoveryService) {
        super("Loading all Spoofax languages in workspace");
        setPriority(Job.LONG);

        this.resourceService = resourceService;
        this.languageDiscoveryService = languageDiscoveryService;
    }


    @Override protected IStatus run(IProgressMonitor monitor) {
        logger.debug("Running startup job");
        logger.debug("Loading static languages");
        final IExtensionRegistry registry = Platform.getExtensionRegistry();
        final IExtensionPoint point = registry.getExtensionPoint("org.metaborg.spoofax.eclipse.language");
        for(IConfigurationElement config : point.getConfigurationElements()) {
            if(config.getName().equals("language")) {
                final String relativeEsvLocation = config.getAttribute("esvFile");
                final String contributor = config.getDeclaringExtension().getContributor().getName();
                try {
                    final Bundle bundle = Platform.getBundle(contributor);
                    final File bundleLocationFile = FileLocator.getBundleFile(bundle);
                    final FileObject bundleLocation = resourceService.resolve(bundleLocationFile);
                    final FileObject esvLocation = bundleLocation.resolveFile(relativeEsvLocation);
                    languageDiscoveryService.discover(esvLocation);
                } catch(Exception e) {
                    final String message =
                        String.format("Could not load language from %s in plugin %s", relativeEsvLocation, contributor);
                    logger.error(message, e);
                }
            }
        }

        logger.debug("Loading dynamic languages");
        for(final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if(project.isOpen()) {
                final FileObject location = resourceService.resolve(project);
                try {
                    languageDiscoveryService.discover(location);
                } catch(Exception e) {
                    final String message = String.format("Could not load language at location %s", location);
                    logger.error(message, e);
                }
            }
        }
        return StatusUtils.success();
    }
}