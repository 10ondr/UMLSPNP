package cz.muni.fi.umlspnp.controllers.deploymentdiagram;

import cz.muni.fi.umlspnp.views.MainView;
import cz.muni.fi.umlspnp.models.deploymentdiagram.DeploymentDiagram;
import cz.muni.fi.umlspnp.models.deploymentdiagram.CommunicationLink;
import cz.muni.fi.umlspnp.models.deploymentdiagram.Artifact;
import cz.muni.fi.umlspnp.models.deploymentdiagram.RedundancyGroup;
import cz.muni.fi.umlspnp.models.deploymentdiagram.DeploymentTarget;
import cz.muni.fi.umlspnp.models.MainModel;
import cz.muni.fi.umlspnp.common.Utils;
import cz.muni.fi.umlspnp.controllers.BaseController;
import cz.muni.fi.umlspnp.models.OperationType;
import cz.muni.fi.umlspnp.views.common.layouts.BooleanModalWindow;
import cz.muni.fi.umlspnp.views.common.layouts.EditableListView;
import cz.muni.fi.umlspnp.views.deploymentdiagram.DeploymentDiagramView;
import cz.muni.fi.umlspnp.views.deploymentdiagram.DeploymentTargetView;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;

/**
 *  Controller which handles all functionalities within the deployment diagram
 *  and provides a model-view binding.
 *
 */
public class DeploymentDiagramController extends BaseController<DeploymentDiagram, DeploymentDiagramView>{
    private final List<CommunicationLinkController> communicationLinkControllers;
    private final List<DeploymentTargetController> deploymentTargetControllers;
    private final List<ArtifactController> artifactControllers;
    
    public DeploymentDiagramController(MainModel mainModel, MainView mainView){
        super(mainModel, mainView, mainModel.getDeploymentDiagram(), mainView.getDeploymentDiagramView());

        communicationLinkControllers = new ArrayList<>();
        deploymentTargetControllers = new ArrayList<>();
        artifactControllers = new ArrayList<>();

        // Creation and removal of artifacts and deployment targets
        nodeManagerInit();
        
        // Creation and removal of communication links
        communicationLinkManagerInit();
        
        // Connection container for connecting two deployment targets with a communication link
        connectionContainerInit();
        
        // Node menu
        nodeMenuInit();
        
        // Global properties menu
        globalMenuInit();
    }

    private void connectionContainerInit() {
        var connectionContainer = view.getConnectionContainer();
        connectionContainer.connectionProperty().addListener(new ChangeListener(){
            @Override
            public void changed(ObservableValue ov, Object oldValue, Object newValue) {
                if(newValue == null)
                    return;
                
                var firstElementID = connectionContainer.getFirstElementID();
                var secondElementID = connectionContainer.getSecondElementID();
                if(firstElementID != null){
                    var firstDTView = view.getDeploymentTargetView(firstElementID.intValue());
                    firstDTView.setSelected(true);
                    
                    if(secondElementID != null){
                        if(connectionContainer.getFirstElement() instanceof DeploymentTargetView){
                            var firstDT = model.getDeploymentTarget(firstElementID.intValue());
                            var secondDT = model.getDeploymentTarget(secondElementID.intValue());
                            if(model.areNodesConnected(firstDT, secondDT)){
                                System.err.println(String.format(
                                        "Error: Nodes \"%s\" and \"%s\" are already connected!",
                                        firstDT.getNameProperty().getValue(),
                                        secondDT.getNameProperty().getValue()));
                            }
                            else{
                                model.createCommunicationLink(firstDT, secondDT);
                            }
                        }
                        firstDTView.setSelected(false);
                        connectionContainer.clear();
                    }
                }
            }
        });
    }
    
    private void communicationLinkManagerInit() {
        model.addCommunicationLinksChangeListener(new MapChangeListener(){
            @Override
            public void onChanged(MapChangeListener.Change change) {
                if(change.wasAdded()){
                    if(change.getValueAdded() instanceof CommunicationLink) {
                        var newConnection = (CommunicationLink) change.getValueAdded();
                        var firstID = newConnection.getFirst().getObjectInfo().getID();
                        var secondID = newConnection.getSecond().getObjectInfo().getID();
                        var newConnectionView = view.createConnection(firstID, secondID, newConnection.getObjectInfo().getID());

                        var controller = new CommunicationLinkController(mainModel, mainView, newConnection, newConnectionView);
                        communicationLinkControllers.add(controller);
                    }
                }
                if(change.wasRemoved()){
                    if(change.getValueRemoved() instanceof CommunicationLink) {
                        var removedConnection = (CommunicationLink) change.getValueRemoved();
                        view.removeConnection(removedConnection.getObjectInfo().getID());
                        communicationLinkControllers.removeIf((controller) -> controller.getModel().equals(removedConnection));
                    }
                }
            }
        });
    }
    
    private void nodeManagerInit() {
        model.addAllNodesChangeListener(new MapChangeListener(){
            @Override
            public void onChanged(MapChangeListener.Change change) {
                if(change.wasAdded()){
                    var newNode = change.getValueAdded();
                    if(newNode instanceof DeploymentTarget){
                        var newDT = (DeploymentTarget) newNode;
                        
                        DeploymentTargetView newDTParent = null;
                        if(newDT.getParent() != null)
                            newDTParent = view.getDeploymentTargetView(newDT.getParent().getObjectInfo().getID());

                        var newDTView = view.createDeploymentTargetView(newDTParent, newDT.getObjectInfo().getID());
                        var controller = new DeploymentTargetController(mainModel, mainView, newDT, newDTView);
                        deploymentTargetControllers.add(controller);
                    }
                    else if(newNode instanceof Artifact){
                        Artifact newArtifact = (Artifact) newNode;
                        var newArtifactParent = view.getDeploymentTargetView(newArtifact.getParent().getObjectInfo().getID());
                        var newArtifactView = view.CreateArtifact(newArtifactParent, newArtifact.getObjectInfo().getID());
                        
                        var controller = new ArtifactController(mainModel, mainView, newArtifact, newArtifactView);
                        artifactControllers.add(controller);
                    }
                }
                else if(change.wasRemoved()){
                    var removedItem = change.getValueRemoved();
                    if(removedItem instanceof DeploymentTarget) {
                        deploymentTargetControllers.removeIf(controller -> controller.getModel().equals(removedItem));
                    }
                    else if(removedItem instanceof Artifact) {
                        artifactControllers.removeIf(controller -> controller.getModel().equals(removedItem));
                    }
                    var removedNode = (Artifact) change.getValueRemoved();
                    view.removeNode(removedNode.getObjectInfo().getID());
                }
            }
        });
    }
    
    private void nodeMenuInit() {
        Menu addNodeMenu = new Menu("Add Node");
        MenuItem deviceMenuItem = new MenuItem("Deployment target");
        
        EventHandler<ActionEvent> menuEventHandler = (ActionEvent tt) -> {
            if(tt.getSource().equals(deviceMenuItem)) {
                var dt = model.createDeploymentTarget(null);
                dt.createInitialData();
            }
        };
        
        deviceMenuItem.setOnAction(menuEventHandler);
        addNodeMenu.getItems().addAll(deviceMenuItem);
        view.addMenu(addNodeMenu);
    }
    
    private void globalMenuInit() {
        Menu globalMenu = new Menu("Global");
        MenuItem operationTypesMenuItem = new MenuItem("Operation types");
        operationTypesMenuItem.setOnAction((e) -> {
            var operationTypesView = createOperationTypesView();
            ArrayList<EditableListView> sections = new ArrayList();
            sections.add(operationTypesView);

            this.mainView.createPropertiesModalWindow("Operation types", sections);
        });
        globalMenu.getItems().addAll(operationTypesMenuItem);

        MenuItem redundancyGroupsMenuItem = new MenuItem("Redundancy groups");
        redundancyGroupsMenuItem.setOnAction((e) -> {
            var redundancyGroupsView = createRedundancyGroupsView();
            ArrayList<EditableListView> sections = new ArrayList();
            sections.add(redundancyGroupsView);

            this.mainView.createPropertiesModalWindow("Redundancy groups", sections);
        });
        globalMenu.getItems().addAll(redundancyGroupsMenuItem);
        
        view.addMenu(globalMenu);
        
        // Remove corresponding annotation data when the operation type is deleted
        globalOperationTypesInit();
    }
    
    private void globalOperationTypesInit() {
        var allOperationTypes = model.getOperationTypes();
        allOperationTypes.addListener(new ListChangeListener(){
            @Override
            public void onChanged(ListChangeListener.Change change) {
                while (change.next()) {
                    if (change.wasRemoved()) {
                        change.getRemoved().forEach(removedItem -> {
                            model.getNodes().forEach(node -> {
                                if(node instanceof DeploymentTarget) {
                                    ((DeploymentTarget) node).getStateOperations().forEach(stateOperation -> {
                                        stateOperation.getOperationEntries().removeIf(operationEntry -> 
                                                removedItem.equals(operationEntry.getOperationType()));
                                    });
                                }
                            });
                        });
                    }
                }
            }
        });
    }
    
    private EditableListView createOperationTypesView(){
        var operationTypes = model.getOperationTypes();
        var operationTypesView = new EditableListView("Operation Types:", operationTypes);
        
        var addBtnHandler = (EventHandler<ActionEvent>) (ActionEvent e) -> {
            model.addOperationType(new OperationType("New operation"));
        };

        var removeBtnHandler = (EventHandler<ActionEvent>) (ActionEvent e) -> {
            var selected = (OperationType) operationTypesView.getSelected();
            if(selected != null){
                var promptText = String.format("The operation type \"%s\" will be deleted. Proceed?",
                                               Utils.shortenString(selected.toString(), 50));
                BooleanModalWindow confirmWindow = new BooleanModalWindow(
                                                    (Stage) operationTypesView.getScene().getWindow(),
                                                    "Confirm",
                                                    promptText);
                confirmWindow.showAndWait();
                if(confirmWindow.getResult()){
                    model.removeOperationType(selected);
                }
            }
        };

        var editBtnHandler = (EventHandler<ActionEvent>) (ActionEvent e) -> {
            var selected = (OperationType) operationTypesView.getSelected();
            mainView.createStringModalWindow("Rename", "New name", selected.nameProperty(), null);
            operationTypesView.refresh();
        };
        
        operationTypesView.createButton("Add", addBtnHandler, false);
        operationTypesView.createButton("Remove", removeBtnHandler, true);
        operationTypesView.createButton("Edit", editBtnHandler, true);
        return operationTypesView;
    }

    private EditableListView createRedundancyGroupsView(){
        var redundancyGroups = model.getRedundancyGroups();
        var redundancyGroupsView = new EditableListView("Redundancy Groups:", redundancyGroups);
        
        var addBtnHandler = (EventHandler<ActionEvent>) (ActionEvent e) -> {
            model.createRedundancyGroup();
        };

        var removeBtnHandler = (EventHandler<ActionEvent>) (ActionEvent e) -> {
            var selected = (RedundancyGroup) redundancyGroupsView.getSelected();
            if(selected != null){
                var promptText = String.format("The redundancy group \"%s\" will be deleted. Proceed?",
                                               Utils.shortenString(selected.toString(), 50));
                BooleanModalWindow confirmWindow = new BooleanModalWindow(
                                                        (Stage) redundancyGroupsView.getScene().getWindow(),
                                                        "Confirm",
                                                        promptText);
                confirmWindow.showAndWait();
                if(confirmWindow.getResult()){
                    model.removeRedundancyGroup(selected);
                }
            }
        };

        redundancyGroupsView.createButton("Add", addBtnHandler, false);
        redundancyGroupsView.createButton("Remove", removeBtnHandler, true);
        return redundancyGroupsView;
    }
}
