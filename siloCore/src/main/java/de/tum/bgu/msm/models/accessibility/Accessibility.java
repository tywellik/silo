package de.tum.bgu.msm.models.accessibility;

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
	
	public double getAutoAccessibilityForZone(int zone);
	
	public double getTransitAccessibilityForZone(int zoneId);
	
	public double getRegionalAccessibility(int region);
}