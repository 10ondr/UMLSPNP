package cz.muni.fi.umlspnp.views.sequencediagram;

import com.google.gson.annotations.Expose;
import cz.muni.fi.umlspnp.common.ElementContainer;
import cz.muni.fi.umlspnp.views.DiagramView;
import cz.muni.fi.umlspnp.views.common.BasicRectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.Group;
import javafx.scene.input.MouseButton;
import javafx.scene.shape.Path;
import javafx.scene.shape.Shape;

/**
 *  View rendering the sequence diagram pane and providing related functionality.
 *
 */
public class SequenceDiagramView extends DiagramView{
    private final Group root;
    
    @Expose(serialize = true, deserialize = false)
    private final ElementContainer<LifelineView, MessageView> allElements = new ElementContainer<>();
    private final ObjectProperty<LifelineView> highestLifelineProperty = new SimpleObjectProperty(null);

    @Expose(serialize = true, deserialize = false)
    private final ObservableMap<Number, LoopView> loopViews;
    
    public SequenceDiagramView(){
        this.root = new Group();
        
        loopViews = FXCollections.observableHashMap();
        
        diagramPane.getChildren().add(root);
        
        highestLifelineProperty.addListener(new ChangeListener(){
            @Override
            public void changed(ObservableValue ov, Object oldValue, Object newValue) {
                if(oldValue != null){
                    ((LifelineView) oldValue).setIsHighest(false);
                }
                if(newValue != null){
                    ((LifelineView) newValue).setIsHighest(true);
                }
            }
        });

        setMouseDraggedCallback(MouseButton.SECONDARY, (e) -> {
            moveAll(e.getSceneX());
        });
    }

    public Collection<LoopView> getLoopViews() {
        return loopViews.values();
    }
    
    public void moveAll(double sceneX) {
        double diffX = sceneX - originalPositionX;
        allElements.getNodes().values().forEach(node -> {
            node.setTranslateX(node.getTranslateX() + diffX);
        });
        loopViews.values().forEach(loop -> {
            loop.setTranslateX(loop.getTranslateX() + diffX);
        });
        originalPositionX = sceneX;
    }
    
    public LifelineView createLifelineView(int modelObjectID){
        var newLifelineView = new LifelineView(10, 30, 10, 0, modelObjectID);
        allElements.addNode(newLifelineView, modelObjectID);
        
        newLifelineView.localToSceneTransformProperty().addListener(new ChangeListener(){
            @Override
            public void changed(ObservableValue ov, Object oldValue, Object newValue) {
                checkHighestLifeline();
            }
        });
        
//        newLifelineView.setRestrictionsInParent(root);
        root.getChildren().add(newLifelineView);
        
        newLifelineView.changeDimensions(150, 40);
        checkHighestLifeline();
        return newLifelineView;
    }
    
    @Override
    public BasicRectangle getNode(int objectID) {
        BasicRectangle result = null;
        var lifelineView = allElements.getNode(objectID);
        if(lifelineView != null) {
            result = lifelineView;
        }
        else {
            for(var lv : allElements.getNodes().values()) {
                var activationView = lv.getActivationView(objectID);
                if(activationView != null) {
                    result = activationView;
                    break;
                }
            }
            for(var loopView : getLoopViews()) {
                if(loopView.getObjectInfo().getID() == objectID) {
                    result = loopView;
                    break;
                }
            }
        }
        return result;
    }
    
    public LifelineView getLifelineView(int objectID){
        return (LifelineView) allElements.getNode(objectID);
    }
    
    public boolean removeLifelineView(int modelObjectID){
        var removedLifline = getLifelineView(modelObjectID);
        boolean success = allElements.removeNode(modelObjectID);
        if(!success)
            return false;

        checkHighestLifeline();
        return root.getChildren().remove(removedLifline);
    }

    public ActivationView getActivationView(int objectID) {
        for(var lifeline : allElements.getNodes().values()) {
            var activationView = lifeline.getActivationView(objectID);
            if(activationView != null)
                return activationView;
        }
        return null;
    }
    
    public MessageView createMessage(ActivationView source, ActivationView destination, int messageModelID){
        var connectionSlotSource = source.getEmptySlot();
        var connectionSlotDestination = destination.getEmptySlot();
        connectionSlotDestination.disable(source == destination);

        connectionSlotSource.setSiblingVertical(connectionSlotDestination);
        connectionSlotDestination.setSiblingVertical(connectionSlotSource);

        var newMessageView = new MessageView(messageModelID, connectionSlotSource, connectionSlotDestination, source == destination, root);

        newMessageView.getArrow().getLine().boundsInLocalProperty().addListener(new ChangeListener(){
            @Override
            public void changed(ObservableValue ov, Object t, Object t1) {
                if(!newMessageView.getHovered()) {
                    for(var loopView : loopViews.values()){
                        if(processMessageLoopIntersect(newMessageView, loopView))
                            break;
                    }
                }
            }
        });
        
        allElements.addConnection(newMessageView, messageModelID);
        root.getChildren().add(newMessageView);
        newMessageView.refreshLinePosition();
        newMessageView.processMessageMoved();
        return newMessageView;
    }
    
    public MessageView createMessage(int sourceID, int destinationID, int connectionModelID){
        var source = getActivationView(sourceID);
        var destination = getActivationView(destinationID);
        return createMessage(source, destination, connectionModelID);
    }
    
    public boolean removeMessage(int messageModelID){
        var message = getConnection(messageModelID);

        if(message == null)
            return false;

        message.removeSlots();
        allElements.removeConnection(messageModelID);
        root.getChildren().remove(message);
        return true;
    }
    
    @Override
    public MessageView getConnection(int objectID){
        return allElements.getConnection(objectID);
    }
    
    public LoopView createLoop(int objectID){
        var loop = new LoopView(10, 10, 200, 50, "Loop", objectID);

        loop.addChangeListener(new ChangeListener(){
            @Override
            public void changed(ObservableValue ov, Object t, Object t1) {
                for(var message : allElements.getConnections().values()){
                    processMessageLoopIntersect(message, loop);
                }
            }
        });
        
        loopViews.put(objectID, loop);
        root.getChildren().add(loop);
        return loop;
    }
    
    public boolean removeLoop(int objectID){
        var removedLoop = loopViews.get(objectID);
        if(removedLoop == null)
            return false;

        root.getChildren().remove(removedLoop);
        boolean success = loopViews.remove(objectID) != null;
        if(success) {
            allElements.getConnections().values().forEach(messageView -> {
                processMessageAllLoopsIntersect(messageView);
            });
        }
        return success;
    }
    
    public boolean isMessageLoopIntersection(MessageView messageView, LoopView loopView){
        var shape = (Path) Shape.intersect(messageView.getArrow().getLine(), loopView.getRectangle());
        return shape.getElements().size() > 0;
    }

    private void processMessageAllLoopsIntersect(MessageView messageView) {
        for(var loopView : loopViews.values()){
            if(isMessageLoopIntersection(messageView, loopView)){
                messageView.setInLoop(loopView);
                return;
            }
        }
        messageView.setInLoop(null);
    }
    
    private boolean processMessageLoopIntersect(MessageView messageView, LoopView loopView){
        if(isMessageLoopIntersection(messageView, loopView)){
            messageView.setInLoop(loopView);
            return true;
        }
        else{
            processMessageAllLoopsIntersect(messageView);
            return false;
        }
    }

    private void checkHighestLifeline() {
        var currentHighest = highestLifelineProperty.getValue();
        
        double minX = Double.POSITIVE_INFINITY;
        LifelineView highest = null;

        for(var lifelineView : allElements.getNodes().values()) {
            double x = lifelineView.getTranslateX();
            if(x < minX) {
                minX = x;
                highest = lifelineView;
            }
        }
        if(currentHighest != highest)
            highestLifelineProperty.setValue(highest);
    }
    
    public Collection<ActivationView> sortActivations(Collection<Integer> activationObjectIDs){
        var sortedActivations = new ArrayList<ActivationView>();
        for(var activationID : activationObjectIDs){
            var activationView = this.getActivationView(activationID);
            if(activationView != null){
                sortedActivations.add(activationView);
            }
        }

        Collections.sort(sortedActivations, (a1, a2) -> {
            Double first = a1.getLocalToSceneTransform().getTy();
            Double second = a2.getLocalToSceneTransform().getTy();
            return first.compareTo(second);
        });

        return sortedActivations;
    }

    public ObjectProperty<LifelineView> getHighestLifelineProperty() {
        return highestLifelineProperty;
    }
}
