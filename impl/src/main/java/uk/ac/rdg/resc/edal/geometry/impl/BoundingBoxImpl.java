package uk.ac.rdg.resc.edal.geometry.impl;

import org.opengis.geometry.DirectPosition;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.position.impl.DirectPositionImpl;

/**
 * <p>
 * Immutable implementation of a {@link BoundingBox}.
 * </p>
 * 
 * @author Jon
 */
public class BoundingBoxImpl extends AbstractEnvelope implements BoundingBox {
    private final double minx;
    private final double miny;
    private final double maxx;
    private final double maxy;

    public BoundingBoxImpl(Envelope envelope2d) {
        super(envelope2d.getCoordinateReferenceSystem());
        if (envelope2d.getDimension() != 2) {
            throw new IllegalArgumentException("Envelope dimension must be 2");
        }
        this.minx = envelope2d.getMinimum(0);
        this.maxx = envelope2d.getMaximum(0);
        this.miny = envelope2d.getMinimum(1);
        this.maxy = envelope2d.getMaximum(1);
    }

    public BoundingBoxImpl(Envelope xExtent, Envelope yExtent, CoordinateReferenceSystem crs) {
        super(crs);

        if (xExtent.getDimension() != 1 || yExtent.getDimension() != 1) {
            throw new IllegalArgumentException("Envelopes must be one-dimensional");
        }

        // Check that CRSs match
        if (xExtent.getCoordinateReferenceSystem() != null) {
            if (!xExtent.getCoordinateReferenceSystem().equals(crs)) {
                throw new IllegalArgumentException("CRSs do not match");
            }
        }
        if (yExtent.getCoordinateReferenceSystem() != null) {
            if (!yExtent.getCoordinateReferenceSystem().equals(crs)) {
                throw new IllegalArgumentException("CRSs do not match");
            }
        }

        this.minx = xExtent.getMinimum(0);
        this.maxx = xExtent.getMaximum(0);
        this.miny = yExtent.getMinimum(0);
        this.maxy = yExtent.getMaximum(0);
    }

    /** Constructs a BoundingBox with a null coordinate reference system */
    public BoundingBoxImpl(Envelope xExtent, Envelope yExtent) {
        this(xExtent, yExtent, null);
    }

    /** Creates a BoundingBox from a four-element array [minx, miny, maxx, maxy] */
    public BoundingBoxImpl(double[] bbox, CoordinateReferenceSystem crs) {
        super(crs);
        if (bbox == null)
            throw new NullPointerException();
        if (bbox.length != 4)
            throw new IllegalArgumentException("Bounding box" + " must have four elements");
        this.minx = bbox[0];
        this.maxx = bbox[2];
        this.miny = bbox[1];
        this.maxy = bbox[3];
        // Check the bounds of the bbox
        if (this.minx > this.maxx || this.miny > this.maxy) {
            throw new IllegalArgumentException("Invalid bounding box specification");
        }
    }

    /** Constructs a bounding box with an unknown (null) CRS */
    public BoundingBoxImpl(double[] bbox) {
        this(bbox, null);
    }

    @Override
    public int getDimension() {
        return 2;
    }

    @Override
    public double getMinX() {
        return this.minx;
    }

    @Override
    public double getMaxX() {
        return this.maxx;
    }

    @Override
    public double getMinY() {
        return this.miny;
    }

    @Override
    public double getMaxY() {
        return this.maxy;
    }

    @Override
    public double getMinimum(int i) {
        if (i == 0)
            return this.minx;
        if (i == 1)
            return this.miny;
        throw new IndexOutOfBoundsException();
    }

    @Override
    public double getMaximum(int i) {
        if (i == 0)
            return this.maxx;
        if (i == 1)
            return this.maxy;
        throw new IndexOutOfBoundsException();
    }

    @Override
    public DirectPosition getLowerCorner() {
        return new DirectPositionImpl(this.getCoordinateReferenceSystem(), this.minx, this.miny);
    }

    @Override
    public DirectPosition getUpperCorner() {
        return new DirectPositionImpl(this.getCoordinateReferenceSystem(), this.maxx, this.maxy);
    }

    @Override
    public String toString() {
        return String.format("%f, %f - %f, %f", this.minx, this.miny, this.maxx, this.maxy);
    }
}