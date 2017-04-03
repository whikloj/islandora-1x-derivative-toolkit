package ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class IslandoraObject {

    public enum FedoraStates {
        Active('A'), Inactive('I'), Deleted('D');

        private char stateCharacter;

        private static Map<Character, FedoraStates> FORMAT_MAP = Stream
                .of(FedoraStates.values())
                .collect(Collectors.toMap(s -> s.stateCharacter,
                        Function.<FedoraStates> identity()));

        FedoraStates(char state) {
            this.stateCharacter = state;
        }

        FedoraStates(Character state) {
            this.stateCharacter = state.charValue();
        }

        FedoraStates(String state) {
            this.stateCharacter = state.toCharArray()[0];
        }

        @JsonCreator
        public static FedoraStates fromString(String string) {
            if (string == null) {
                throw new IllegalArgumentException(String.format(
                        "(%s) has no corresponding FedoraState, use one of A, I or D",
                        string));
            }
            return FedoraStates.fromChar(string.charAt(0));
        }

        @JsonCreator
        public static FedoraStates fromChar(char theChar) {
            FedoraStates state = FORMAT_MAP.get(theChar);
            if (state == null) {
                throw new IllegalArgumentException(String.format(
                        "(%s) has no corresponding FedoraState, use one of A, I or D",
                        theChar));
            }
            return state;
        }
    }

    private String pid;
    private String label;
    private String owner;
    @JsonProperty(value = "models")
    private Set<String> contentModels;
    private FedoraStates state = FedoraStates.Active;
    private Date created;
    private Date modified;
    private IslandoraDatastream[] datastreams;
    private IslandoraDerivativeMapping[] derivativeMap;

    /**
     * @return the pid
     */
    public String getPid() {
        return pid;
    }

    /**
     * @param pid
     *            the pid to set
     */
    public void setPid(String pid) {
        this.pid = pid;
    }

    /**
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * @param label
     *            the label to set
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * @return the owner
     */
    public String getOwner() {
        return owner;
    }

    /**
     * @param owner
     *            the owner to set
     */
    public void setOwner(String owner) {
        this.owner = owner;
    }

    /**
     * @return the contentModels
     */
    public Set<String> getContentModels() {
        return contentModels;
    }

    /**
     * @param contentModels
     *            the contentModels to set
     */
    public void setContentModels(Set<String> contentModels) {
        this.contentModels = contentModels;
    }

    /**
     * @return the state
     */
    public FedoraStates getState() {
        return state;
    }

    /**
     * @param state
     *            the state to set
     */
    public void setState(FedoraStates state) {
        this.state = state;
    }

    /**
     * @param stateChar
     *            the character abbreviation for state
     */
    public void setState(char stateChar) {
        state = FedoraStates.fromChar(stateChar);
    }

    /**
     * @param stateChar
     *            the character abbreviation for state
     */
    public void setState(String stateChar) {
        state = FedoraStates.fromString(stateChar);
    }

    /**
     * @return the created
     */
    public Date getCreated() {
        return created;
    }

    /**
     * @param created
     *            the created to set
     */
    public void setCreated(Date created) {
        this.created = created;
    }

    /**
     * @return the modified
     */
    public Date getModified() {
        return modified;
    }

    /**
     * @param modified
     *            the modified to set
     */
    public void setModified(Date modified) {
        this.modified = modified;
    }

    /**
     * @return Islandora derivative information objects
     */
    public IslandoraDatastream[] getDatastreams() {
        return datastreams;
    }

    /**
     * @param streams
     *            array of IslandoraDatastream
     */
    public void setDatastreams(IslandoraDatastream[] streams) {
        datastreams = streams;
    }

    /**
     * @return derivative map from Islandora
     */
    public IslandoraDerivativeMapping[] getDerivativeMap() {
        return derivativeMap;
    }

    /**
     * @param map
     *            the Islandora derivative map
     */
    public void setDerivativeMap(IslandoraDerivativeMapping[] map) {
        this.derivativeMap = map;
    }

}
