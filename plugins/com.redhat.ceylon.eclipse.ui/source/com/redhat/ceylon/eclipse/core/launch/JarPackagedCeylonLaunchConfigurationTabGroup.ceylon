import com.redhat.ceylon.eclipse.core.builder {
    CeylonNature
}
import com.redhat.ceylon.eclipse.core.launch {
    ICeylonLaunchConfigurationConstants {
        ...
    },
    LaunchHelper {
        ...
    },
    SWTFactory {
        ...
    }
}
import com.redhat.ceylon.eclipse.ui {
    CeylonPlugin {
        pluginId
    },
    CeylonResources
}
import com.redhat.ceylon.model.typechecker.model {
    Module {
        defaultModuleName
    }
}

import java.lang {
    Types,
    ObjectArray
}

import org.eclipse.core.runtime {
    CoreException
}
import org.eclipse.debug.core {
    ILaunchConfiguration,
    ILaunchConfigurationWorkingCopy
}
import org.eclipse.debug.ui {
    AbstractLaunchConfigurationTabGroup,
    ILaunchConfigurationDialog,
    EnvironmentTab,
    CommonTab,
    ILaunchConfigurationTab
}
import org.eclipse.jdt.debug.ui.launchConfigurations {
    JavaArgumentsTab,
    JavaJRETab
}
import org.eclipse.jdt.internal.debug.ui {
    IJavaDebugHelpContextIds {
        launchConfigurationDialogMainTab
    },
    JDIDebugUIPlugin
}
import org.eclipse.jdt.internal.debug.ui.actions {
    ControlAccessibleListener
}
import org.eclipse.jdt.internal.debug.ui.launcher {
    AbstractJavaMainTab,
    LauncherMessages
}
import org.eclipse.jdt.launching {
    IJavaLaunchConfigurationConstants {
        ...
    }
}
import org.eclipse.swt {
    SWT
}
import org.eclipse.swt.events {
    ModifyEvent,
    ModifyListener,
    SelectionEvent,
    SelectionListener
}
import org.eclipse.swt.layout {
    GridData,
    GridLayout
}
import org.eclipse.swt.widgets {
    Button,
    Composite,
    Text,
    Combo
}
import org.eclipse.ui {
    PlatformUI {
        workbench
    }
}

shared class JarPackagedCeylonLaunchConfigurationTabGroup() 
        extends AbstractLaunchConfigurationTabGroup() {
    createTabs(ILaunchConfigurationDialog d, String s) 
            => setTabs(ObjectArray<ILaunchConfigurationTab>.with {
                CeylonModuleOnlyTab(),
                JavaArgumentsTab(),
                JavaJRETab(),
                EnvironmentTab(),
                CommonTab()            
             });
}

shared class CeylonModuleOnlyTab() extends AbstractJavaMainTab() {
    variable late Text fModuleText;
    variable late Combo fJarCreationToolText;
    variable late Button fModuleSearchButton;
    variable late Button fStopInMainCheckButton;
    
    shared actual void createControl(Composite parent) {
        value comp = createComposite(parent, parent.font, 1, 1, GridData.fillBoth);
        assert(is GridLayout gl = comp.layout);
        gl.verticalSpacing = 0;
        createProjectEditor(comp);
        createVerticalSpacer(comp, 1);
        createModuleEditor(comp, "Module:");
        createVerticalSpacer(comp, 1);
        createToolSelector(comp, "Jar packaging type:");
        setControl(comp);
        workbench.helpSystem.setHelp(control, launchConfigurationDialogMainTab);
    }
    
    image => CeylonResources.\imodule;
    
    name => "Module";
    
    id => "``pluginId``.ceylonModuleOnlyTab";
    
    void handleModuleSearchButtonSelected() {
        if (exists mod = chooseModule(getProjectFromName(this.fProjText.text), true)) {
            if (mod.defaultModule) {
                fModuleText.text = defaultModuleName;
            } else {
                fModuleText.text = getFullModuleName(mod);
            }
        }
    }
    
    shared actual void initializeFrom(ILaunchConfiguration config) {
        super.initializeFrom(config);
        try {
            fModuleText.text = config.getAttribute(attrModuleName, "");
            value toolName = config.getAttribute(attrJarCreationToolName, "");
            if (toolName.empty) {
                fJarCreationToolText.select(-1);
            } else {
                if (exists index = jarCreationTools.firstIndexWhere((tool) 
                    => tool.type == toolName)) {
                    fJarCreationToolText.select(index);
                }
            }
            updateStopInMainFromConfig(config);
        } catch (CoreException ce) {
            JDIDebugUIPlugin.log(ce);
        }
    }
    
    shared actual Boolean isValid(ILaunchConfiguration config) {
        setErrorMessage(null);
        setMessage(null);
        value projectName = fProjText.text.trim(' '.equals);
        value project = getProjectFromName(projectName);
        if (exists project) {
            if (!project.\iexists()) {
                setErrorMessage("The project " + projectName + " does no exist.");
                return false;
            }
            
            if (!project.open) {
                setErrorMessage("The project " + projectName + " is not opened");
                return false;
            }
            
            if (!CeylonNature.isEnabled(project)) {
                setErrorMessage("The project " + projectName + " is not a Ceylon project");
                return false;
            }
        } else {
            return false;
        }
        
        value moduleName = fModuleText.text.trim(' '.equals);
        if (moduleName.empty) {
            setErrorMessage("The Ceylon module is not specified");
            return false;
        }
        
        if (!isModuleInProject(project, moduleName)) {
            setErrorMessage("Ceylon module not found in project");
            return false;
        }

        value tool = jarCreationToolsMap[fJarCreationToolText.text.trim(' '.equals)];
        if (! exists tool) {
            setErrorMessage("No valid Jar creation tool was selected");
            return false;
        }
        
        return true;
    }
    
    shared actual void performApply(ILaunchConfigurationWorkingCopy config) {
        config.setAttribute(attrProjectName, fProjText.text.trim(' '.equals));
        config.setAttribute(attrModuleName, fModuleText.text.trim(' '.equals));
        config.setAttribute(attrJarCreationToolName, fJarCreationToolText.text.trim(' '.equals));
        mapResources(config);
        if (fStopInMainCheckButton.selection) {
            config.setAttribute(attrStopInMain, true);
        } else {
            config.setAttribute(attrStopInMain, (null of String?));
        }
        config.rename(getLaunchConfigurationName(config));
    }
    
    shared actual void setDefaults(ILaunchConfigurationWorkingCopy config) {
        value javaElement = context;
        if (exists javaElement) {
            initializeJavaProject(javaElement, config);
        } else {
            config.setAttribute(attrProjectName, "");
        }
        
        variable String moduleName = "";
        if (!exists javaElement) {
            return;
        }
        
        variable Module? theModule = null;
        if (context.javaProject.\iexists()) {
            value theModules = [
                for (m in getModules(
                    context.javaProject.project, true)) m ];
            if (exists first = theModules.first,
                ! theModules.rest.any((m)=> ! m.nameAsString.startsWith("test."))){
                theModule = first;
            }
        }
        if (exists m = theModule) {
            moduleName = getFullModuleName(m);
        }
        
        config.setAttribute(attrModuleName, moduleName);
        config.setAttribute(attrJarCreationToolName, jarCreationTools[0].type);
        
        config.rename(getLaunchConfigurationName(config));
    }
    
    void createModuleEditor(Composite parent, String text) {
        value group = createGroup(parent, text, 2, 1, GridData.fillHorizontal);
        fModuleText = createSingleText(group, 1);
        fModuleText.addModifyListener(object satisfies ModifyListener {
                shared actual void modifyText(ModifyEvent e) {
                    updateLaunchConfigurationDialog();
                }
            }
            
        );
        ControlAccessibleListener.addListener(fModuleText, group.text);
        fModuleSearchButton = createPushButton(group, LauncherMessages.abstractJavaMainTab_2, null);
        fModuleSearchButton.addSelectionListener(object satisfies SelectionListener {
                widgetDefaultSelected(SelectionEvent e) 
                        => noop();
                widgetSelected(SelectionEvent e)
                        => handleModuleSearchButtonSelected();
            }
            
        );
    }
    
    void createToolSelector(Composite parent, String text) {
        value group = createGroup(parent, text, 2, 1, GridData.fillHorizontal);
        fJarCreationToolText = createCombo(group, SWT.none, 2, 
            ObjectArray.with { for (t in jarCreationTools) Types.nativeString(t.type) });

        value canStopInMain 
                => jarCreationTools[fJarCreationToolText.selectionIndex]?.canStopInMain
                else false;

        fJarCreationToolText.addSelectionListener(object satisfies SelectionListener {
            shared actual void widgetDefaultSelected(SelectionEvent selectionEvent) {
                fStopInMainCheckButton.enabled = canStopInMain;
                fStopInMainCheckButton.selection &&= fStopInMainCheckButton.enabled;
                updateLaunchConfigurationDialog();
                
            }
            
            shared actual void widgetSelected(SelectionEvent selectionEvent) {
                fStopInMainCheckButton.enabled = 
                        jarCreationTools[fJarCreationToolText.selectionIndex]?.canStopInMain
                        else false;
                fStopInMainCheckButton.enabled = canStopInMain;
                fStopInMainCheckButton.selection &&= fStopInMainCheckButton.enabled;
                updateLaunchConfigurationDialog();
            }
        });
        
        ControlAccessibleListener.addListener(fModuleText, group.text);
        value checks = createComposite(group, 2, 2, GridData.beginning);
        fStopInMainCheckButton = createCheckButton(checks, "St&op inside");
        fStopInMainCheckButton.addSelectionListener(defaultListener);
    }

        
    void updateStopInMainFromConfig(ILaunchConfiguration config) {
        variable value stop = false;
        try {
            stop = config.getAttribute(attrStopInMain, false);
        } catch (CoreException e) {
            JDIDebugUIPlugin.log(e);
        }
        
        fStopInMainCheckButton.selection = stop;
    }
}
