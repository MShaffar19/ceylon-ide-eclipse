package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.java2ceylon.Java2CeylonProxies.refactorJ2C;
import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;
import static com.redhat.ceylon.model.typechecker.model.ModelUtil.isTypeUnknown;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.TextEdit;

import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.util.EditorUtil;
import com.redhat.ceylon.ide.common.refactoring.ExtractLinkedModeEnabled;
import com.redhat.ceylon.ide.common.refactoring.ExtractFunctionRefactoring;
import com.redhat.ceylon.model.typechecker.model.Type;

public final class ExtractFunctionLinkedMode 
        extends ExtractLinkedMode {
        
    private final ExtractFunctionRefactoring<IFile, ICompletionProposal, IDocument, InsertEdit, TextEdit, TextChange, IRegion> refactoring;
    
    public ExtractFunctionLinkedMode(CeylonEditor editor) {
        super(editor);
        this.refactoring = refactorJ2C().newExtractFunctionRefactoring(editor);
    }
    
    public ExtractFunctionLinkedMode(CeylonEditor editor, Tree.Declaration target) {
        super(editor);
        this.refactoring = refactorJ2C().newExtractFunctionRefactoring(editor, target);
    }
    
    @Override
    protected int performInitialChange(IDocument document) {
        DocumentChange change = 
                new DocumentChange("Extract Function", 
                        document);
        refactoring.build(change);
        EditorUtil.performChange(change);
        return 0;
    }
    
    @Override
    protected boolean canStart() {
        return refactoring.getEnabled();
    }
    
    @Override
    protected int getNameOffset() {
        return refactorJ2C().toExtractLinkedModeEnabled(refactoring)
                .getDecRegion().getOffset();
    }
    
    @Override
    protected int getTypeOffset() {
        return refactorJ2C().toExtractLinkedModeEnabled(refactoring)
                .getTypeRegion().getOffset();
    }
    
    @Override
    protected int getExitPosition(int selectionOffset, int adjust) {
        return refactorJ2C().toExtractLinkedModeEnabled(refactoring)
                .getRefRegion().getOffset();
    }
    
    @Override
    protected String[] getNameProposals() {
    	return refactorJ2C().toExtractLinkedModeEnabled(refactoring)
    	        .getNameProposals();
    }
    
    @Override
    protected void addLinkedPositions(IDocument document,
            CompilationUnit rootNode, int adjust) {
        
        ExtractLinkedModeEnabled<IRegion> elme = 
                refactorJ2C().toExtractLinkedModeEnabled(refactoring);
        
        addNamePosition(document, 
                elme.getRefRegion().getOffset(),
                elme.getRefRegion().getLength());
        
        Type type = refactoring.getType();
        if (!isTypeUnknown(type)) {
            addTypePosition(document, type, 
                    elme.getTypeRegion().getOffset(), 
                    elme.getTypeRegion().getLength());
        }
    }
    
    @Override
    protected String getName() {
        return refactoring.getNewName();
    }
    
    @Override
    protected void setName(String name) {
        refactoring.setNewName(name);
    }
    
    @Override
    protected boolean forceWizardMode() {
        return refactoring.forceWizardMode();
    }
    
    @Override
    protected String getActionName() {
        return PLUGIN_ID + ".action.extractFunction";
    }
    
    @Override
    protected void openPreview() {
        new ExtractFunctionRefactoringAction(editor) {
            @Override
            public Refactoring createRefactoring() {
                return (Refactoring) ExtractFunctionLinkedMode.this.refactoring;
            }
            @Override
            public RefactoringWizard createWizard(Refactoring refactoring) {
                return new ExtractFunctionWizard(refactoring) {
                    @Override
                    protected void addUserInputPages() {}
                };
            }
        }.run();
    }

    @Override
    protected void openDialog() {
        new ExtractFunctionRefactoringAction(editor) {
            @Override
            public Refactoring createRefactoring() {
                return (Refactoring) ExtractFunctionLinkedMode.this.refactoring;
            }
        }.run();
    }
    
    @Override
    public boolean canBeInferred() {
        return refactoring.getCanBeInferred();
    }
    
    @Override
    protected String getKind() {
        return "function";
    }

    public static void selectExpressionAndStart(
            final CeylonEditor editor) {
        final Shell shell = editor.getSite().getShell();
        if (editor.getSelection().getLength()>0) {
            new SelectContainerPopup(shell, 0, editor,
                    "Extract Function To") {
                @Override void finish() {
                    new ExtractFunctionLinkedMode(editor, getResult()).start();
                }
                @Override boolean isEnabled() {
                    return new refactorJ2C().newExtractFunctionRefactoring(editor).getEnabled();
                }
            }
            .open();
        }
        else {
            new SelectExpressionPopup(shell, 0, editor,
                    "Extract Function") {
                @Override void finish() {
                    new SelectContainerPopup(shell, 0, editor,
                            "Extract Function To") {
                        @Override void finish() {
                            new ExtractFunctionLinkedMode(editor, getResult()).start();
                        }
                        @Override boolean isEnabled() {
                            return new refactorJ2C().newExtractFunctionRefactoring(editor).getEnabled();
                        }
                    }
                    .open();
                }
            }
            .open();
        }
    }

}
