package ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever;

import java.util.Map;
import java.util.Set;

/**
 * To to retrieve information about the object for use in the tool.
 *
 * @author whikloj
 * @since 2017-03-15
 */
public interface ObjectInformationInterface {

    /**
     * Get the types/content-models of object identified by id
     *
     * @param id
     *            the object id on the remote system
     * @return a set of all types
     */
    public Set<String> getTypes(String id);

    /**
     * Get the types of derivatives and where they are created from.
     *
     * @param id
     *            the object id on the remote system
     * @return a map of derivative DSIDs and source DSIDs
     */
    public Map<String, String> getDerivativeMap(String id);

    /**
     * Get all the information from the Islandora REST endpoint and return it.
     *
     * @param id the object id on the remote system.
     * @return a JSON string.
     */
    public String getObjectJson(String id);
}
