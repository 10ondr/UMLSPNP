package cz.muni.fi.umlspnp.transformations;

import cz.muni.fi.umlspnp.models.sequencediagram.Loop;
import cz.muni.fi.spnp.core.models.PetriNet;
import cz.muni.fi.spnp.core.models.arcs.ArcDirection;
import cz.muni.fi.spnp.core.models.arcs.InhibitorArc;
import cz.muni.fi.spnp.core.models.arcs.StandardArc;
import cz.muni.fi.spnp.core.models.functions.FunctionType;
import cz.muni.fi.spnp.core.models.places.StandardPlace;
import cz.muni.fi.spnp.core.models.transitions.ImmediateTransition;
import cz.muni.fi.spnp.core.models.transitions.TimedTransition;
import cz.muni.fi.spnp.core.models.transitions.Transition;
import cz.muni.fi.spnp.core.models.transitions.probabilities.ConstantTransitionProbability;
import cz.muni.fi.spnp.core.transformators.spnp.code.FunctionSPNP;
import cz.muni.fi.spnp.core.transformators.spnp.distributions.ExponentialTransitionDistribution;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *  The loop segment which spans multiple control service segment places.
 *
 */
public class LoopSegment extends Segment{
    private final String commentPrefix;
    private final ControlServiceSegment controlServiceSegment;
    private final ServiceCallTreeNode highestTreeNode;
    private final Loop loop;
    private ServiceCall highestControlServiceCall = null;
    private final List<ServiceCall> controlServiceCalls = new ArrayList<>();

    private ImmediateTransition flushTransition = null;
    private StandardPlace flushPlace = null;
    
    private Transition restartTransition = null;
    private StandardPlace repeatsPlace = null;
    
    public LoopSegment( PetriNet petriNet,
                        boolean generateComments,
                        ControlServiceSegment controlServiceSegment,
                        ServiceCallTreeNode treeNode,
                        Loop loop) {
        super(petriNet, generateComments);
        
        this.controlServiceSegment = controlServiceSegment;
        this.highestTreeNode = treeNode;
        this.loop = loop;
        
        this.commentPrefix = String.format("Loop segment [iterations: %d rate: %.4f]", loop.getIterations(), loop.getRestartRate());
    }

    private void resolveControlServiceCalls(ServiceCallTreeNode serviceCallNode) {
        var message = serviceCallNode.getMessage();

        if(!serviceCallNode.isMarkedForLoopCheck()) {
            var serviceCalls = controlServiceSegment.getControlServiceCalls(message);
            controlServiceCalls.addAll(serviceCalls);
            serviceCallNode.setMarkedForLoopCheck(true);
        }
        else{
            System.err.println(String.format("Loop transformation error: the message \"%s\" is present in multiple loops.", message.nameProperty().getValue()));
        }

        serviceCallNode.getChildren().forEach(child -> {
            resolveControlServiceCalls(child);
        });
    }
    
    private List<String> getFailPlacesStrings(Collection<StandardPlace> failPlaces) {
        var result = new ArrayList<String>();
        failPlaces.forEach(failPlace -> {
            result.add(String.format("mark(\"%s\")", failPlace.getName()));
        });
        return result;
    }
    
    private FunctionSPNP<Integer> createFlushTransitionGuard() {
        var guardBody = new StringBuilder();
        
        // All fail places from relevant execution and communication segments
        controlServiceCalls.forEach(serviceCall -> {
            ActionServiceSegment actionSegment = serviceCall.getActionSegment();
            if(actionSegment != null) {
                var failPlacesStrings = getFailPlacesStrings(actionSegment.getFailPlaces());
                if(failPlacesStrings.size() > 0){
                    if(!"".equals(guardBody.toString()))
                        guardBody.append(" || ");
                    guardBody.append(String.format("(%s)", String.join(" || ", failPlacesStrings)));
                }
            }
        });
        guardBody.insert(0, "return (");

        guardBody.append(String.format(") &&%n       ("));
        if(controlServiceCalls.size() < 1){
            guardBody.append("0"); // Just in case, this should not happend
        }
        // All relevant places from control segment
        else{
            controlServiceCalls.forEach(serviceCall -> {
                if(controlServiceCalls.indexOf(serviceCall) > 0)
                    guardBody.append(" || ");
                guardBody.append(String.format("mark(\"%s\")", serviceCall.getPlace().getName()));
            });
        }
        guardBody.append(");");

        var flushGuardName = SPNPUtils.createFunctionName(String.format("guard_loop_flush"));
        return new FunctionSPNP<>(flushGuardName, FunctionType.Guard, guardBody.toString(), Integer.class);
    }
    
    private void transformFlushTransition() {
        var flushTransitionName = SPNPUtils.createTransitionName("loop", "flush");
    
        flushTransition = new ImmediateTransition(SPNPUtils.transitionCounter++, flushTransitionName,
                              SPNPUtils.TR_PRIORTY_LOOP_FLUSH, createFlushTransitionGuard(), new ConstantTransitionProbability(1.0));
        if(generateComments)
            flushTransition.setCommentary(String.format("%s - Flush transition", commentPrefix));
        petriNet.addTransition(flushTransition);
        
        controlServiceCalls.forEach(serviceCall -> {
            var controlPlace = serviceCall.getPlace();
            var cardinalityFunctionName = SPNPUtils.createFunctionName(String.format("cardinality_%s_loop_control_to_flush",
                                        SPNPUtils.prepareName(serviceCall.getMessage().nameProperty().getValue(), 15)));
            var cardinalityFunctionBody = String.format("return mark(\"%s\");", controlPlace.getName());
            var cardinalityFunction = new FunctionSPNP<Integer>(cardinalityFunctionName, FunctionType.ArcCardinality, cardinalityFunctionBody, Integer.class);
            var flushInputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Input, controlPlace, flushTransition, cardinalityFunction);
            petriNet.addArc(flushInputArc);
        });
    }
    
    private void transformFlushPlace() {
        var flushPlaceName = SPNPUtils.createPlaceName("loop", "flush");
        flushPlace = new StandardPlace(SPNPUtils.placeCounter++, flushPlaceName);
        if(generateComments)
            flushPlace.setCommentary(String.format("%s - Flush place", commentPrefix));
        petriNet.addPlace(flushPlace);
        
        var outputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Output, flushPlace, flushTransition);
        petriNet.addArc(outputArc);
    }
    
    private void transformRestartTransition() {
        var rate = loop.getRestartRate();
        var restartTransitionName = SPNPUtils.createTransitionName("loop", "restart");
        
        if(rate > 0.0) {
            var distribution = new ExponentialTransitionDistribution(rate);
            restartTransition = new TimedTransition(SPNPUtils.transitionCounter++, restartTransitionName, SPNPUtils.TR_PRIORTY_DEFAULT, null, distribution);
        }
        else {
            restartTransition = new ImmediateTransition(SPNPUtils.transitionCounter++, restartTransitionName,
                                SPNPUtils.TR_PRIORTY_LOOP_RESTART, null, new ConstantTransitionProbability(1.0));
        }
        if(generateComments)
            restartTransition.setCommentary(String.format("%s - Restart transition", commentPrefix));
        petriNet.addTransition(restartTransition);

        var flushInputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Input, flushPlace, restartTransition);
        petriNet.addArc(flushInputArc);

        var controlOutputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Output, highestControlServiceCall.getPlace(), restartTransition);
        petriNet.addArc(controlOutputArc);
    }

    private void transformRepeatsPlace() {
        var repeatsPlaceName = SPNPUtils.createPlaceName("loop", "repeats");
        repeatsPlace = new StandardPlace(SPNPUtils.placeCounter++, repeatsPlaceName);
        if(generateComments)
            repeatsPlace.setCommentary(String.format("%s - Repeats place", commentPrefix));
        petriNet.addPlace(repeatsPlace);

        var outputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Output, repeatsPlace, restartTransition);
        petriNet.addArc(outputArc);

        var inhibitorArc = new InhibitorArc(SPNPUtils.arcCounter++, repeatsPlace, flushTransition, loop.getIterations());
        petriNet.addArc(inhibitorArc);
    }

    public boolean containsControlServiceCall(ServiceCall controlServiceCall) {
        return controlServiceCalls.contains(controlServiceCall);
    }
    
    public StandardPlace getFlushPlace() {
        return flushPlace;
    }

    public void transform() {
        var serviceCall = controlServiceSegment.getHighestControlServiceCall(highestTreeNode);
        if(serviceCall != null) {
            highestControlServiceCall = serviceCall;
        }
        else {
            System.err.println(String.format("Loop transformation error: no highest control service call found for message \"%s\".",
                    highestTreeNode.getMessage().nameProperty().getValue()));
            return;
        }

        resolveControlServiceCalls(highestTreeNode);

        // Flush transition
        transformFlushTransition();
        
        // Flush place
        transformFlushPlace();
        
        // Restart transition
        transformRestartTransition();
        
        // Repeats place
        transformRepeatsPlace();
    }
}
