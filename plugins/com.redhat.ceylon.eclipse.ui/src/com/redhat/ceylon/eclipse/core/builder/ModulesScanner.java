package com.redhat.ceylon.eclipse.core.builder;

import static com.redhat.ceylon.compiler.typechecker.model.Util.formatPath;
import static com.redhat.ceylon.eclipse.core.vfs.ResourceVirtualFile.createResourceVirtualFile;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.analyzer.ModuleManager;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnits;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.eclipse.core.model.JDTModelLoader;
import com.redhat.ceylon.eclipse.core.model.JDTModuleManager;
import com.redhat.ceylon.eclipse.core.vfs.ResourceVirtualFile;

final class ModulesScanner implements IResourceVisitor {
	private final Module defaultModule;
	private final JDTModelLoader modelLoader;
	private final JDTModuleManager moduleManager;
	private final ResourceVirtualFile srcDir;
	private final IPath srcFolderPath;
    private final TypeChecker typeChecker;
    private final List<IFile> scannedSources;
    private final PhasedUnits phasedUnits;
	private Module module;

	ModulesScanner(Module defaultModule, JDTModelLoader modelLoader,
			JDTModuleManager moduleManager, ResourceVirtualFile srcDir,
			IPath srcFolderPath, TypeChecker typeChecker,
            List<IFile> scannedSources, PhasedUnits phasedUnits) {
		this.defaultModule = defaultModule;
		this.modelLoader = modelLoader;
		this.moduleManager = moduleManager;
		this.srcDir = srcDir;
		this.srcFolderPath = srcFolderPath;
        this.typeChecker = typeChecker;
        this.scannedSources = scannedSources;
        this.phasedUnits = phasedUnits;
	}

	public boolean visit(IResource resource) throws CoreException {
	    if (resource.equals(srcDir.getResource())) {
	        IFile moduleFile = ((IFolder) resource).getFile(ModuleManager.MODULE_FILE);
	        if (moduleFile.exists()) {
	            moduleManager.addTopLevelModuleError();
	        }
	        return true;
	    }

	    if (resource.getParent().equals(srcDir.getResource())) {
	        // We've come back to a source directory child : 
	        //  => reset the current Module to default and set the package to emptyPackage
	        module = defaultModule;
	    }

	    if (resource instanceof IFolder) {
	        List<String> pkgName = Arrays.asList(resource.getProjectRelativePath()
	        		.makeRelativeTo(srcFolderPath).segments());
	        String pkgNameAsString = formatPath(pkgName);
	        
	        if ( module != defaultModule ) {
	            if (! pkgNameAsString.startsWith(module.getNameAsString() + ".")) {
	                // We've ran above the last module => reset module to default 
	                module = defaultModule;
	            }
	        }
	        
	        IFile moduleFile = ((IFolder) resource).getFile(ModuleManager.MODULE_FILE);
	        if (moduleFile.exists()) {
	            if ( module != defaultModule ) {
	                moduleManager.addTwoModulesInHierarchyError(module.getName(), pkgName);
	            } else {
	                // First create the package with the default module and we'll change the package
	                // after since the module doesn't exist for the moment and the package is necessary 
	                // to create the PhasedUnit which in turns is necessary to create the module with the 
	                // right version from the beginning (which is necessary now because the version is 
	                // part of the Module signature used in equals/has methods and in caching
	                // The right module will be set when calling findOrCreatePackage() with the right module 
	                Package pkg = new Package();
	                pkg.setName(pkgName);
	                
	                IFile file = moduleFile;
                    ResourceVirtualFile virtualFile = createResourceVirtualFile(file);
                    boolean moduleVisited = false;
                    try {
                        PhasedUnit tempPhasedUnit = null;
                        tempPhasedUnit = CeylonBuilder.parseFileToPhasedUnit(moduleManager, 
                                typeChecker, virtualFile, srcDir, pkg);
                        module = tempPhasedUnit.visitSrcModulePhase();
                        moduleVisited = true;
                    } 
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (!moduleVisited) {
                        final List<String> moduleName = pkgName;
                        module = moduleManager.getOrCreateModule(moduleName, null);
                    }
	                
	                assert(module != null);
	            }
	        }
	        
	        if (module != defaultModule) {
	            // Creates a package with this module only if it's not the default
	            // => only if it's a *ceylon* module
	            modelLoader.findOrCreatePackage(module, pkgNameAsString);
	        }
	        return true;
	    }
	    return false;
	}
}