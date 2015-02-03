package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.code.refactor.MoveUtil.addImportEdits;
import static com.redhat.ceylon.eclipse.code.refactor.MoveUtil.createEditorChange;
import static com.redhat.ceylon.eclipse.code.refactor.MoveUtil.refactorDocLinks;
import static com.redhat.ceylon.eclipse.code.refactor.MoveUtil.refactorImports;
import static com.redhat.ceylon.eclipse.code.refactor.MoveUtil.refactorProjectImportsAndDocLinks;
import static com.redhat.ceylon.eclipse.code.refactor.MoveUtil.removeImport;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getProjectTypeChecker;
import static com.redhat.ceylon.eclipse.util.EditorUtil.getFile;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.util.Nodes.getNodeLength;
import static com.redhat.ceylon.eclipse.util.Nodes.getNodeStartOffset;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;

public class MoveToUnitRefactoring extends Refactoring {
    
    private final CeylonEditor editor;
    private final Tree.CompilationUnit rootNode;
    private final Tree.Declaration node;
    private final IFile originalFile; 
    private final IDocument document;
    private IFile targetFile;
    
    public void setTargetFile(IFile targetFile) {
        this.targetFile = targetFile;
    }
    
    public IFile getOriginalFile() {
        return originalFile;
    }
    
    public Tree.Declaration getNode() {
        return node;
    }
    
    public MoveToUnitRefactoring(CeylonEditor ceylonEditor) {
        editor = ceylonEditor;
        rootNode = editor.getParseController().getRootNode();
        document = editor.getDocumentProvider()
                .getDocument(editor.getEditorInput());
        originalFile = getFile(editor.getEditorInput());
        if (rootNode!=null) {
            Node node = editor.getSelectedNode();
            if (node instanceof Tree.Declaration) {
                this.node = (Tree.Declaration) node;
            }
            else {
                this.node = null;
            }
        }
        else {
            this.node = null;
        }
    }
    
    @Override
    public boolean isEnabled() {
        return node!=null;
    }

    @Override
    public String getName() {
        return "Move to Source File";
    }

    @Override
    public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        return new RefactoringStatus();
    }

    @Override
    public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        RefactoringStatus refactoringStatus = new RefactoringStatus();
        if (!targetFile.exists()) {
            refactoringStatus.addError("source file does not exist");
        }
        Tree.CompilationUnit targetRootNode = getTargetRootNode();
        Package targetPackage = targetRootNode.getUnit().getPackage();
        HashSet<String> packages = new HashSet<String>();
        Map<Declaration, String> imports = 
                MoveUtil.getImports(node, 
                        targetPackage.getNameAsString(), 
                        targetRootNode, packages);
        for (Declaration d: imports.keySet()) {
            Package p = d.getUnit().getPackage();
            String packageName = p.getNameAsString();
            if (packageName.isEmpty()) {
                refactoringStatus.addWarning("moved declaration depends on declaration in the default package: " +
                        d.getName());
            }
            else {
                if (!d.isShared() &&
                        !packageName.equals(targetPackage.getNameAsString())) {
                    refactoringStatus.addWarning("moved declaration depends on unshared declaration: " + 
                            d.getName());
                }
                if (targetPackage.getModule().getPackage(packageName)==null) {
                    refactoringStatus.addWarning("moved declaration depends on declaration in unimported module: " + 
                            d.getName() + " in module " + p.getModule().getNameAsString());
                }
            }
        }
        return refactoringStatus;
    }

    @Override
    public Change createChange(IProgressMonitor pm) 
            throws CoreException, OperationCanceledException {
        Tree.CompilationUnit ncu = getTargetRootNode();
        String original = rootNode.getUnit()
                .getPackage().getNameAsString();
        String moved = ncu.getUnit()
                .getPackage().getNameAsString();
        
        Declaration dec = node.getDeclarationModel();
        int start = getNodeStartOffset(node);
        int length = getNodeLength(node);
        
        CompositeChange change = 
                new CompositeChange("Move to Source File");
        
        TextChange targetUnitChange = 
                new TextFileChange("Move to Source File", 
                        targetFile);
        targetUnitChange.setEdit(new MultiTextEdit());
        IDocument targetUnitDocument = 
                targetUnitChange.getCurrentDocument(null);
        String contents;
        try {
            contents = document.get(start, length);
        }
        catch (BadLocationException e) {
            e.printStackTrace();
            throw new OperationCanceledException();
        }
        String delim = getDefaultLineDelimiter(targetUnitDocument);
        String text = delim + contents;
        Set<String> packages = new HashSet<String>();
        addImportEdits(node, targetUnitChange, targetUnitDocument, 
                ncu, packages, dec);
        removeImport(original, dec, ncu, targetUnitChange, packages);
        targetUnitChange.addEdit(new InsertEdit(targetUnitDocument.getLength(), text));
        targetUnitChange.setTextType("ceylon");
        change.add(targetUnitChange);
        
        TextChange originalUnitChange = 
                createEditorChange(editor, document);
        originalUnitChange.setEdit(new MultiTextEdit());
        refactorImports(node, original, 
                moved, rootNode, originalUnitChange);
        refactorDocLinks(node, moved, rootNode, 
                originalUnitChange);
        originalUnitChange.addEdit(new DeleteEdit(start, length));
        originalUnitChange.setTextType("ceylon");
        change.add(originalUnitChange);
        
        refactorProjectImportsAndDocLinks(node, originalFile, targetFile, 
                change, original, moved);
        
        //TODO: DocLinks
        
        return change;
    }

    public Tree.CompilationUnit getTargetRootNode() {
        IProject project = targetFile.getProject();
        String path = 
                targetFile.getProjectRelativePath()
                    .removeFirstSegments(1)
                    .toPortableString();
        return getProjectTypeChecker(project)
                .getPhasedUnitFromRelativePath(path)
                .getCompilationUnit();
    }

    public int getOffset() {
        return 0; //TODO!!!
    }

    public IPath getTargetPath() {
        return targetFile.getFullPath();
    }

}
