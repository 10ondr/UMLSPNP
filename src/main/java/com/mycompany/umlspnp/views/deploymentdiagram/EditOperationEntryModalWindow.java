/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.umlspnp.views.deploymentdiagram;

import com.mycompany.umlspnp.views.common.layouts.ModalWindow;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 *
 * @author 10ondr
 */
public class EditOperationEntryModalWindow extends ModalWindow{
    private final TextField nameInput;
    private final TextField speedInput;
    private final CheckBox speedEnabled = new CheckBox();
    
    private final Button confirmButton;
    
    public EditOperationEntryModalWindow(Stage parentStage, String windowName, StringProperty entryName, IntegerProperty entryProcessingSpeed) {
        super(parentStage, windowName);
        
        var nameLabel = new Label("Operation name:");
        this.nameInput = new TextField(entryName.getValue());
        
        var speedEnabledLabel = new Label("Limit processing speed");
  
        var speedLabel = new Label("Processing speed [%]:");
        
        Integer speedLimit = entryProcessingSpeed.getValue();
        if(speedLimit < 0){
            this.speedEnabled.setSelected(false);
            this.speedInput = new TextField("100");
        }
        else{
            this.speedEnabled.setSelected(true);
            this.speedInput = new TextField(speedLimit.toString());
        }
        speedLabel.visibleProperty().bind(this.speedEnabled.selectedProperty());
        this.speedInput.visibleProperty().bind(this.speedEnabled.selectedProperty());

        this.confirmButton = new Button("Confirm");
        this.confirmButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                if(checkInputs()){
                    entryName.setValue(nameInput.textProperty().getValue());
                    entryProcessingSpeed.setValue(parseProcessingSpeedValue());
                    close();
                }
            }
        });
        
        this.rootGrid.add(nameLabel, 0, 0);
        this.rootGrid.add(nameInput, 1, 0);
        
        this.rootGrid.add(speedEnabled, 0, 1);
        this.rootGrid.add(speedEnabledLabel, 1, 1);
        
        this.rootGrid.add(speedLabel, 0, 2);
        this.rootGrid.add(speedInput, 1, 2);
        
        this.rootGrid.add(confirmButton, 0, 3);
        
        // TODO: make responsive
        this.setWidth(300);
        this.setHeight(200);
    }
    
    private Integer parseProcessingSpeedValue(){
        if(this.speedEnabled.isSelected())
            return Integer.parseInt(speedInput.textProperty().getValue());
        return -1;
    }

    private boolean checkInputs(){
        String errorMessage = null;
        if(this.nameInput.textProperty().isEmpty().getValue()){
            errorMessage = "Name is not valid.";
        }
        
        try {
            int speedLimit = parseProcessingSpeedValue();
            if(speedEnabled.isSelected() && (speedLimit > 100 || speedLimit < 0)){
                errorMessage = "Speed limit is out of range (0% to 100%).";
            }
        }
        catch(Exception e) {
            errorMessage = "Error while parsing speed limit value.";
        }
        
        if(errorMessage != null){
            Alert errorDialog = new Alert(Alert.AlertType.ERROR);
            errorDialog.setTitle("Input error");
            errorDialog.setHeaderText("Incorrect values!");
            errorDialog.setContentText(errorMessage);
            errorDialog.showAndWait();
            return false;
        }
        return true;
    }
    
}