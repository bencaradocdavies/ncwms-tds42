/*
 * Copyright (c) 2007 The University of Reading
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University of Reading, nor the names of the
 *    authors or contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package uk.ac.rdg.resc.ncwms.coords;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.rdg.resc.ncwms.cdm.DataReadingStrategy;

/**
 *<p>Maps real-world points to i and j indices of corresponding
 * points within the source data.  This is a very important class in ncWMS.  A
 * PixelMap is constructed using the following general algorithm:</p>
 *
 * <pre>
 * For each point in the given {@link PointList}:
 *    1. Find the x-y coordinates of this point in the CRS of the PointList
 *    2. Transform these x-y coordinates into latitude and longitude
 *    3. Use the given {@link HorizontalCoordSys} to transform lat-lon into the
 *       index values (i and j) of the nearest cell in the source grid
 *    4. Add the mapping (point -> i,j) to the pixel map
 * </pre>
 *
 * <p>(A more efficient algorithm is used for the special case in which both the
 * requested CRS and the CRS of the data are lat-lon.)</p>
 *
 * <p>The resulting PixelMap is then used by {@link DataReadingStrategy}s to work out what
 * data to read from the source data files.  A variety of strategies are possible
 * for reading these data points, each of which may be optimal in a certain
 * situation.</p>
 *
 * @author Jon Blower
 * @todo Perhaps we can think of a more appropriate name for this class?
 * @todo equals() and hashCode(), particularly if we're going to cache instances
 * of this class.
 * @todo It may be possible to create an alternative version of this class for
 * cases where both source and target grids are lat-lon.  In this case, the
 * pixelmap should also be a RectilinearGrid, meaning that there would be no need
 * to store mapping information in HashMaps etc.  (Profiling shows that getting
 * and putting data from/to the HashMaps is a bottleneck.)
 * @see DataReadingStrategy
 */
public final class PixelMap implements Iterable<PixelMap.PixelMapEntry>
{
    private static final Logger logger = LoggerFactory.getLogger(PixelMap.class);

    /**
     * Source grid points corresponding to target grid points in
     * {@link PixelMap#pixelMapTargetEntries}. Only the first
     * {@link PixelMap#numEntries} elements are in use.
     */
    private long[] pixelMapSourceEntries;

    /**
     * Target grid points corresponding to source grid points in
     * {@link PixelMap#pixelMapSourceEntries}. Only the first
     * {@link PixelMap#numEntries} elements are in use.
     */
    private int[] pixelMapTargetEntries;

    /**
     * The number of entries in this pixel map, that is, the number of elements
     * of {@link PixelMap#pixelMapSourceEntries} and
     * {@link PixelMap#pixelMapTargetEntries} that are in use.
     */
    private int numEntries = 0;

    /**
     * The arrays of pixel map entries will grow in size as required by this
     * number of elements.  This means that the arrays of pixels map entries will
     * never be more than {@code chunkSize - 1} greater than they have to be.
     */
    private final int chunkSize;

    /**
     * Maps a point in the source grid to corresponding points in the target grid.
     */
    public static interface PixelMapEntry
    {
        /** Gets the i index of this point in the source grid */
        public int getSourceGridIIndex();
        /** Gets the j index of this point in the source grid */
        public int getSourceGridJIndex();
        /** Gets the array of all target grid points that correspond with this
         * source grid point.  Each grid point is expressed as a single integer
         * {@code j * width + i}.*/
        public List<Integer> getTargetGridPoints();
    }

    private final int sourceGridISize;

    // These define the bounding box (in terms of axis indices) of the data
    // to extract from the source files
    private int minIIndex = Integer.MAX_VALUE;
    private int minJIndex = Integer.MAX_VALUE;
    private int maxIIndex = -1;
    private int maxJIndex = -1;

    public PixelMap(HorizontalCoordSys horizCoordSys, PointList pointList) throws TransformException
    {
        long start = System.currentTimeMillis();
        this.sourceGridISize = horizCoordSys.getXAxisSize();

        // Create an estimate of a suitable chunk size.  We don't want this to
        // be too small because we would have to do many array copy operations
        // to grow the array in put().  Conversely we don't want it to be too
        // large and lead to wasted space.
        this.chunkSize = pointList.size() < 1000
            ? pointList.size()
            : pointList.size() / 10;
        this.pixelMapSourceEntries = new long[this.chunkSize];
        this.pixelMapTargetEntries = new int[this.chunkSize];

        if (pointList instanceof HorizontalGrid)
        {
            this.initFromGrid(horizCoordSys, (HorizontalGrid)pointList);
        }
        else
        {
            this.initFromPointList(horizCoordSys, pointList);
        }
        logger.debug("Built pixel map in {} ms", System.currentTimeMillis() - start);
    }

    private void initFromPointList(HorizontalCoordSys horizCoordSys, PointList pointList) throws TransformException
    {
        logger.debug("Using generic method based on iterating over the PointList");
        CrsHelper crsHelper = pointList.getCrsHelper();
        int pixelIndex = 0;
        for (HorizontalPosition point : pointList.asList())
        {
            // Check that this point is valid in the target CRS
            if (crsHelper.isPointValidForCrs(point))
            {
                // Translate this point in the target grid to lat-lon
                LonLatPosition lonLat = crsHelper.crsToLonLat(point);
                // Now find the nearest index in the grid: gridCoords will be
                // null if latLon is outside the grid's domain
                int[] gridCoords = horizCoordSys.lonLatToGrid(lonLat);
                if (gridCoords != null)
                {
                    this.put(gridCoords[0], gridCoords[1], pixelIndex);
                }
            }
            pixelIndex++;
        }
    }

    /**
     * Generates a PixelMap for the given Layer.  Data read from the Layer will
     * be projected onto the given HorizontalGrid
     *
     * @throws Exception if the necessary transformations could not be performed
     */
    private void initFromGrid(HorizontalCoordSys horizCoordSys, HorizontalGrid grid) throws TransformException
    {
        // Cycle through each pixel in the picture and work out which
        // i and j index in the source data it corresponds to

        // We can gain efficiency if the target grid is a lat-lon grid and
        // the data exist on a lat-long grid by minimizing the number of
        // calls to axis.getIndex().
        if (grid.isLatLon() && horizCoordSys instanceof LatLonCoordSys)
        {
            logger.debug("Using optimized method for lat-lon coordinates with 1D axes");
            LatLonCoordSys latLonGrid = (LatLonCoordSys)horizCoordSys;
            int pixelIndex = 0;
            // Calculate the indices along the x axis.
            int[] xIndices = new int[grid.getXAxisValues().length];
            for (int i = 0; i < grid.getXAxisValues().length; i++)
            {
                xIndices[i] = latLonGrid.getLonIndex(grid.getXAxisValues()[i]);
            }
            for (double lat : grid.getYAxisValues())
            {
                if (lat >= -90.0 && lat <= 90.0)
                {
                    int yIndex = latLonGrid.getLatIndex(lat);
                    for (int xIndex : xIndices)
                    {
                        this.put(xIndex, yIndex, pixelIndex);
                        pixelIndex++;
                    }
                }
                else
                {
                    // We still need to increment the pixel index
                    pixelIndex += xIndices.length;
                }
            }
        }
        else
        {
            // We can't do better than the generic initialization method
            // based upon iterating through each point in the grid.
            this.initFromPointList(horizCoordSys, (PointList)grid);
        }
    }

    /**
     * Adds a new pixel index to this map.  Does nothing if either i or j is
     * negative.
     * @param i The i index of the point in the source data
     * @param j The j index of the point in the source data
     * @param targetGridIndex The index of the corresponding point in the target domain
     */
    private void put(int i, int j, int targetGridIndex)
    {
        // If either of the indices are negative there is no data for this
        // target grid point
        if (i < 0 || j < 0) return;

        // Modify the bounding box if necessary
        if (i < this.minIIndex) this.minIIndex = i;
        if (i > this.maxIIndex) this.maxIIndex = i;
        if (j < this.minJIndex) this.minJIndex = j;
        if (j > this.maxJIndex) this.maxJIndex = j;

        // Calculate a single long integer representing this grid point in the source grid
        long sourceGridIndex = ((long) j) * ((long) sourceGridISize) + ((long) i);

        if (pixelMapSourceEntries.length != pixelMapTargetEntries.length) {
            throw new RuntimeException(
                    "Internal error: array size mismatch in PixelMap "
                            + "(this should never happen)");
        }

        // See if we need to grow the arrays of pixel map entries
        if (numEntries >= pixelMapSourceEntries.length) {
            // grow source array
            {
                long[] newPixelMapSourceEntries = new long[pixelMapSourceEntries.length
                        + chunkSize];
                System.arraycopy(this.pixelMapSourceEntries, 0,
                        newPixelMapSourceEntries, 0,
                        pixelMapSourceEntries.length);
                pixelMapSourceEntries = newPixelMapSourceEntries;
            }
            // grow target array
            {
                int[] newPixelMapTargetEntries = new int[pixelMapTargetEntries.length
                        + chunkSize];
                System.arraycopy(pixelMapTargetEntries, 0,
                        newPixelMapTargetEntries, 0,
                        pixelMapTargetEntries.length);
                pixelMapTargetEntries = newPixelMapTargetEntries;
            }
        }

        pixelMapSourceEntries[numEntries] = sourceGridIndex;
        pixelMapTargetEntries[numEntries] = targetGridIndex;
        numEntries++;
    }

    /**
     * Returns true if this PixelMap does not contain any data: this will happen
     * if there is no intersection between the requested data and the data on disk.
     * @return true if this PixelMap does not contain any data: this will happen
     * if there is no intersection between the requested data and the data on disk
     */
    public boolean isEmpty()
    {
        return this.numEntries == 0;
    }

    /**
     * Gets the minimum i index in the whole pixel map
     * @return the minimum i index in the whole pixel map
     */
    public int getMinIIndex()
    {
        return minIIndex;
    }

    /**
     * Gets the minimum j index in the whole pixel map
     * @return the minimum j index in the whole pixel map
     */
    public int getMinJIndex()
    {
        return minJIndex;
    }

    /**
     * Gets the maximum i index in the whole pixel map
     * @return the maximum i index in the whole pixel map
     */
    public int getMaxIIndex()
    {
        return maxIIndex;
    }

    /**
     * Gets the maximum j index in the whole pixel map
     * @return the maximum j index in the whole pixel map
     */
    public int getMaxJIndex()
    {
        return maxJIndex;
    }

    /**
     * <p>Gets the number of unique i-j pairs in this pixel map. When combined
     * with the size of the resulting image we can quantify the under- or
     * oversampling.  This is the number of data points that will be extracted
     * by the {@link DataReadingStrategy#PIXEL_BY_PIXEL PIXEL_BY_PIXEL} data
     * reading strategy.</p>
     * <p>This implementation counts the number of unique pairs by cycling through
     * the {@link #iterator()} and so is not a cheap operation.  Use sparingly,
     * e.g. for debugging.</p>
     * @return the number of unique i-j pairs in this pixel map.
     */
    public int getNumUniqueIJPairs()
    {
        int count = 0;
        for (@SuppressWarnings("unused") PixelMapEntry pme : this) {
            count++;
        }
        return count;
    }

    /**
     * Gets the size of the i-j bounding box that encompasses all data.  This is
     * the number of data points that will be extracted using the
     * {@link DataReadingStrategy#BOUNDING_BOX BOUNDING_BOX} data reading strategy.
     * @return the size of the i-j bounding box that encompasses all data.
     */
    public long getBoundingBoxSize() {
        return ((long) (maxIIndex - minIIndex + 1))
                * ((long) (maxJIndex - minJIndex + 1));
    }

    /**
     * Returns an unmodifiable iterator over all the {@link PixelMapEntry}s in this PixelMap.
     */
    @Override
    public Iterator<PixelMapEntry> iterator()
    {
        return new Iterator<PixelMapEntry>()
        {
            /** Index in the arrays of entries */
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < numEntries;
            }

            @Override
            public PixelMapEntry next() {
                final long sourceGridIndex = pixelMapSourceEntries[index];
                final List<Integer> targetGridIndices = new ArrayList<Integer>();
                targetGridIndices.add(pixelMapTargetEntries[index]);
                index++;

                // Now find all the other entries that use the same source grid
                // index
                boolean done = false;
                while (!done && hasNext()) {
                    long otherSourceGridIndex = pixelMapSourceEntries[index];
                    if (otherSourceGridIndex == sourceGridIndex) {
                        targetGridIndices.add(pixelMapTargetEntries[index]);
                        this.index++;
                    } else {
                        done = true;
                    }
                }

                return new PixelMapEntry() {

                    @Override
                    public int getSourceGridIIndex() {
                        return (int) (sourceGridIndex % sourceGridISize);
                    }

                    @Override
                    public int getSourceGridJIndex() {
                        return (int) (sourceGridIndex / sourceGridISize);
                    }

                    @Override
                    public List<Integer> getTargetGridPoints() {
                        return targetGridIndices;
                    }

                };

            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

        };

    }

}
