package com.redhat.ceylon.eclipse.core.vfs;

import static com.redhat.ceylon.eclipse.java2ceylon.Java2CeylonProxies.modelJ2C;
import static com.redhat.ceylon.eclipse.java2ceylon.Java2CeylonProxies.utilJ2C;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;

import com.redhat.ceylon.compiler.typechecker.io.VirtualFile;
import com.redhat.ceylon.eclipse.java2ceylon.VfsJ2C;
import com.redhat.ceylon.ide.common.model.CeylonProjects;
import com.redhat.ceylon.ide.common.vfs.FileVirtualFile;
import com.redhat.ceylon.ide.common.vfs.FolderVirtualFile;
import com.redhat.ceylon.ide.common.vfs.ResourceVirtualFile;

public class vfsJ2C implements VfsJ2C {
    @Override
    public CeylonProjects<IProject,IResource, IFolder, IFile>.VirtualFileSystem eclipseVFS() {
        return modelJ2C().ceylonModel().getVfs();
    }
    
    @Override
    public ResourceVirtualFile<IProject, IResource, IFolder, IFile> createVirtualResource(IResource resource, IProject project) {
        return eclipseVFS().createVirtualResource(resource, project);
    }

    @Override
    public FileVirtualFile<IProject, IResource, IFolder, IFile> createVirtualFile(IFile file, IProject project) {
        // TODO ceylonProject should not be null 
        return eclipseVFS().createVirtualFile(file, null);
    }
    
    @Override
    public FileVirtualFile<IProject, IResource, IFolder, IFile> createVirtualFile(IProject project, IPath path) {
        return eclipseVFS().createVirtualFileFromProject(project, utilJ2C().fromEclipsePath(path));
    }
    
    @Override
    public FolderVirtualFile<IProject, IResource, IFolder, IFile> createVirtualFolder(IFolder folder, IProject project) {
        return eclipseVFS().createVirtualFolder(folder, project);
    }
    
    @Override
    public FolderVirtualFile<IProject, IResource, IFolder, IFile> createVirtualFolder(IProject project, IPath path) {
        return eclipseVFS().createVirtualFolderFromProject(project, utilJ2C().fromEclipsePath(path));
    }
    
    @Override
    public boolean instanceOfIFileVirtualFile(VirtualFile file) {
        return file instanceof IFileVirtualFile;
    }

    @Override
    public FileVirtualFile<IProject, IResource, IFolder, IFile> getIFileVirtualFile(VirtualFile file) {
        return (IFileVirtualFile) file;
    }

    @Override
    public boolean instanceOfIFolderVirtualFile(VirtualFile file) {
        return file instanceof IFolderVirtualFile;
    }

    @Override
    public FolderVirtualFile<IProject, IResource, IFolder, IFile> getIFolderVirtualFile(VirtualFile file) {
        return (IFolderVirtualFile) file;
    }

}