/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.umlspnp.views.common;

import javafx.beans.binding.DoubleExpression;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;

/**
 *
 * @author 10ondr
 */
public class Annotation extends NamedRectangle {
    private final VBox items = new VBox();
    private final Line line = new Line();
    
    public Annotation(double x, double y, double width, double height, DoubleExpression targetX, DoubleExpression targetY, String name) {
        super(x, y, width, height, name, -1);
        
        this.setBoldHeader(true);

        line.getStrokeDashArray().addAll(5.0, 5.0);
        
        line.startXProperty().bind(this.translateXProperty().add(this.widthProperty().divide(2)));
        line.startYProperty().bind(this.translateYProperty().add(this.heightProperty().divide(2)));

        line.endXProperty().bind(targetX);
        line.endYProperty().bind(targetY);
        
        this.setRestrictionsInParent(null);
        
        items.setTranslateX(5);
        items.setTranslateY(20);
        this.getChildren().add(items);
    }

    public Line getLine(){
        return this.line;
    }
    
    private Node getItem(String itemString){
        for(var item : items.getChildren()){
            Label label = (Label) item;
            if(label.getText().equals(itemString)){
                return item;
            }
        }
        return null;
    }

    public void addItem(String newItem){
        items.getChildren().add(new Label(newItem));
    }
    
    public boolean updateItem(String oldItem, String newItem){
        Node item = getItem(oldItem);
        if(item == null)
            return false;
        Label label = (Label) item;
        label.setText(newItem);
        return true;
    }
    
    public boolean removeItem(String removedItem){
        Node item = getItem(removedItem);
        if(item == null)
            return false;
        return items.getChildren().remove(item);
    }
}
