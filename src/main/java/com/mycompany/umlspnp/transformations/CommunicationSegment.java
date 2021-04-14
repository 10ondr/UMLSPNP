/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.umlspnp.transformations;

import com.mycompany.umlspnp.models.deploymentdiagram.CommunicationLink;
import com.mycompany.umlspnp.models.deploymentdiagram.DeploymentTarget;
import com.mycompany.umlspnp.models.sequencediagram.Message;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.util.Pair;

/**
 *
 * @author 10ondr
 */
public class CommunicationSegment extends Segment {
    protected UsageSegment usageSegment = null;
    protected List<PhysicalSegment> physicalSegments = null;

    protected final ServiceCallNode treeRoot;
    protected final CommunicationLink communicationLink;
    
    protected List<ServiceCall> topLevelServiceCalls = new ArrayList<>();
    
    protected ImmediateTransition initialTransition = null;
    protected StandardPlace startPlace = null;
    
    protected TimedTransition endTransition = null;
    protected StandardPlace endPlace = null;

    protected ImmediateTransition failHWTransitionFirst = null;
    protected StandardPlace failHWPlaceFirst = null;

    protected ImmediateTransition failHWTransitionSecond = null;
    protected StandardPlace failHWPlaceSecond = null;
    
    protected Map<TimedTransition, StandardPlace> failTypes = new HashMap<>();

    protected ImmediateTransition flushTransition = null;

    public CommunicationSegment(PetriNet petriNet,
                                ServiceCallNode treeRoot,
                                CommunicationLink communicationLink) {
        super(petriNet);

        this.treeRoot = treeRoot;
        this.communicationLink = communicationLink;

        String communicationLinkName = getCommunicationLinkNameSPNP();
        // End place
        transformEndPlace(communicationLinkName);
    }
    
    public CommunicationLink getCommunicationLink() {
        return communicationLink;
    }

    public StandardPlace getEndPlace() {
        return endPlace;
    }

    private void resolveTopLevelServiceCalls(ServiceCallNode node) {
        var message = node.getMessage();
        if(message != null) {
            var messageCommunicationLink = SPNPUtils.getMessageCommunicationLink(message);
            if(communicationLink == messageCommunicationLink){
                var topLevelServiceCall = findTopLevelServiceCall(usageSegment, message);
                if(topLevelServiceCall != null)
                    topLevelServiceCalls.add(topLevelServiceCall);
            }
        }
        node.getChildren().forEach(child -> {
            resolveTopLevelServiceCalls(child);
        });
    }

    private void transformInitialTransition(String communicationLinkName) {
        var initialTransitionName = SPNPUtils.createTransitionName(communicationLinkName, "comStart");
        initialTransition = new ImmediateTransition(SPNPUtils.transitionCounter++, initialTransitionName, 1, null, new ConstantTransitionProbability(1.0));
        petriNet.addTransition(initialTransition);
    }

    private String getTopLevelServicePlacesString() {
        var result = new StringBuilder();
        if(topLevelServiceCalls.size() > 0) {
            topLevelServiceCalls.forEach(topLevelServiceCall -> {
                result.append(String.format("mark(\"%s\")", topLevelServiceCall.getPlace().getName()));
                if(topLevelServiceCalls.indexOf(topLevelServiceCall) < topLevelServiceCalls.size() - 1)
                    result.append(" || ");
            });
        }
        return result.toString();
    }
    
    private void createInitialTransitionGuard(String communicationLinkName) {
        var guardBody = new StringBuilder();
        guardBody.append("return ");

        if(topLevelServiceCalls.size() > 0)
            guardBody.append(String.format("(%s) && ", getTopLevelServicePlacesString()));

        guardBody.append(String.format("!(mark(\"%s\")", startPlace.getName()));
        guardBody.append(String.format(" || (mark(\"%s\")", endPlace.getName()));
        failTypes.values().forEach(failTypePlace -> {
            guardBody.append(String.format(" || mark(\"%s\")", failTypePlace.getName()));
        });
        guardBody.append(String.format(" || mark(\"%s\")", failHWPlaceFirst.getName()));
        guardBody.append(String.format(" || mark(\"%s\"));", failHWPlaceSecond.getName()));

        var guardName = SPNPUtils.createFunctionName(String.format("guard_%s_comm_start", SPNPUtils.prepareName(communicationLinkName, 15)));
        FunctionSPNP<Integer> guard = new FunctionSPNP<>(guardName, FunctionType.Guard, guardBody.toString(), Integer.class);

        petriNet.addFunction(guard);
        initialTransition.setGuardFunction(guard);
    }
    
    private void transformStartPlace(String communicationLinkName) {
        var startPlaceName = SPNPUtils.createPlaceName(communicationLinkName, "trStart");
        startPlace = new StandardPlace(SPNPUtils.placeCounter++, startPlaceName);
        petriNet.addPlace(startPlace);

        var outputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Output, startPlace, initialTransition);
        petriNet.addArc(outputArc);
    }
    
    private void transformFlushTransition(String communicationLinkName) {
        var flushTransitionName = SPNPUtils.createTransitionName(communicationLinkName, "comFlush");
        flushTransition = new ImmediateTransition(SPNPUtils.transitionCounter++, flushTransitionName, 1, null, new ConstantTransitionProbability(1.0));
        petriNet.addTransition(flushTransition);
    }
    
    private void createFlushTransitionGuard(String communicationLinkName) {
        var guardBody = new StringBuilder();
        guardBody.append("return ");

        if(topLevelServiceCalls.size() > 0) {
            guardBody.append(String.format("(%s) && ", getTopLevelServicePlacesString()));
        }

        guardBody.append(String.format("(mark(\"%s\")", endPlace.getName()));
        failTypes.values().forEach(failTypePlace -> {
            guardBody.append(String.format(" || mark(\"%s\")", failTypePlace.getName()));
        });
        guardBody.append(String.format(" || mark(\"%s\")", failHWPlaceFirst.getName()));
        guardBody.append(String.format(" || mark(\"%s\"));", failHWPlaceSecond.getName()));

        var guardName = SPNPUtils.createFunctionName(String.format("guard_%s_comm_flush", SPNPUtils.prepareName(communicationLinkName, 15)));
        FunctionSPNP<Integer> guard = new FunctionSPNP<>(guardName, FunctionType.Guard, guardBody.toString(), Integer.class);

        petriNet.addFunction(guard);
        flushTransition.setGuardFunction(guard);
    }

    private String createGuardBody(DeploymentTarget targetNode) {
        var downPlace = SPNPUtils.getDownPlace(physicalSegments, targetNode);
        if(downPlace != null)
            return String.format("return mark(\"%s\");", downPlace.getName());
        return "return 0";
    }

    private Pair<ImmediateTransition, StandardPlace> transformFailHW(DeploymentTarget targetNode, String communicationLinkName) {
        String failHWPlaceName;
        String failHWTransitionName;
        String guardNameFormatString;
        if(targetNode == communicationLink.getFirst()){
            failHWPlaceName = SPNPUtils.createPlaceName(communicationLinkName, "HWf_st");
            failHWTransitionName = SPNPUtils.createTransitionName(communicationLinkName, "HWf_st");
            guardNameFormatString = "guard_%s_HW_fail_first";
        }
        else {
            failHWPlaceName = SPNPUtils.createPlaceName(communicationLinkName, "HWf_nd");
            failHWTransitionName = SPNPUtils.createTransitionName(communicationLinkName, "HWf_nd");
            guardNameFormatString = "guard_%s_HW_fail_second";
        }
        var failHWPlace = new StandardPlace(SPNPUtils.placeCounter++, failHWPlaceName);
        petriNet.addPlace(failHWPlace);

        var guardName = SPNPUtils.createFunctionName(String.format(guardNameFormatString, SPNPUtils.prepareName(communicationLinkName, 15)));
        FunctionSPNP<Integer> guard = new FunctionSPNP<>(guardName, FunctionType.Guard, createGuardBody(targetNode), Integer.class);

        var failHWTransition = new ImmediateTransition(SPNPUtils.transitionCounter++, failHWTransitionName, 1, guard, new ConstantTransitionProbability(1.0));
        petriNet.addTransition(failHWTransition);

        var inputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Input, startPlace, failHWTransition);
        petriNet.addArc(inputArc);

        var outputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Output, failHWPlace, failHWTransition);
        petriNet.addArc(outputArc);

        var flushInputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Input, failHWPlace, flushTransition);
        petriNet.addArc(flushInputArc);
        
        return new Pair<>(failHWTransition, failHWPlace);
    }
    
    private ServiceCall findTopLevelServiceCall(HighLevelSegment segment, Message message) {
        for(var serviceCall : segment.serviceCalls.values()) {
            if(serviceCall.getMessage() == message)
                return serviceCall;
        }

        for(var subsegment : segment.getServiceSegments()) {
            if(subsegment instanceof HighLevelSegment)
                return findTopLevelServiceCall((HighLevelSegment) subsegment, message);
        }
        return null;
    }
    
    private FunctionSPNP<Double> createDistributionFunction(String communicationLinkName) {
        var distributionFunctionName = SPNPUtils.createFunctionName(String.format("comm_trans_dist_func__%s", SPNPUtils.prepareName(communicationLinkName, 15)));
        var distributionValues = new StringBuilder();

        double transferRate = communicationLink.getLinkType().rateProperty().getValue();

        topLevelServiceCalls.forEach(topLevelServiceCall -> {
            var messageSizeObj = topLevelServiceCall.getMessage().getMessageSize();
            if(messageSizeObj == null)
                return;
            int messageSize = messageSizeObj.messageSizeProperty().getValue();
            double rate;
            if(messageSize <= 0)
                rate = 0;
            else
                rate = 1.0 / (messageSize / transferRate);

            if(!distributionValues.isEmpty())
                distributionValues.append(" + ");
            distributionValues.append(String.format("mark(\"%s\") * %f", topLevelServiceCall.getPlace().getName(), rate));
        });

        if(distributionValues.isEmpty())
            distributionValues.append("0");
        
        String distributionFunctionBody = String.format("return %s;", distributionValues.toString());
        FunctionSPNP<Double> distributionFunction = new FunctionSPNP<>(distributionFunctionName, FunctionType.Distribution, distributionFunctionBody, Double.class);
        return distributionFunction;
    }
    
    private void transformEndPlace(String communicationLinkName) {
        var endPlaceName = SPNPUtils.createPlaceName(communicationLinkName, "trEnd");
        endPlace = new StandardPlace(SPNPUtils.placeCounter++, endPlaceName);
        petriNet.addPlace(endPlace);
    }
    
    private void transformEndTransition(String communicationLinkName) {
        var endTransitionName = SPNPUtils.createTransitionName(communicationLinkName, "trEnd");
  
        var distribution = new ExponentialTransitionDistribution(createDistributionFunction(communicationLinkName));
        endTransition = new TimedTransition(SPNPUtils.transitionCounter++, endTransitionName, 1, null, distribution);
        petriNet.addTransition(endTransition);

        var inputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Input, startPlace, endTransition);
        petriNet.addArc(inputArc);

        var outputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Output, endPlace, endTransition);
        petriNet.addArc(outputArc);

        var flushInputArc = new StandardArc(SPNPUtils.arcCounter++, ArcDirection.Input, endPlace, flushTransition);
        petriNet.addArc(flushInputArc);
    }
    
    private void transformFailType(String failTypeName, double failTypeRate) {
        var failTypePlaceName = SPNPUtils.createPlaceName(failTypeName, "trFail");
        var failTypePlace = new StandardPlace(SPNPUtils.placeCounter++, failTypePlaceName);
        petriNet.addPlace(failTypePlace);

        var failTypeTransitionName = SPNPUtils.createTransitionName(failTypeName, "trFail");       
        var distribution = new ExponentialTransitionDistribution(failTypeRate);
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
    
    private String getCommunicationLinkNameSPNP() {
        return SPNPUtils.prepareName(communicationLink.getLinkType().nameProperty().getValue(), 8);
    }

    public void transformUsageSegmentDependencies(UsageSegment usageSegment) {
        this.usageSegment = usageSegment;
        
        String communicationLinkName = getCommunicationLinkNameSPNP();

        this.resolveTopLevelServiceCalls(treeRoot);

        // Initial transition
        transformInitialTransition(communicationLinkName);

        // Start place
        transformStartPlace(communicationLinkName);

        // Flush transition
        transformFlushTransition(communicationLinkName);

        // Fail type places and transitions
        communicationLink.getLinkFailures().forEach(failType -> {
            transformFailType(failType.nameProperty().getValue(), failType.rateProperty().getValue());
        });

        // End transition
        transformEndTransition(communicationLinkName);
    }
    
    public void transformPhysicalSegmentDependencies(List<PhysicalSegment> physicalSegments) {
        this.physicalSegments = physicalSegments;

        String communicationLinkName = getCommunicationLinkNameSPNP();

        // HW fail place and transition
        var firstPair = transformFailHW(this.communicationLink.getFirst(), communicationLinkName);
        failHWPlaceFirst = firstPair.getValue();
        failHWTransitionFirst = firstPair.getKey();
        
        // HW fail place and transition
        var secondPair = transformFailHW(this.communicationLink.getSecond(), communicationLinkName);
        failHWPlaceSecond = secondPair.getValue();
        failHWTransitionSecond = secondPair.getKey();

        // Initial transition guard function
        createInitialTransitionGuard(communicationLinkName);
        
        // Flush transition guard function
        createFlushTransitionGuard(communicationLinkName);
    }
}