package uk.ac.rdg.resc.edal.wms;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import uk.ac.rdg.resc.edal.wms.exceptions.WmsException;

/**
 * Class that contains the parameters of the user's request. Parameter names are
 * not case sensitive.
 * 
 * @author Jon Blower
 * @author Guy
 */
public class RequestParams {
    private Map<String, String> paramMap = new HashMap<String, String>();

    /**
     * Creates a new RequestParams object from the given Map of parameter names
     * and values (normally gained from HttpServletRequest.getParameterMap()).
     * The Map matches parameter names (Strings) to parameter values (String
     * arrays).
     */
    public RequestParams(Map<?, ?> httpRequestParamMap) {
        @SuppressWarnings("unchecked")
        Map<String, String[]> httpParamMap = (Map<String, String[]>) httpRequestParamMap;

        for (String name : httpParamMap.keySet()) {
            String[] values = httpParamMap.get(name);
            assert values.length >= 1;
            try {
                String key = URLDecoder.decode(name.trim(), "UTF-8").toLowerCase();
                String value = URLDecoder.decode(values[0].trim(), "UTF-8");
                this.paramMap.put(key, value);
            } catch (UnsupportedEncodingException uee) {
                /* Shouldn't happen: UTF-8 should always be supported */
                throw new AssertionError(uee);
            }
        }
    }

    /**
     * Returns the value of the parameter with the given name as a String, or
     * null if the parameter does not have a value. This method is not sensitive
     * to the case of the parameter name. Use getWmsVersion() to get the
     * requested WMS version.
     */
    public String getString(String paramName) {
        return paramMap.get(paramName.toLowerCase());
    }

    /**
     * Returns the value of the parameter with the given name, throwing a
     * WmsException if the parameter does not exist. Use
     * getMandatoryWmsVersion() to get the requested WMS version.
     */
    public String getMandatoryString(String paramName) throws WmsException {
        String value = this.getString(paramName);
        if (value == null) {
            throw new WmsException("Must provide a value for parameter " + paramName.toUpperCase());
        }
        return value;
    }

    /**
     * Finds the WMS version that the user has requested. This looks for both
     * WMTVER and VERSION, the latter taking precedence. WMTVER is used by older
     * versions of WMS and older clients may use this in version negotiation.
     * 
     * @return The request WMS version as a string, or null if not set
     */
    public String getWmsVersion() {
        String version = this.getString("version");
        if (version == null) {
            version = this.getString("wmtver");
        }
        return version;
    }

    /**
     * Finds the WMS version that the user has requested, throwing a
     * WmsException if a version has not been set.
     * 
     * @return The request WMS version as a string
     * @throws WmsException
     *             if neither VERSION nor WMTVER have been requested
     */
    public String getMandatoryWmsVersion() throws WmsException {
        String version = this.getWmsVersion();
        if (version == null) {
            throw new WmsException("Must provide a value for VERSION");
        }
        return version;
    }

    /**
     * Returns the value of the parameter with the given name as a positive
     * integer, or the provided default if no parameter with the given name has
     * been supplied. Throws a WmsException if the parameter does not exist or
     * if the value is not a valid positive integer. Zero is counted as a
     * positive integer.
     */
    public int getPositiveInt(String paramName, int defaultValue) throws WmsException {
        String value = this.getString(paramName);
        if (value == null)
            return defaultValue;
        return parsePositiveInt(paramName, value);
    }

    /**
     * Returns the value of the parameter with the given name as a positive
     * integer, throwing a WmsException if the parameter does not exist or if
     * the value is not a valid positive integer. Zero is counted as a positive
     * integer.
     */
    public int getMandatoryPositiveInt(String paramName) throws WmsException {
        String value = this.getString(paramName);
        if (value == null) {
            throw new WmsException("Must provide a value for parameter " + paramName.toUpperCase());
        }
        return parsePositiveInt(paramName, value);
    }

    private static int parsePositiveInt(String paramName, String value) throws WmsException {
        try {
            int i = Integer.parseInt(value);
            if (i < 0) {
                throw new WmsException("Parameter " + paramName.toUpperCase()
                        + " must be a valid positive integer");
            }
            return i;
        } catch (NumberFormatException nfe) {
            throw new WmsException("Parameter " + paramName.toUpperCase()
                    + " must be a valid positive integer");
        }
    }

    /**
     * Returns the value of the parameter with the given name, or the supplied
     * default value if the parameter does not exist.
     */
    public String getString(String paramName, String defaultValue) {
        String value = this.getString(paramName);
        if (value == null)
            return defaultValue;
        return value;
    }

    /**
     * Returns the value of the parameter with the given name as a boolean
     * value, or the provided default if no parameter with the given name has
     * been supplied.
     * 
     * @throws WmsException
     *             if the value is not a valid boolean string ("true" or
     *             "false", case-insensitive).
     */
    public boolean getBoolean(String paramName, boolean defaultValue) throws WmsException {
        String value = this.getString(paramName);
        if (value == null)
            return defaultValue;
        value = value.trim();
        if ("true".equalsIgnoreCase(value))
            return true;
        if ("false".equalsIgnoreCase(value))
            return false;
        throw new WmsException("Invalid boolean value for parameter " + paramName);
    }

    /**
     * Returns the value of the parameter with the given name, or the supplied
     * default value if the parameter does not exist.
     * 
     * @throws WmsException
     *             if the value of the parameter is not a valid floating-point
     *             number
     */
    public float getFloat(String paramName, float defaultValue) throws WmsException {
        String value = this.getString(paramName);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException nfe) {
            throw new WmsException("Parameter " + paramName.toUpperCase()
                    + " must be a valid floating-point number");
        }
    }

}
