package de.tum.bgu.msm.data.geo;

import de.tum.bgu.msm.data.Region;
import de.tum.bgu.msm.data.Zone;
import de.tum.bgu.msm.utils.SeededRandomPointsBuilder;
import de.tum.bgu.msm.utils.SiloUtil;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.shape.random.RandomPointsBuilder;

public class ZoneImpl implements Zone {

    private final int id;
    private final int msa;
    private final float area;

    private Region region;
    
    private SimpleFeature zoneFeature;
    

    public ZoneImpl(int id, int msa, float area) {
        this.id = id;
        this.msa = msa;
        this.area = area;
    }

    @Override
    public void setRegion(Region region) {
        this.region = region;
    }

    @Override
    public int getZoneId() {
        return this.id;
    }

    @Override
    public Region getRegion() {
        return this.region;
    }

    @Override
    public int getMsa() {
        return this.msa;
    }

    @Override
    public float getArea() {
        return area;
    }
    
    @Override
	public SimpleFeature getZoneFeature() {
        return zoneFeature;
    }
    
    @Override
	public void setZoneFeature(SimpleFeature zoneFeature) {
        this.zoneFeature = zoneFeature;
    }
    
    @Override
	public Coordinate getRandomCoordinate() {
        //TODO:this can be optimized by using the same (static) points builder multiple times instead of recreating it
        RandomPointsBuilder randomPointsBuilder = new SeededRandomPointsBuilder(new GeometryFactory(),
                SiloUtil.getRandomObject());
        randomPointsBuilder.setNumPoints(1);
        randomPointsBuilder.setExtent((Geometry) zoneFeature.getDefaultGeometry());
        Coordinate coordinate = randomPointsBuilder.getGeometry().getCoordinates()[0];
        Point p = MGC.coordinate2Point(coordinate);
        return new Coordinate(p.getX(), p.getY());
    }

    @Override
    public int getId() {
        return getZoneId();
    }
}