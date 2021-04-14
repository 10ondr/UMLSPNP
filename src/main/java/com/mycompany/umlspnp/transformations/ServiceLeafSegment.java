/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.umlspnp.transformations;

import com.mycompany.umlspnp.models.deploymentdiagram.Artifact;
import com.mycompany.umlspnp.models.deploymentdiagram.DeploymentTarget;
import cz.muni.fi.spnp.core.models.PetriNet;
import cz.muni.fi.spnp.core.models.arcs.ArcDirection;
import cz.muni.fi.spnp.core.models.arcs.StandardArc;
import cz.muni.fi.spnp.core.models.functions.FunctionType;
import cz.muni.fi.spnp.core.models.places.StandardPlace;
import cz.muni.fi.spnp.core.models.transitions.ImmediateTransition;
import cz.muni.fi.spnp.core.models.transitions.TimedTransition;
import cz.muni.fi.spnp.core.models.transitions.probabilities.ConstantTransitionProbability;
import cz.muni.fi.spnp.core.transformators.spnp.code.FunctionSPNP;
import cz.muni.fi.spnp.core.transformators.spnp.distributions.ExponentialTransitionDistribution;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author 10ondr
 */
public class ServiceLeafSegment extends Segment implements ServiceSegment {
    private final List<PhysicalSegment> physicalSegments;
    private final List<CommunicationSegment> communicationSegments;
    private final ServiceCallNode serviceCallNode;
    private final ServiceCall serviceCall;
    
    protected ImmediateTransition initialTransition = null;
    protected ImmediateTransition flushTransition = null;

    protected StandardPlace startPlace = null;
    
    protected TimedTransition endTransition = null;
    protected StandardPlace endPlace = null;
    
    protected ImmediateTransition failHWTransition = null;
    protected StandardPlace failHWPlace = null;
    
    protected Map<TimedTransition, StandardPlace> failTypes = new HashMap<>();

    
    public ServiceLeafSegment(PetriNet petriNet,
                              List<PhysicalSegment> physicalSegments,
                              List<CommunicationSegment> communicationSegments,
                              ServiceCallNode serviceCallNode,
                              ServiceCall serviceCall) {
        super(petriNet);
        
        this.physicalSegments = physicalSegments;
        this.communicationSegments = communicationSegments;
        this.serviceCallNode = serviceCallNode;
        this.serviceCall = serviceCall;
    }
    
    private void transformInitialTransition(String messageName) {
        var initialTransitionName = SPNPUtils.createTransitionName(messageName, "start");
        initialTransition = new ImmediateTransition(SPNPUtils.transitionCounter++, initialTransitionName, 1, null, new ConstantTransitionProbability(1.0));
        petriNet.addTransition(initialTransition);
    }
    
    private void createInitialTransitionGuard(String messageName) {
        var guardBody = new StringBuilder();
        guardBody.append(String.format("return mark(\"%s\") && !(", serviceCall.getPlace().getName()));

        guardBody.append(String.format("mark(\"%s\") || ", startPlace.getName()));
        guardBody.append(String.format("mark(\"%s\") || ", endPlace.getName()));
        failTypes.values().forEach(failTypePlace -> {
            guardBody.append(String.format("mark(\"%s\") || ", failTypePlace.getName()));
        });
        guardBody.append(String.format("mark(\"%s\"))", failHWPlace.getName()));

        // Remotely invoked through a communication link
        var communicationLink = SPNPUtils.getMessageCommunicationLink(serviceCall.getMessage());
        if(communicationLink != null) {
            communicationSegments.forEach(communicationSegment -> {
                if(communicationLink == communicationSegment.getCommunicationLink()) {
                    guardBody.append(String.format(" && mark(\"%s\")", communicationSegment.getEndPlace().getName()));
                }
            });
        }
        guardBody.append(";");
        
        var startGuardName = SPNPUtils.createFunctionName(String.format("guard_%s_leaf_start", SPNPUtils.prepareName(messageName, 15)));
        FunctionSPNP<Integer> startGuard = new FunctionSPNP<>(startGuardName, FunctionType.Guard, guardBody.toString(), Integer.class);

        petriNet.addFunction(startGuard);
        initialTransition.setGuardFunction(startGuard);
    }
    
    private void transformStartPlace(String messageName) {
        var startPlaceName = SPNPUtils.createPlaceName(messageName, "start");
        startPlace = new StandardPlace(SPNPUtils.placeCounter++, startPlaceName);
        petriNet.addPlace(startPlace);

        var outputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Output, startPlace, initialTransition);
        petriNet.addArc(outputArc);
    }

    private void transformFlushTransition(String messageName) {
        var flushTransitionName = SPNPUtils.createTransitionName(messageName, "flush");
        flushTransition = new ImmediateTransition(SPNPUtils.transitionCounter++, flushTransitionName, 1, null, new ConstantTransitionProbability(1.0));
        petriNet.addTransition(flushTransition);
        // TODO flush and end transition priorities needs to be configured (not yet specified properly)
    }

    private void createFlushTransitionGuard(String messageName) {
        var guardBody = new StringBuilder();
        guardBody.append(String.format("return mark(\"%s\") && (", serviceCall.getPlace().getName()));

        guardBody.append(String.format("mark(\"%s\") || ", endPlace.getName()));
        failTypes.values().forEach(failTypePlace -> {
            guardBody.append(String.format("mark(\"%s\") || ", failTypePlace.getName()));
        });
        guardBody.append(String.format("mark(\"%s\"));", failHWPlace.getName()));

        var flushGuardName = SPNPUtils.createFunctionName(String.format("guard_%s_leaf_flush", SPNPUtils.prepareName(messageName, 15)));
        FunctionSPNP<Integer> flushGuard = new FunctionSPNP<>(flushGuardName, FunctionType.Guard, guardBody.toString(), Integer.class);

        petriNet.addFunction(flushGuard);
        flushTransition.setGuardFunction(flushGuard);
    }

    private void transformEnd(String messageName) {
        var endPlaceName = SPNPUtils.createPlaceName(messageName, "end");
        endPlace = new StandardPlace(SPNPUtils.placeCounter++, endPlaceName);
        petriNet.addPlace(endPlace);

        var endTransitionName = SPNPUtils.createTransitionName(messageName, "end");
        var distribution = new ExponentialTransitionDistribution(1 / (double) serviceCall.getMessage().getExecutionTimeValue());
        endTransition = new TimedTransition(SPNPUtils.transitionCounter++, endTransitionName, 1, null, distribution);
        petriNet.addTransition(endTransition);

        var inputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Input, startPlace, endTransition);
        petriNet.addArc(inputArc);

        var outputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Output, endPlace, endTransition);
        petriNet.addArc(outputArc);
        
        var flushInputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Input, endPlace, flushTransition);
        petriNet.addArc(flushInputArc);
    }
    
    private DeploymentTarget getDeploymentTargetFromArtifact(Artifact artifact) {
        if(artifact == null)
            return null;
        if(artifact instanceof DeploymentTarget)
            return (DeploymentTarget) artifact;
        return artifact.getParent();
    }
    
    private Set<DeploymentTarget> getUnprocessedNodes() {
        var result = new HashSet<DeploymentTarget>();
        result.add(getDeploymentTargetFromArtifact(serviceCallNode.getArtifact()));

        var node = serviceCallNode;
        while(!node.isRoot() && !node.isProcessed()) {
            node.setProcessed(true);
            node = node.getParent();
            result.add(getDeploymentTargetFromArtifact(node.getArtifact()));
        }
        return result;
    }
    
    private FunctionSPNP<Integer> createHWFailGuard(String messageName) {
        var guardName = SPNPUtils.createFunctionName(String.format("guard_%s_HW_fail", SPNPUtils.prepareName(messageName, 15)));
        var guardBody = new StringBuilder("return ");

        var hwFailNodes = getUnprocessedNodes();

        // TODO remove prints when not needed
        System.err.println(String.format("%s  %s  checked nodes:", serviceCallNode.getCompoundOrderString(), messageName));
        hwFailNodes.forEach(node -> { System.err.println(node.getNameProperty().getValue());});


        boolean first = true;
        for(var node : hwFailNodes) {               
            var downPlace = SPNPUtils.getDownPlace(physicalSegments, node);
            if(first)
                first = false;
            else
                guardBody.append(" || ");
            guardBody.append(String.format("mark(\"%s\")", downPlace.getName()));
        }

        if(hwFailNodes.size() < 1)
            guardBody.append("0");
        guardBody.append(";");
        return new FunctionSPNP<>(guardName, FunctionType.Guard, guardBody.toString(), Integer.class);
    }
    
    private void transformFailHW(String messageName) {
        var failHWPlaceName = SPNPUtils.createPlaceName(messageName, "HW_fail");
        failHWPlace = new StandardPlace(SPNPUtils.placeCounter++, failHWPlaceName);
        petriNet.addPlace(failHWPlace);

        var failHWTransitionName = SPNPUtils.createTransitionName(messageName, "HW_fail");
        failHWTransition = new ImmediateTransition(SPNPUtils.transitionCounter++,
                                                   failHWTransitionName,
                                                   1, 
                                                   createHWFailGuard(messageName),
                                                   new ConstantTransitionProbability(1.0));
        petriNet.addTransition(failHWTransition);

        var inputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Input, startPlace, failHWTransition);
        petriNet.addArc(inputArc);

        var outputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Output, failHWPlace, failHWTransition);
        petriNet.addArc(outputArc);
        
        var flushInputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Input, failHWPlace, flushTransition);
        petriNet.addArc(flushInputArc);
    }
    
    private void transformFailType(String messageName, String failureName, double failureRate) {
        var failTypePlaceName = SPNPUtils.createPlaceName(messageName, "FT_" + failureName);
        var failTypePlace = new StandardPlace(SPNPUtils.placeCounter++, failTypePlaceName);
        petriNet.addPlace(failTypePlace);

        var failTypeTransitionName = SPNPUtils.createTransitionName(messageName, "FT_" + failureName);
        var distribution = new ExponentialTransitionDistribution(failureRate);
        var failTypeTransition = new TimedTransition(SPNPUtils.transitionCounter++, failTypeTransitionName, 1, null, distribution);
        petriNet.addTransition(failTypeTransition);

        failTypes.put(failTypeTransition, failTypePlace);
        
        var inputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Input, startPlace, failTypeTransition);
        petriNet.addArc(inputArc);

        var outputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Output, failTypePlace, failTypeTransition);
        petriNet.addArc(outputArc);
        
        var flushInputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Input, failTypePlace, flushTransition);
        petriNet.addArc(flushInputArc);
    }
    
    @Override
    public ServiceCall getServiceCall() {
        return serviceCall;
    }

    @Override
    public StandardPlace getEndPlace() {
        return endPlace;
    }

    @Override
    public void transform() {
        var message = serviceCall.getMessage();
        var messageName = message.nameProperty().getValue();

        // Initial transition
        transformInitialTransition(messageName);

        // Start place
        transformStartPlace(messageName);

        // Flush transition
        transformFlushTransition(messageName);

        // End place and transition
        transformEnd(messageName);

        // HW Fail place and transition
        transformFailHW(messageName);

        // Service call fail types - places and transitions
        message.getMessageFailures().forEach(messageFailure -> {
            transformFailType(messageName, messageFailure.nameProperty().getValue(), messageFailure.rateProperty().getValue());
        });

        // Initial transition guard function
        createInitialTransitionGuard(messageName);

        // Flush transition guard function
        createFlushTransitionGuard(messageName);
    }
}