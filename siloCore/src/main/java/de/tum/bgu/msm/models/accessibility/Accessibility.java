package de.tum.bgu.msm.models.accessibility;

import de.tum.bgu.msm.data.Region;
import de.tum.bgu.msm.data.Zone;

/**
 * @author dziemke, nico
 */
public interface Accessibility {
	// TODO add mode and time as arguments
	
	// TODO need specification for undefined time, e.g. null
	// undefined = some averaging?
	// SILO needs peak-hour accessibilities
	// need to be scaled
	
	// TODO use Location instead of zone as argument
	
	// TODO Maybe rename this method?
	public void getHansenAccessibilities(int year);
	
	public double getAutoAccessibilityForZone(Zone zone);
	
	public double getTransitAccessibilityForZone(Zone zone);
	
	public double getRegionalAccessibility(Region region);
}