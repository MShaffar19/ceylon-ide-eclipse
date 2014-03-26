package com.redhat.ceylon.eclipse.code.wizard;

import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getCeylonRepositories;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getProjectDeclaredSourceModules;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.CEYLON_EXPORT_CAR;
import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider;
import com.redhat.ceylon.eclipse.code.select.ProjectSelectionDialog;
import com.redhat.ceylon.eclipse.ui.CeylonPlugin;

public class ExportModuleWizardPage extends WizardPage implements IWizardPage {

    static final String CLEAN_BUILD_BEFORE_EXPORT = "cleanBuildBeforeExport";
    //private IStructuredSelection selection;
    private String repositoryPath;
    private IProject project;
    private IJavaElement selection;
    private boolean clean = true;
    
    ExportModuleWizardPage(String defaultRepositoryPath, 
            IProject project, IJavaElement selection) {
        super("Export Ceylon Module", "Export Ceylon Module", CeylonPlugin.getInstance()
                .getImageRegistry().getDescriptor(CEYLON_EXPORT_CAR));
        setDescription("Export a Ceylon module to a module repository.");
        repositoryPath = defaultRepositoryPath;
        this.project = project;
        this.selection = selection;
    }

    /*public void init(IStructuredSelection selection) {
        this.selection = selection;
    }*/
    
    @Override
    public void createControl(Composite parent) {
        initializeDialogUnits(parent);
        
        Composite composite= new Composite(parent, SWT.NONE);
        composite.setFont(parent.getFont());

        GridLayout layout = new GridLayout();
        layout.numColumns = 4;
        composite.setLayout(layout);
        
        //TODO: let you select a module descriptor to
        //      export just that module!
        addSelectProject(composite);
        addSelectRepo(composite);
        
        setControl(composite);

        Dialog.applyDialogFont(composite);

        setPageComplete(isComplete());
    }

    private void updateMessage() {
        if (project==null) {
            setErrorMessage("Please select a project");
        }
        else if (!isValidRepo()) {
            setErrorMessage("Please select an existing local repository");
        }
        else if (getModules().isEmpty()) {
            setErrorMessage("Please select a module to export");
        }
        else {
            setErrorMessage(null);
        }
    }

    void addSelectRepo(Composite composite) {
        Label folderLabel = new Label(composite, SWT.LEFT | SWT.WRAP);
        folderLabel.setText("Target module repository: ");
        GridData flgd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
        flgd.horizontalSpan = 1;
        folderLabel.setLayoutData(flgd);

        final Combo folder = new Combo(composite, SWT.SINGLE | SWT.BORDER);
        GridData fgd = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
        fgd.horizontalSpan = 2;
        fgd.grabExcessHorizontalSpace = true;
        fgd.widthHint = 300;
        folder.setLayoutData(fgd);
        folder.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                repositoryPath = folder.getText();
                updateMessage();
                setPageComplete(isComplete());
            }
        });
        
        folder.setText(repositoryPath);
        if (project!=null) {
            folder.add(repositoryPath);
            for (String path: getCeylonRepositories(project.getProject())) {
                if (!path.startsWith("http://") && !path.equals(repositoryPath)) {
                    folder.add(path);
                }
            }
        }
        
        Button selectFolder = new Button(composite, SWT.PUSH);
        selectFolder.setText("Browse...");
        GridData sfgd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
        sfgd.horizontalSpan = 1;
        selectFolder.setLayoutData(sfgd);
        selectFolder.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String dir = new DirectoryDialog(getShell(), SWT.SHEET).open();
                if (dir!=null) {
                    repositoryPath = dir;
                    folder.setText(repositoryPath);
                }
                updateMessage();
                setPageComplete(isComplete());
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
        });
        
        new Label(composite, SWT.NONE);
        final Button et = new Button(composite, SWT.CHECK);
        et.setText("Perform a clean build before exporting");
        clean = getDialogSettings().getBoolean(CLEAN_BUILD_BEFORE_EXPORT);
        et.setSelection(clean);
        et.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                clean = !clean;
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent event) {}
        });
        
        modules.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateMessage();
                setPageComplete(isComplete());
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
        });
    
    }
    
    Table modules;
    
    void addSelectProject(Composite composite) {
        
        Label projectLabel = new Label(composite, SWT.LEFT | SWT.WRAP);
        projectLabel.setText("Project containing modules: ");
        GridData plgd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
        plgd.horizontalSpan = 1;
        projectLabel.setLayoutData(plgd);

        final Text projectField = new Text(composite, SWT.SINGLE | SWT.BORDER);
        GridData pgd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
        pgd.horizontalSpan = 2;
        pgd.grabExcessHorizontalSpace = true;
        projectField.setLayoutData(pgd);
        
        Button selectProject = new Button(composite, SWT.PUSH);
        selectProject.setText("Browse...");
        GridData spgd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
        spgd.horizontalSpan = 1;
        selectProject.setLayoutData(spgd);

        Label modulesLabel = new Label(composite, SWT.LEFT | SWT.WRAP);
        modulesLabel.setText("Modules to export: ");
        GridData mlgd= new GridData(GridData.HORIZONTAL_ALIGN_FILL|GridData.VERTICAL_ALIGN_BEGINNING);
        mlgd.horizontalSpan = 1;
        modulesLabel.setLayoutData(mlgd);

        modules = new Table(composite, SWT.CHECK | SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
        modules.setHeaderVisible(true);
        modules.setLinesVisible(true);
        GridData mgd = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
        mgd.horizontalSpan = 2;
        mgd.grabExcessHorizontalSpace = true;
        mgd.heightHint = 100;
        mgd.widthHint = 240;
        modules.setLayoutData(mgd);
        TableColumn nameColumn = new TableColumn(modules, SWT.NONE);
        nameColumn.setText("Module");
        nameColumn.setWidth(180);
        TableColumn versionColumn = new TableColumn(modules, SWT.NONE, 1);
        versionColumn.setText("Version");
        versionColumn.setWidth(50);
        
        if (project!=null) {
            projectField.setText(project.getName());
            updateModuleList();
        }
        if (selection instanceof IPackageFragment) {
            String selectionName = selection.getElementName();
            TableItem[] items = modules.getItems();
            boolean found=false;
            for (int i=0; i<items.length; i++) {
                if (items[i].getText().equals(selectionName)) {
                    if (!found) {
                        for (TableItem it: items) {
                            it.setChecked(false);
                        }
//                        modules.deselectAll();
                        found=true;
                    }
                    items[i].setChecked(true);
//                    modules.select(i);
                }
            }
        }
        
        new Label(composite, SWT.NONE);
        
        selectProject.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IProject result = 
                        ProjectSelectionDialog.selectProject(getShell());
                if (result!=null) {
                    project = result;
                    projectField.setText(project.getName());
                    updateModuleList();
                }
                updateMessage();
                setPageComplete(isComplete());
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
        });
        
        projectField.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                String projectName = projectField.getText();
                if (project==null ||
                        !project.getName().equals(projectName)) {
                    setProject(projectName);
                    updateModuleList();
                }
                updateMessage();
                setPageComplete(isComplete());
            }
            private void setProject(String projectName) {
                project = null;
                for (IProject jp: getWorkspace().getRoot().getProjects()) {
                    if (jp.getName().equals(projectName)) {
                        project = jp;
                        return;
                    }
                }
            }
        });
    }

    private void updateModuleList() {
        if (project!=null) {
            modules.removeAll();
            
            for (Module module: getProjectDeclaredSourceModules(project.getProject())) {
                TableItem item = new TableItem(modules, SWT.NONE);
                item.setText(module.getNameAsString());
                item.setText(1, module.getVersion());
                item.setImage(CeylonLabelProvider.ARCHIVE);
                item.setChecked(true);
            }
            
//            modules.selectAll();
        }
    }
    
    public List<TableItem> getModules() {
        List<TableItem> list = new ArrayList<TableItem>();
        for (TableItem item: modules.getItems()) {
            if (item.getChecked()) {
                list.add(item);
            }
        }
        return list;
    }

    private boolean isComplete() {
        return project!=null &&
                isValidRepo() &&
                !getModules().isEmpty();
    }
    
    private boolean isValidRepo() {
        return repositoryPath!=null &&
                !repositoryPath.isEmpty() &&
                new File(repositoryPath).exists();
    }
    
    public String getRepositoryPath() {
        return repositoryPath;
    }
    
    public IProject getProject() {
        return project;
    }
    
    public boolean isClean() {
        return clean;
    }
    
}
