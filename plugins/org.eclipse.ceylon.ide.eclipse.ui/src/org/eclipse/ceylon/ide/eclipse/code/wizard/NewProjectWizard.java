/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 *
 * SPDX-License-Identifier: EPL-1.0
 ********************************************************************************/
package org.eclipse.ceylon.ide.eclipse.code.wizard;

import static org.eclipse.ceylon.ide.eclipse.java2ceylon.Java2CeylonProxies.modelJ2C;
import static org.eclipse.ceylon.ide.eclipse.ui.CeylonResources.CEYLON_NEW_PROJECT;
import static org.eclipse.ceylon.ide.eclipse.util.EditorUtil.getActivePage;
import static org.eclipse.ceylon.ide.eclipse.util.InteropUtils.toJavaString;
import static org.eclipse.jdt.launching.JavaRuntime.JRE_CONTAINER;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.EnumSet;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.wizards.NewElementWizard;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstall2;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ui.IPackagesViewPart;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.wizards.newresource.BasicNewProjectResourceWizard;

import org.eclipse.ceylon.compiler.typechecker.analyzer.Warning;
import org.eclipse.ceylon.ide.eclipse.code.explorer.PackageExplorerPart;
import org.eclipse.ceylon.ide.eclipse.code.preferences.CeylonRepoConfigBlock;
import org.eclipse.ceylon.ide.eclipse.core.builder.CeylonNature;
import org.eclipse.ceylon.ide.eclipse.ui.CeylonPlugin;
import org.eclipse.ceylon.ide.common.model.CeylonProject;
import org.eclipse.ceylon.ide.common.model.CeylonProjectConfig;

public class NewProjectWizard extends NewElementWizard 
        implements IExecutableExtension {

    private NewCeylonProjectWizardPageOne firstPage;
    private NewCeylonProjectWizardPageTwo secondPage;
    private NewCeylonProjectWizardPageThree thirdPage;
    
    private IConfigurationElement fConfigElement;

    public NewProjectWizard() {
        this(null, null);
    }

    public NewProjectWizard(
            NewCeylonProjectWizardPageOne pageOne, 
            NewCeylonProjectWizardPageTwo pageTwo) {
        ImageDescriptor desc = 
                CeylonPlugin.imageRegistry()
                    .getDescriptor(CEYLON_NEW_PROJECT);
        setDefaultPageImageDescriptor(desc);
        setDialogSettings(CeylonPlugin.getInstance().getDialogSettings());
        setWindowTitle("New Ceylon Project");
        firstPage = pageOne;
        secondPage = pageTwo;
    }

    public void addPages() {
        if (firstPage == null) {
            firstPage = 
                    new NewCeylonProjectWizardPageOne();
        }
        firstPage.setTitle("New Ceylon Project");
        firstPage.setDescription("Create a Ceylon project in the workspace or in an external location.");
        addPage(firstPage);

        if (secondPage == null) {
            secondPage = 
                    new NewCeylonProjectWizardPageTwo(firstPage);
        }
        secondPage.setTitle("Ceylon Project Settings");
        secondPage.setDescription("Define the Ceylon build settings.");
        addPage(secondPage);
        
        if (thirdPage == null) {
            thirdPage = 
                    new NewCeylonProjectWizardPageThree(secondPage);
        }
        thirdPage.setTitle("Ceylon Module Repository Settings");
        thirdPage.setDescription("Specify the Ceylon module repositories for the project.");
        addPage(thirdPage);
        
        firstPage.init(getSelection(), getActivePart());
    }
    
    protected void finishPage(IProgressMonitor monitor) 
            throws InterruptedException, CoreException {
        secondPage.performFinish(monitor); // use the full progress monitor
    }
    
    @Override
    public IWizardPage getNextPage(IWizardPage page) {
        if (page==firstPage && !checkJre()) {
            displayError("Please select a Java 1.7 or 1.8 JRE");
            return page;
        }
        else if (page==secondPage && !checkOutputPaths()) {
            displayError("Please select a different Java output path");
            return page;
        }
        else if (page==thirdPage && !checkOutputPaths()) {
            displayError("Please select a different Ceylon output path");
            return page;
        }
        else {
            clearErrors();
            return super.getNextPage(page);
        }
    }

    public boolean performFinish() {
        if (!checkJre()) {
            displayError("Please select a Java 1.7 or 1.8 JRE");
            return false;
        }
        if (!checkOutputPaths()) {
            displayError("Java and Ceylon output paths collide");
            return false;
        }
        
        if (super.performFinish()) {
            IWorkingSet[] workingSets = 
                    firstPage.getWorkingSets();
            if (workingSets.length > 0) {
                PlatformUI.getWorkbench()
                    .getWorkingSetManager()
                    .addToWorkingSets(getCreatedElement(), 
                            workingSets);
            }
            
            IProject project = getCreatedElement().getProject();
            
            CeylonProject<IProject, IResource,IFolder,IFile> ceylonProject = 
                    modelJ2C().ceylonModel().getProject(project);
            CeylonProjectConfig projectConfig = 
                    ceylonProject.getConfiguration();
            
            if (!firstPage.isShowCompilerWarnings()) {
                projectConfig.setProjectSuppressWarningsEnum(
                        EnumSet.allOf(Warning.class));
            }
            
            String jdkProvider = firstPage.getJdkProvider();
            if (jdkProvider!=null){
                projectConfig.setProjectJdkProvider(
                        ceylon.language.String.instance(jdkProvider));
            }
            
            Boolean offlineOption = firstPage.getOfflineOption();
            if (offlineOption!=null) {
                projectConfig.setProjectOffline(
                        ceylon.language.Boolean.instance(offlineOption));
            }
            
            CeylonRepoConfigBlock block = thirdPage.getBlock();
            if (block.getProject() != null) {
                projectConfig.setOutputRepo(block.getOutputRepo());
                
                block.applyToConfiguration(projectConfig);
                
                String overrides = block.getOverrides();
                if (overrides!=null) {
                    projectConfig.setProjectOverrides(
                            ceylon.language.String.instance(overrides));
                }

                Boolean flatClasspath = block.getFlatClasspath();
                if (flatClasspath!=null) { 
                    projectConfig.setProjectFlatClasspath(
                            ceylon.language.Boolean.instance(flatClasspath));
                }
                
                Boolean autoExportMavenDependencies = 
                        block.getAutoExportMavenDependencies();
                if (autoExportMavenDependencies!=null) {
                    projectConfig.setProjectAutoExportMavenDependencies(
                            ceylon.language.Boolean.instance(autoExportMavenDependencies));
                }

                Boolean fullyExportMavenDependencies = 
                        block.getFullyExportMavenDependencies();
                if (fullyExportMavenDependencies!=null) {
                    projectConfig.setProjectFullyExportMavenDependencies(
                            ceylon.language.Boolean.instance(fullyExportMavenDependencies));
                }
            }
            
            projectConfig.save();
            
            try {
                project.setDefaultCharset(
                        toJavaString(projectConfig.getEncoding()), 
                        new NullProgressMonitor());
            }
            catch (CoreException e) {
                throw new RuntimeException(e);
            }
            
            new CeylonNature(block.getSystemRepo(),
                    firstPage.isEnableJdtClassesDir(), 
                    firstPage.isCompileJava(),
                    firstPage.isCompileJs(),
                    firstPage.areAstAwareIncrementalBuildsEnabled(), 
                    null)
                .addToProject(project);

            if (firstPage.isCreateBoostrapFiles()) {
                CreateBootstrapFilesHandler.createBootstrapFiles(ceylonProject, firstPage.getBoostrapVersion(), getShell());
            }
            
            BasicNewProjectResourceWizard.updatePerspective(fConfigElement);
            selectAndReveal(project);             

            Display.getDefault().asyncExec(new Runnable() {
                public void run() {
                    IWorkbenchPart activePart = getActivePart();
                    if (activePart instanceof IPackagesViewPart) {
                        PackageExplorerPart.openInActivePerspective()
                            .tryToReveal(getCreatedElement());
                    }
                }
            });
            
            return true;
        }
        else {
            return false;
        }
    }

    private boolean checkJre() {
        for (IClasspathEntry cpe: 
                firstPage.getDefaultClasspathEntries()) {
            if (cpe.getEntryKind()==IClasspathEntry.CPE_CONTAINER) {                
                IPath path = cpe.getPath();
                if (path.segment(0).equals(JRE_CONTAINER)) {
                    IVMInstall vm = JavaRuntime.getVMInstall(path);
                    if (vm instanceof IVMInstall2) {
                        IVMInstall2 ivm = (IVMInstall2) vm;
                        String javaVersion = 
                                ivm.getJavaVersion();
                        if (!javaVersion.startsWith("1.7") && 
                            !javaVersion.startsWith("1.8")) {
                            return false;
                        }
                        if (path.segmentCount()==3) {
                            String s = path.segment(2);
                            if ((s.startsWith("JavaSE-") || 
                                 s.startsWith("J2SE-")) &&
                                    !s.contains("1.7") && 
                                    !s.contains("1.8")) {
                                return false;
                            }
                        }
                    }
                    else {
                        return false;
                    }
                }
            }
        }
        return true;
    }
    
    private boolean checkOutputPaths() {
        String ceylonOutString = 
                thirdPage.getBlock().getOutputRepo();
        if (ceylonOutString.startsWith("."+File.separator)) {
            ceylonOutString = firstPage.getProjectName() + 
                    ceylonOutString.substring(1);
        }
        IPath ceylonOut = Path.fromOSString(ceylonOutString);
        IPath javaOut = secondPage.getJavaOutputLocation();
        return javaOut==null || 
        		!ceylonOut.isPrefixOf(javaOut) && 
                !javaOut.isPrefixOf(ceylonOut);
    }

    private void displayError(String message) {
        for (IWizardPage page: getPages()) {
            if (page instanceof WizardPage) {
                WizardPage wizardPage = (WizardPage) page;
                wizardPage.setErrorMessage(message);
            }
        }
    }

    private void clearErrors() {
        for (IWizardPage page: getPages()) {
            if (page instanceof WizardPage) {
                WizardPage wizardPage = (WizardPage) page;
                wizardPage.setErrorMessage(null);
            }
        }
    }

    private IWorkbenchPart getActivePart() {
        IWorkbenchPage activePage = getActivePage();
        if (activePage != null) {
            return activePage.getActivePart();
        }
        return null;
    }

    protected void handleFinishException(Shell shell, 
            InvocationTargetException e) {
        ExceptionHandler.handle(e, getShell(), 
                NewWizardMessages.JavaProjectWizard_op_error_title, 
                NewWizardMessages.JavaProjectWizard_op_error_create_message);
    }   

    public void setInitializationData(
            IConfigurationElement cfig, 
            String propertyName, Object data) {
        fConfigElement= cfig;
    }

    public boolean performCancel() {
        secondPage.performCancel();
        return super.performCancel();
    }

    public IJavaProject getCreatedElement() {
        return secondPage.getJavaProject();
    }
}