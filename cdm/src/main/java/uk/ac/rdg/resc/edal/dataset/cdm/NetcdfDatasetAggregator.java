/*******************************************************************************
 * Copyright (c) 2015 The University of Reading
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
 ******************************************************************************/

package uk.ac.rdg.resc.edal.dataset.cdm;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import thredds.client.catalog.ServiceType;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.nc2.dataset.DatasetUrl;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.ncml.NcMLReader;
import ucar.nc2.units.DateUnit;
import uk.ac.rdg.resc.edal.exceptions.DataReadingException;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.util.cdm.CdmUtils;

public class NetcdfDatasetAggregator {
    private static final Logger log = LoggerFactory.getLogger(NetcdfDatasetAggregator.class);
    private static final int DATASET_CACHE_SIZE = 20;

    private static Map<String, String> ncmlStringCache = new HashMap<>();

    private static Map<NetcdfDataset, Integer> activeDatasets = new HashMap<>();
    /**
     * A LRU cache of {@link NetcdfDataset}s.
     */
    @SuppressWarnings("serial")
    private static Map<String, NetcdfDataset> datasetCache = new LinkedHashMap<String, NetcdfDataset>(
            DATASET_CACHE_SIZE + 1, 1.0f, true) {

        protected boolean removeEldestEntry(final Map.Entry<String, NetcdfDataset> eldest) {
            /*
             * If we are going to remove the eldest entry, we also want to call
             * the close method on it before allowing LinkedHashMap to do the
             * actual removal
             */
            if (super.size() > DATASET_CACHE_SIZE) {
                /*
                 * Start a new thread which will remove the dataset once it
                 * stops being active
                 */
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            while (activeDatasets.containsKey(eldest.getValue())
                                    && activeDatasets.get(eldest.getValue()) > 0) {
                                log.debug("Waiting to close dataset: " + eldest.getKey());
                                Thread.sleep(2_000L);
                            }
                            log.debug("Closing dataset: " + eldest.getKey());
                            closeDataset(eldest.getValue());
                        } catch (IOException e) {
                            log.error("Cannot close dataset", e);
                        } catch (InterruptedException e) {
                            log.error("Problem scheduling dataset closure", e);
                        }
                    }
                }).start();
                return true;
            }
            return false;
        };

    };

    /**
     * Opens the NetCDF dataset at the given location, using the dataset cache.
     * Once acquired, this should be marked as finished with by calling
     * {@link NetcdfDatasetAggregator#releaseDataset(NetcdfDataset)}
     * 
     * @param location
     *            The location of the data: a local NetCDF file, an NcML
     *            aggregation file or an OPeNDAP location, {@literal i.e.}
     *            anything that can be passed to
     *            NetcdfDataset.openDataset(location).
     * 
     * @return a {@link NetcdfDataset} object for accessing the data at the
     *         given location. This should NEVER be CLOSED. Instead
     *         {@link NetcdfDatasetAggregator#releaseDataset(NetcdfDataset)}
     *         MUST be called when the resource is finished with. This will
     *         allow the {@link NetcdfDatasetAggregator} to release it from the
     *         cache and close it if necessary. To re-obtain the same dataset,
     *         call this method again.
     * 
     * @throws IOException
     *             if there was an error reading from the data source.
     */
    public static synchronized NetcdfDataset getDataset(String location) throws IOException,
            EdalException {
        return getDataset(location, false);
    }

    /**
     * Opens the NetCDF dataset at the given location, using the dataset cache.
     * Once acquired, this should be marked as finished with by calling
     * {@link NetcdfDatasetAggregator#releaseDataset(NetcdfDataset)}
     * 
     * @param location
     *            The location of the data: a local NetCDF file, an NcML
     *            aggregation file or an OPeNDAP location, {@literal i.e.}
     *            anything that can be passed to
     *            NetcdfDataset.openDataset(location).
     * @param forceRefresh
     *            Set to <code>true</code> if cached data should be ignored
     * 
     * @return a {@link NetcdfDataset} object for accessing the data at the
     *         given location. This should NEVER be CLOSED. Instead
     *         {@link NetcdfDatasetAggregator#releaseDataset(NetcdfDataset)}
     *         MUST be called when the resource is finished with. This will
     *         allow the {@link NetcdfDatasetAggregator} to release it from the
     *         cache and close it if necessary. To re-obtain the same dataset,
     *         call this method again.
     * 
     * @throws IOException
     *             if there was an error reading from the data source.
     */
    public static synchronized NetcdfDataset getDataset(String location, boolean forceRefresh)
            throws IOException, EdalException {
        NetcdfDataset nc;
        if (datasetCache.containsKey(location) && !forceRefresh) {
            nc = datasetCache.get(location);
        } else {
            if (isRemote(location)) {
                /*
                 * We have a remote dataset
                 */
                nc = openDataset(location);
            } else {
                /*
                 * We have a local dataset
                 */
                List<File> files = null;
                try {
                    files = CdmUtils.expandGlobExpression(location);
                } catch (NullPointerException e) {
                    log.warn("NPE processing location: " + location);
                    throw e;
                }
                if (files.size() == 0) {
                    throw new EdalException("The location " + location
                            + " doesn't refer to any existing files.");
                }
                if (files.size() == 1) {
                    location = files.get(0).getAbsolutePath();
                    nc = openDataset(location);
                } else {
                    /*
                     * We have multiple files in a glob expression. We write
                     * some NcML and use the NetCDF aggregation libs to parse
                     * this into an aggregated dataset.
                     * 
                     * If we have already generated the ncML on a previous call,
                     * just use that.
                     */
                    String ncmlString;
                    if (ncmlStringCache.containsKey(location) && !forceRefresh) {
                        ncmlString = ncmlStringCache.get(location);
                    } else {
                        /*
                         * Find the name of the time dimension
                         */
                        NetcdfDataset first = getDataset(files.get(0).getAbsolutePath(),
                                forceRefresh);
                        if (first.getFileTypeId().startsWith("GRIB")) {
                            throw new EdalException("Cannot automatically aggregate GRIB files.");
                        }
                        String timeDimName = null;
                        for (Variable var : first.getVariables()) {
                            if (var.isCoordinateVariable()) {
                                for (Attribute attr : var.getAttributes()) {
                                    if (attr.getFullName().equalsIgnoreCase("units")
                                            && attr.getStringValue().contains(" since ")) {
                                        /*
                                         * This is the time dimension. Since
                                         * this is a co-ordinate variable, there
                                         * is only 1 dimension
                                         */
                                        Dimension timeDimension = var.getDimension(0);
                                        timeDimName = timeDimension.getFullName();
                                    }
                                }
                            }
                        }
                        releaseDataset(first);
                        if (timeDimName == null) {
                            throw new EdalException(
                                    "Cannot join multiple files without time dimensions");
                        }

                        /*
                         * Create a Map
                         */
                        Map<Long, Map<String, String>> time2vars2filename = new HashMap<>();
                        for (File file : files) {
                            NetcdfFile ncFile = null;
                            try {
                                ncFile = NetcdfFile.open(file.getAbsolutePath());
                                Variable timeVar = ncFile.findVariable(timeDimName);
                                String unitsString = timeVar.findAttribute("units")
                                        .getStringValue();
                                String[] unitsParts = unitsString.split(" since ");
                                long time = new DateUnit(timeVar.read().getDouble(0),
                                        unitsParts[0], DateUnit.getStandardOrISO(unitsParts[1]))
                                        .getDate().getTime();
                                if (!time2vars2filename.containsKey(time)) {
                                    Map<String, String> vars2filename = new HashMap<>();
                                    time2vars2filename.put(time, vars2filename);
                                }
                                List<Variable> variables = ncFile.getVariables();
                                String varNames = "";
                                for (Variable v : variables) {
                                    varNames += v.getFullName();
                                }
                                time2vars2filename.get(time).put(varNames, file.getAbsolutePath());
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                if (ncFile != null) {
                                    ncFile.close();
                                }
                            }
                        }

                        List<Long> times = new ArrayList<>(time2vars2filename.keySet());
                        Collections.sort(times);

                        /*
                         * Now create the NcML string and use it to create an
                         * aggregated dataset
                         */
                        StringBuffer ncmlStringBuffer = new StringBuffer();
                        ncmlStringBuffer
                                .append("<netcdf xmlns=\"http://www.unidata.ucar.edu/namespaces/netcdf/ncml-2.2\">");
                        ncmlStringBuffer.append("<aggregation dimName=\"" + timeDimName
                                + "\" type=\"joinExisting\">");
                        for (Long time : times) {
                            Map<String, String> vars2filename = time2vars2filename.get(time);
                            if (vars2filename.size() == 1) {
                                String filename = vars2filename.values().iterator().next();
                                ncmlStringBuffer.append("<netcdf location=\"" + filename + "\"/>");
                            } else {
                                ncmlStringBuffer.append("<netcdf><aggregation type=\"union\">");
                                for (Entry<String, String> entry : vars2filename.entrySet()) {
                                    ncmlStringBuffer.append("<netcdf location=\""
                                            + entry.getValue() + "\"/>");
                                }
                                ncmlStringBuffer.append("</aggregation></netcdf>");
                            }
                        }
                        ncmlStringBuffer.append("</aggregation>");
                        ncmlStringBuffer.append("</netcdf>");

                        ncmlString = ncmlStringBuffer.toString();
                        ncmlStringCache.put(location, ncmlString);
                    }
                    nc = NcMLReader.readNcML(new StringReader(ncmlString), null);
                }
            }
            datasetCache.put(location, nc);
        }
        /*
         * Mark this dataset as active. It will not be removed from the cache
         * until it is marked as finished with, with the releaseDataset() method
         */
        if (!activeDatasets.containsKey(nc)) {
            activeDatasets.put(nc, 1);
        } else {
            activeDatasets.put(nc, activeDatasets.get(nc) + 1);
        }
        log.debug(location + " has " + activeDatasets.get(nc) + " active connections");
        return nc;
    }

    /**
     * Mark a {@link NetcdfDataset} as inactive. This means that it may be
     * removed from the cache in the event that the cache fills up. Reacquiring
     * the dataset with {@link NetcdfDatasetAggregator#getDataset(String)} will
     * mark it as active again.
     * 
     * @param dataset
     *            The {@link NetcdfDataset} which is no longer (immediately)
     *            required.
     */
    public static synchronized void releaseDataset(NetcdfDataset dataset) {
        if (activeDatasets.containsKey(dataset)) {
            Integer count = activeDatasets.get(dataset);
            if (count == 1) {
                activeDatasets.remove(dataset);
                log.debug(dataset.getLocation() + " has no active connections");
            } else {
                activeDatasets.put(dataset, count - 1);
                log.debug(dataset.getLocation() + " has " + activeDatasets.get(dataset)
                        + " active connections");
            }
        } else {
            if (dataset != null) {
                log.warn("Dataset "
                        + dataset.getLocation()
                        + " is not in active dataset list but has been asked to be released!  This is not harmful in itself but may indicate a coding error whereby a dataset has been marked to be released from the cache multiple times.");
            }
        }
    }

    /**
     * Opens the NetCDF dataset at the given location, using the dataset cache
     * if {@code location} represents an NcML aggregation. We cannot use the
     * cache for OPeNDAP or single NetCDF files because the underlying data may
     * have changed and the NetcdfDataset cache may cache a dataset forever. In
     * the case of NcML we rely on the fact that server administrators ought to
     * have set a "recheckEvery" parameter for NcML aggregations that may change
     * with time. It is desirable to use the dataset cache for NcML aggregations
     * because they can be time-consuming to assemble and we don't want to do
     * this every time a map is drawn.
     * 
     * @param location
     *            The location of the data: a local NetCDF file, an NcML
     *            aggregation file or an OPeNDAP location, {@literal i.e.}
     *            anything that can be passed to
     *            NetcdfDataset.openDataset(location).
     * 
     * @return a {@link NetcdfDataset} object for accessing the data at the
     *         given location.
     * 
     * @throws IOException
     *             if there was an error reading from the data source.
     */
    private static NetcdfDataset openDataset(String location) throws DataReadingException {
        NetcdfDataset nc;
        try {
            if (isNcmlAggregation(location)) {
                /*
                 * We use the cache of NetcdfDatasets to read NcML aggregations
                 * as they can be time-consuming to put together. If the
                 * underlying data can change we rely on the server admin
                 * setting the "recheckEvery" parameter in the aggregation file.
                 */
                if (!isRemote(location)) {
                    location = "file://" + location;
                }
                nc = NetcdfDataset.acquireDataset(new DatasetUrl(ServiceType.NCML, location), true,
                        null);
            } else {
                /*
                 * For local single files and OPeNDAP datasets we don't use the
                 * cache, to ensure that we are always reading the most
                 * up-to-date data. There is a small possibility that the
                 * dataset cache will have swallowed up all available file
                 * handles, in which case the server admin will need to increase
                 * the number of available handles on the server.
                 */
                nc = NetcdfDataset.openDataset(location);
            }
        } catch (IOException e) {
            throw new DataReadingException("Problem reading underlying NetCDF dataset", e);
        }
        return nc;
    }

    private static boolean isRemote(String location) {
        return location.startsWith("dods://") || location.startsWith("http://");
    }

    /**
     * @param location
     *            The location to test
     * @return true if the given location represents an NcML aggregation.
     *         dataset. This method simply checks to see if the location string
     *         ends with ".xml" or ".ncml", following the same procedure as the
     *         Java NetCDF library.
     */
    private static boolean isNcmlAggregation(String location) {
        return location.endsWith(".xml") || location.endsWith(".ncml");
    }

    /**
     * Closes the given dataset, logging any exceptions at debug level
     * 
     * @param nc
     *            The {@link NetcdfDataset} to close
     * @throws IOException
     *             if there is a problem closing the underlying dataset
     */
    private static void closeDataset(NetcdfDataset nc) throws IOException {
        if (nc == null)
            return;
        nc.close();
    }
}
