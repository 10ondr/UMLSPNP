package cz.muni.fi.umlspnp.controllers.deploymentdiagram;

import cz.muni.fi.umlspnp.common.Utils;
import cz.muni.fi.umlspnp.controllers.BaseController;
import cz.muni.fi.umlspnp.models.ConnectionFailure;
import cz.muni.fi.umlspnp.models.MainModel;
import cz.muni.fi.umlspnp.models.deploymentdiagram.CommunicationLink;
import cz.muni.fi.umlspnp.models.deploymentdiagram.LinkType;
import cz.muni.fi.umlspnp.views.MainView;
import cz.muni.fi.umlspnp.views.common.layouts.EditFailureTypeModalWindow;
import cz.muni.fi.umlspnp.views.common.layouts.EditableListView;
import cz.muni.fi.umlspnp.views.deploymentdiagram.CommunicationLinkView;
import cz.muni.fi.umlspnp.views.deploymentdiagram.EditAllLinkTypesModalWindow;
import java.util.ArrayList;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.stage.Stage;

/**
 *  Controller which handles all functionalities within the communication link
 * and provides a model-view binding.
 *
 */
public class CommunicationLinkController extends BaseController<CommunicationLink, CommunicationLinkView>{
    
    public CommunicationLinkController(MainModel mainModel,
                                       MainView mainView,
                                       CommunicationLink model,
                                       CommunicationLinkView view) {
        super(mainModel, mainView, model, view);
        
        communicationLinkMenuInit();
        communicationLinkAnnotationsInit();
    }

    private void communicationLinkMenuInit(){
        view.clearMenuItems();
        var deploymentDiagram = this.mainModel.getDeploymentDiagram();
        var communicationLinkObjectID = model.getObjectInfo().getID();

        view.createConfirmMenu("Delete connection", "The communication link will be deleted. Proceed?",
                                                () -> {deploymentDiagram.removeCommunicationLink(communicationLinkObjectID);});

        view.createToggleAnnotationsMenu();
        view.createMenuSeparator();
        
        var linkTypesView = createLinkTypeProperties();
        var failuresView = createFailureTypesProperties();
        ArrayList<EditableListView> sections = new ArrayList<>();
        sections.add(linkTypesView);
        sections.add(failuresView);
        var propertiesWindowName = String.format("Communication link properties");
        view.createPropertiesMenu(propertiesWindowName, sections);
    }

    private EditableListView createLinkTypeProperties(){
        var deploymentDiagram = mainModel.getDeploymentDiagram();
        var linkTypes = deploymentDiagram.getAllLinkTypes();
        var linkTypesView = new EditableListView("Link type:", linkTypes);
        
        var selectBtnHandler = (EventHandler<ActionEvent>) (ActionEvent e) -> {
            var selected = (LinkType) linkTypesView.getSelected();
            if(selected != null){
                model.setLinkType(selected);
            }
        };
        
        var addBtnHandler = (EventHandler<ActionEvent>) (ActionEvent e) -> {
            deploymentDiagram.createLinkType("New link type", 1.0);
        };

        var removeBtnHandler = (EventHandler<ActionEvent>) (ActionEvent e) -> {
            var selected = (LinkType) linkTypesView.getSelected();
            if(selected != null){
                var windowName = "Confirm";
                var promptText = String.format("The link type \"%s\" will be deleted. Proceed?", Utils.shortenString(selected.toString(), 50));
                Runnable callback = () -> {
                    deploymentDiagram.removeLinkType(selected);
                };
                view.createBooleanModalWindow(windowName, promptText, callback, null);
            }
        };

        var editBtnHandler = (EventHandler<ActionEvent>) (ActionEvent e) -> {
            var selected = (LinkType) linkTypesView.getSelected();
            if(selected != null){
                var editWindow = new EditAllLinkTypesModalWindow((Stage) linkTypesView.getScene().getWindow(),
                        "Edit link type",
                        selected.nameProperty(),
                        selected.rateProperty());
                editWindow.showAndWait();
                linkTypesView.refresh();
            }
        };

        linkTypesView.createButton("Select", selectBtnHandler, true);
        linkTypesView.createButton("Add", addBtnHandler, false);
        
        var removeButton = linkTypesView.createButton("Remove", removeBtnHandler, false);
        removeButton.disableProperty().bind(Bindings.size(linkTypes).lessThan(2));
        
        linkTypesView.createButton("Edit", editBtnHandler, true);
        
        return linkTypesView;
    }

    private EditableListView createFailureTypesProperties(){
        var failures = model.getLinkFailures();
        var failuresView = new EditableListView("Failure types:", failures);

        var addBtnHandler = (EventHandler<ActionEvent>) (ActionEvent e) -> {
            model.addLinkFailure(new ConnectionFailure("New failure", 0.01));
        };

        var removeBtnHandler = (EventHandler<ActionEvent>) (ActionEvent e) -> {
            var selected = (ConnectionFailure) failuresView.getSelected();
            if(selected != null){
                var windowName = "Confirm";
                var promptText = String.format("The failure type \"%s\" will be deleted. Proceed?", Utils.shortenString(selected.toString(), 50));
                Runnable callback = () -> {
                    failures.remove(selected);
                };
                view.createBooleanModalWindow(windowName, promptText, callback, null);
            }
        };

        var editBtnHandler = (EventHandler<ActionEvent>) (ActionEvent e) -> {
            var selected = (ConnectionFailure) failuresView.getSelected();
            if(selected != null){
                var editWindow = new EditFailureTypeModalWindow(   (Stage) failuresView.getScene().getWindow(),
                        "Edit failure type",
                        selected.nameProperty(),
                        selected.rateProperty());
                editWindow.showAndWait();
                failuresView.refresh();
            }
        };

        failuresView.createButton("Add", addBtnHandler, false);
        failuresView.createButton("Remove", removeBtnHandler, true);
        failuresView.createButton("Edit", editBtnHandler, true);
        
        return failuresView;
    }

    private void communicationLinkAnnotationsInit(){
        view.getLinkTypeAnnotation().setItems(model.getLinkTypeList());
        view.getLinkFailuresAnnotation().setItems(model.getLinkFailures());
    }
}
