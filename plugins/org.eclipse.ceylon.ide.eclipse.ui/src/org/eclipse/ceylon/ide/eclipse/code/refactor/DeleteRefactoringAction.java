/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 *
 * SPDX-License-Identifier: EPL-1.0
 ********************************************************************************/
package org.eclipse.ceylon.ide.eclipse.code.refactor;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ui.IEditorPart;

public class DeleteRefactoringAction extends AbstractRefactoringAction {
    public DeleteRefactoringAction(IEditorPart editor) {
        super(editor);
    }
    
    @Override
    public Refactoring createRefactoring() {
        return new DeleteRefactoring(editor);
    }
    
    @Override
    public RefactoringWizard createWizard(Refactoring refactoring) {
        return new DeleteWizard((AbstractRefactoring) refactoring);
    }
    
    @Override
    public String message() {
        return "No declaration name selected";
    }
    
}
