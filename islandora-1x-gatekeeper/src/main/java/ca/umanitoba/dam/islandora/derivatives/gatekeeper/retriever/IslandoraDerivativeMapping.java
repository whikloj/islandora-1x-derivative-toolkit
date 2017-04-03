package ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({ "function", "file", "weight" })
public class IslandoraDerivativeMapping {

    private String source_dsid;
    private String destination_dsid;
    private int weight;
    /**
     * @return the source_dsid
     */
    public String getSource_dsid() {
        return source_dsid;
    }
    /**
     * @param source_dsid the source_dsid to set
     */
    public void setSource_dsid(String source_dsid) {
        this.source_dsid = source_dsid;
    }
    /**
     * @return the destination_dsid
     */
    public String getDestination_dsid() {
        return destination_dsid;
    }
    /**
     * @param destination_dsid the destination_dsid to set
     */
    public void setDestination_dsid(String destination_dsid) {
        this.destination_dsid = destination_dsid;
    }
    /**
     * @return the weight
     */
    public int getWeight() {
        return weight;
    }
    /**
     * @param weight the weight to set
     */
    public void setWeight(int weight) {
        this.weight = weight;
    }

}
