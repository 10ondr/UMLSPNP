/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.umlspnp.views.common;

import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;

/**
 *
 * @author 10ondr
 */
public class NamedRectangle extends BasicRectangle{
    private final Label nameLabel;
    
    public NamedRectangle(double x, double y, double width, double height, String name) {
        super(x, y, width, height);
        
        nameLabel = new Label(name);
        nameLabel.translateXProperty().bind(rect.translateXProperty().add(rect.widthProperty().divide(2)).subtract(nameLabel.widthProperty().divide(2)));
        nameLabel.translateYProperty().bind(rect.yProperty());
        
        this.getChildren().add(nameLabel);

    }
    
    public void setName(String newName){
        nameLabel.setText(newName);
    }
    
    public String getName(){
        return nameLabel.getText();
    }
    
    public void bindLabelTo(StringProperty name){
        this.nameLabel.textProperty().bind(name);
    }
}