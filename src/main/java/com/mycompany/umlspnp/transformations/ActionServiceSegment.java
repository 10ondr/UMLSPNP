/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.umlspnp.transformations;

import cz.muni.fi.spnp.core.models.places.StandardPlace;
import java.util.Collection;

/**
 *
 * @author 10ondr
 */
public interface ActionServiceSegment {
    public StandardPlace getEndPlace();
    public Collection<StandardPlace> getFailPlaces();
    public void setFlushTransitionGuardDependentPlace(StandardPlace dependentPlace);
    public void transform();
}