package uk.ac.rdg.resc.ncwms.coords;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.junit.Test;

import ucar.nc2.constants.AxisType;
import uk.ac.rdg.resc.ncwms.coords.PixelMap.PixelMapEntry;

/**
 * Tests for {@link PixelMap} index handling.
 * 
 * @author Ben Caradoc-Davies (CSIRO Earth Science and Resource Engineering)
 */
public class PixelMapTest {

    /**
     * Test for a small source grid.
     * 
     * @throws Exception
     */
    @Test
    public void small() throws Exception {
        test(SourceGrid.Small);
    }

    /**
     * Test for a medium source grid.
     * 
     * @throws Exception
     */
    @Test
    public void medium() throws Exception {
        test(SourceGrid.Medium);
    }

    /**
     * Test for a large source grid.
     * 
     * @throws Exception
     */
    @Test
    public void large() throws Exception {
        test(SourceGrid.Large);
    }

    /**
     * Test that expected indices are found for a selection of arbitrary points.
     */
    @Test
    public void indices() throws Exception {
        testIndices(SourceGrid.Small, 0.51, 0.48, 2352, 7225);
        testIndices(SourceGrid.Small, 0.01, 0.02, 46, 301);
        testIndices(SourceGrid.Small, 0.97, 0.99, 4474, 14902);
        testIndices(SourceGrid.Small, 0.03, 0.96, 138, 14451);
        testIndices(SourceGrid.Small, 0.98, 0.05, 4520, 753);
    }

    /**
     * Run tests for a source grid of a given size.
     * 
     * @param sourceGrid
     * @throws Exception
     */
    public void test(SourceGrid sourceGrid) throws Exception {
        HorizontalCoordSys cs = sourceGrid.buildCoordSys();
        PointList targetPoints = sourceGrid.buildTargetPointList();
        for (HorizontalPosition targetPoint : targetPoints.asList()) {
            testPoint(cs, targetPoint);
        }
        testGrid(cs, targetPoints);
    }

    /**
     * Test that a single point has consistent indices.
     * 
     * @param cs
     * @param targetPoint
     * @throws Exception
     */
    public void testPoint(HorizontalCoordSys cs, HorizontalPosition targetPoint)
            throws Exception {
        PixelMap pixelMap = new PixelMap(cs,
                PointList.fromPoint((LonLatPosition) targetPoint));
        Assert.assertEquals(pixelMap.getMinIIndex(), pixelMap.getMaxIIndex());
        Assert.assertEquals(pixelMap.getMinJIndex(), pixelMap.getMaxJIndex());
        int count = 0;
        for (PixelMapEntry entry : pixelMap) {
            Assert.assertEquals(pixelMap.getMinIIndex(),
                    entry.getSourceGridIIndex());
            Assert.assertEquals(pixelMap.getMinJIndex(),
                    entry.getSourceGridJIndex());
            count++;
        }
        Assert.assertEquals(1, count);
    }

    /**
     * Test that expected indices are found for an arbitrary selection of
     * points.
     * 
     * @param sourceGrid
     * @param targetPointLonFraction
     * @param targetPointLatFraction
     * @param expectedSourceGridIIndex
     * @param expectedSourceGridJIndex
     * @throws Exception
     */
    public void testIndices(SourceGrid sourceGrid,
            double targetPointLonFraction, double targetPointLatFraction,
            int expectedSourceGridIIndex, int expectedSourceGridJIndex)
            throws Exception {
        PixelMap pixelMap = new PixelMap(sourceGrid.buildCoordSys(),
                PointList.fromPoint((LonLatPosition) sourceGrid.buildTestPoint(
                        targetPointLonFraction, targetPointLatFraction)));
        Assert.assertEquals(pixelMap.getMinIIndex(), pixelMap.getMaxIIndex());
        Assert.assertEquals(pixelMap.getMinJIndex(), pixelMap.getMaxJIndex());
        int count = 0;
        for (PixelMapEntry entry : pixelMap) {
            Assert.assertEquals(pixelMap.getMinIIndex(),
                    entry.getSourceGridIIndex());
            Assert.assertEquals(pixelMap.getMinJIndex(),
                    entry.getSourceGridJIndex());
            Assert.assertEquals(expectedSourceGridIIndex,
                    entry.getSourceGridIIndex());
            Assert.assertEquals(expectedSourceGridJIndex,
                    entry.getSourceGridJIndex());
            count++;
        }
        Assert.assertEquals(1, count);
    }

    /**
     * Test that grid point indices are in the expected row-major order.
     * 
     * @param cs
     * @param targetPoints
     * @throws Exception
     */
    public void testGrid(HorizontalCoordSys cs, PointList targetPoints)
            throws Exception {
        PixelMap pixelMap = new PixelMap(cs, targetPoints);
        int count = 0;
        int lastSourceGridIIndex = 0;
        int lastSourceGridJIndex = 0;
        for (PixelMapEntry entry : pixelMap) {
            Assert.assertTrue(lastSourceGridIIndex <= entry
                    .getSourceGridIIndex());
            Assert.assertTrue(lastSourceGridJIndex < entry
                    .getSourceGridJIndex()
                    || lastSourceGridIIndex < entry.getSourceGridIIndex());
            count++;
            lastSourceGridIIndex = entry.getSourceGridIIndex();
            lastSourceGridJIndex = entry.getSourceGridJIndex();
        }
        Assert.assertEquals(targetPoints.size(), count);
    }

    /**
     * A longitude/latitude source grid.
     */
    public enum SourceGrid {

        Small(141.84, 152.32, 4613, -44.20, -9.98, 15054), //
        Medium(141.84, 152.32, 92255, -44.20, -9.98, 301081), //
        Large(141.84, 152.32, 461301, -44.20, -9.98, 1505403);

        private double lonMin;

        private double lonMax;

        private int lonSize;

        private double latMin;

        private double latMax;

        private int latSize;

        /**
         * Constructor a source grid for given longitude and latitude range and
         * grid size.
         * 
         * @param lonMin
         * @param lonMax
         * @param lonSize
         * @param latMin
         * @param latMax
         * @param latSize
         */
        private SourceGrid(double lonMin, double lonMax, int lonSize,
                double latMin, double latMax, int latSize) {
            this.lonMin = lonMin;
            this.lonMax = lonMax;
            this.lonSize = lonSize;
            this.latMin = latMin;
            this.latMax = latMax;
            this.latSize = latSize;
        }

        /**
         * Return the coordinate system representing this source grid.
         */
        public HorizontalCoordSys buildCoordSys() {
            return new LatLonCoordSys(buildCoordAxisAxis(lonMin, lonMax,
                    lonSize, AxisType.Lon), buildCoordAxisAxis(latMin, latMax,
                    latSize, AxisType.Lat));
        }

        /**
         * Return a coordinate axis.
         * 
         * @param min
         * @param max
         * @param size
         * @param axisType
         * @return
         */
        private static OneDCoordAxis buildCoordAxisAxis(double min, double max,
                int size, AxisType axisType) {
            return new Regular1DCoordAxis(min, (max - min) / (size - 1), size,
                    axisType);
        }

        /**
         * Return a test point inside the limits of the grid calculated from
         * fractions spans of longitude and latitude.
         * 
         * @param lonFraction
         *            longitude fraction from 0.0 to 1.0
         * @param latFraction
         *            latitude fraction from 0.0 to 1.0)
         * @return
         */
        public LonLatPosition buildTestPoint(double lonFraction,
                double latFraction) {
            return new LonLatPositionImpl(lonMin + lonFraction
                    * (lonMax - lonMin), latMin + latFraction
                    * (latMax - latMin));
        }

        /**
         * Return an arbitrary target grid in row-major order. The target grid
         * is contained in the source grid but does not necessarily share
         * points. It is assumed to be much coarser than the source grid.
         * 
         * @return
         */
        public PointList buildTargetPointList() {
            // these target grid parameters are arbitrary; should be coarser
            // than source grid
            int lonSize = 101;
            int latSize = 103;
            double lonFractionMin = 0.01;
            double lonFractionMax = 0.98;
            double latFractionMin = 0.03;
            double latFractionMax = 0.95;
            List<HorizontalPosition> positions = new ArrayList<HorizontalPosition>();
            for (int i = 0; i < lonSize; i++) {
                for (int j = 0; j < latSize; j++) {
                    positions.add(buildTestPoint(
                            lonFractionMin + i
                                    * (lonFractionMax - lonFractionMin)
                                    / (lonSize - 1), latFractionMin + j
                                    * (latFractionMax - latFractionMin)
                                    / (latSize - 1)));
                }
            }
            return PointList.fromList(positions, CrsHelper.CRS_84);
        }

    }

}
