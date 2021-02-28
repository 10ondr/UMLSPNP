/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.umlspnp.views.deploymentdiagram;

import com.mycompany.umlspnp.views.common.Annotation;
import com.mycompany.umlspnp.views.common.AnnotationOwner;
import com.mycompany.umlspnp.views.common.ConnectionView;
import com.mycompany.umlspnp.views.common.ConnectionSlot;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.paint.Color;

/**
 *
 * @author 10ondr
 */
public class CommunicationLinkView extends ConnectionView implements AnnotationOwner {
    private final Annotation linkTypeAnnotation;
    private final Annotation linkFailuresAnnotation;
    
    private boolean annotationsDisplayed = true;

    public CommunicationLinkView(int modelObjectID, ConnectionSlot source, ConnectionSlot destination, Group diagramRoot){
        super(modelObjectID, source, destination, diagramRoot, false);

        DoubleProperty lineCenterX = new SimpleDoubleProperty();
        DoubleProperty lineCenterY = new SimpleDoubleProperty();
        
        arrow.getLine().layoutBoundsProperty().addListener(new ChangeListener(){
            @Override
            public void changed(ObservableValue ov, Object oldValue, Object newValue) {
                var newLineCenter = (Bounds) newValue;
                lineCenterX.set(newLineCenter.getCenterX());
                lineCenterY.set(newLineCenter.getCenterY());
            }
        });
        
        linkTypeAnnotation = new Annotation(250, 10, lineCenterX, lineCenterY, "Communication Link");
        linkTypeAnnotation.setFill(Color.LIGHTGREEN);
        
        linkFailuresAnnotation = new Annotation(250, 100, lineCenterX, lineCenterY, "Failure Types");
        linkFailuresAnnotation.setFill(Color.LIGHTCORAL);
        
        annotationInit(linkTypeAnnotation);
        annotationInit(linkFailuresAnnotation);
    }
    
    public Annotation getLinkTypeAnnotation(){
        return linkTypeAnnotation;
    }
    
    public Annotation getLinkFailuresAnnotation(){
        return linkFailuresAnnotation;
    }
    
    private void annotationInit(Annotation newAnnotation){
        arrow.getLine().layoutBoundsProperty().addListener(new ChangeListener(){
            @Override
            public void changed(ObservableValue ov, Object oldValue, Object newValue) {
                var annotationLine = newAnnotation.getLine();

                var oldLineCenter = (Bounds) oldValue;
                var newLineCenter = (Bounds) newValue;
                
                annotationLine.setEndX(newLineCenter.getCenterX());
                annotationLine.setEndY(newLineCenter.getCenterY());
                
                newAnnotation.setTranslateX(newAnnotation.getTranslateX() + (newLineCenter.getCenterX() - oldLineCenter.getCenterX()));
                newAnnotation.setTranslateY(newAnnotation.getTranslateY() + (newLineCenter.getCenterY() - oldLineCenter.getCenterY()));
            }
        });

        getChildren().add(newAnnotation);
        getChildren().add(newAnnotation.getLine());
        newAnnotation.getLine().toBack();
        
        newAnnotation.setRestrictionsInParent(diagramRoot);
    }
    
    @Override
    public void setAnnotationsDisplayed(boolean value){
        if(annotationsDisplayed != value){
            annotationsDisplayed = value;

            linkTypeAnnotation.setDisplayed(value);
            linkFailuresAnnotation.setDisplayed(value);
        }
    }

    @Override
    public boolean areAnnotationsDisplayed(){
        return annotationsDisplayed;
    }
}
