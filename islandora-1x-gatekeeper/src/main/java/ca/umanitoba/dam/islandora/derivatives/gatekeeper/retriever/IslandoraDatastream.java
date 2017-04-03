package ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

import ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraObject.FedoraStates;

/**
 * @author whikloj
 *
 */
public class IslandoraDatastream {

    private static Logger LOGGER = getLogger(IslandoraDatastream.class);
    public enum controlGroups {
        InternalXmlContent('X'), ManagedContent(
                'M'), ExternallyReferencedContent(
                        'E'), RedirectReferencedContent('R');

        private char controlCharacter;

        private static Map<Character, controlGroups> FORMAT_MAP = Stream
                .of(controlGroups.values())
                .collect(Collectors.toMap(s -> s.controlCharacter,
                        Function.<controlGroups> identity()));

        controlGroups(char controlChar) {
            this.controlCharacter = controlChar;
        }

        controlGroups(Character controlChar) {
            this.controlCharacter = controlChar.charValue();
        }

        controlGroups(String controlChar) {
            this.controlCharacter = controlChar.charAt(0);
        }

        @JsonCreator
        public static controlGroups fromString(String string) {
            if (string == null) {
                throw new IllegalArgumentException(String.format(
                        "(%s) is not a valid code for controlGroups, use one of X, M, E or R",
                        string));
            }
            return controlGroups.fromChar(string.charAt(0));
        }

        @JsonCreator
        public static controlGroups fromChar(char theChar) {
            controlGroups control = FORMAT_MAP.get(theChar);
            if (control == null) {
                throw new IllegalArgumentException(String.format(
                        "(%s) is not a valid code for controlGroups, use one of X, M, E or R",
                        theChar));
            }
            return control;
        }
    }

    public enum checksumTypes {
        @JsonEnumDefaultValue
        DISABLED(null), MD5("MD5"), SHA1("SHA-1"), SHA256("SHA-256"), SHA385("SHA-385"), SHA512("SHA-512");
        // Not sure if I need to account for a DEFAULT setting in the datastream
        // history, probably not.
        // DEFAULT("MD5"),

        @JsonProperty("checksumType")
        private String checksumFormatted;

        private static Map<String, checksumTypes> FORMAT_MAP = Stream
                .of(checksumTypes.values())
                .collect(Collectors.toMap(s -> s.checksumFormatted,
                        Function.<checksumTypes> identity()));

        private checksumTypes(String type) {
            this.checksumFormatted = type;
        }

        @JsonCreator
        public static checksumTypes fromString(String string) {
            LOGGER.debug("string is ({}) and is null ({})", string,
                    (string == null ? "yes" : "no"));

            checksumTypes type = FORMAT_MAP.get(string);

            if (type == null) {
                throw new IllegalArgumentException(String.format(
                        "(%s) is not a valid code for checksumTypes, use one of MD5, SHA-1, SHA-256, SHA-385, SHA-512 or null",
                        string));
            }
            return type;
        }

    }

    @JsonProperty(value = "dsid")
    private String id;
    private String label;
    private int size;
    private String mimeType;
    private controlGroups controlGroup;
    private FedoraStates state;
    private Date created;
    private boolean versionable;
    private String checksum;
    private checksumTypes checksumTypeHolder;
    private IslandoraDatastream[] versions;

    /**
     * @return the datastream id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id
     *            the datastream id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * @param label
     *            the label
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * @return the datastream size
     */
    public int getSize() {
        return size;
    }

    /**
     * @param size
     *            the datastream size
     */
    public void setSize(int size) {
        this.size = size;
    }

    /**
     * @return the mime-type
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * @param mimeType
     *            the mime-type
     */
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * @return the control group
     */
    public controlGroups getControlGroup() {
        return controlGroup;
    }

    /**
     * @param controlChar
     *            character for a control group
     */
    public void setControlGroup(char controlChar) {
        controlGroup = controlGroups.fromChar(controlChar);
    }

    /**
     * @param controlChar
     *            character for a control group
     */
    public void setControlGroup(String controlChar) {
        controlGroup = controlGroups.fromString(controlChar);
    }

    /**
     * @param controlGroup
     *            object control group
     */
    public void setControlGroup(controlGroups controlGroup) {
        this.controlGroup = controlGroup;
    }

    /**
     * @return the object state
     */
    public FedoraStates getState() {
        return state;
    }

    /**
     * @param stateChar
     *            the character equivalent of state
     */
    public void setState(char stateChar) {
        switch (stateChar) {
        case 'A':
            this.state = FedoraStates.Active;
            break;
        case 'I':
            this.state = FedoraStates.Inactive;
            break;
        case 'D':
            this.state = FedoraStates.Deleted;
            break;
        default:
            throw new IllegalArgumentException(
                    "State can only be a FedoraStates enum or one of the characters (A, I or D)");
        }
    }

    /**
     * @param state
     *            the state
     */
    public void setState(FedoraStates state) {
        this.state = state;
    }

    /**
     * @return the created date
     */
    public Date getCreated() {
        return created;
    }

    /**
     * @param date
     *            created date
     */
    public void setCreated(Date date) {
        this.created = date;
    }

    /**
     * @return whether it is versionable
     */
    public boolean getVersionable() {
        return versionable;
    }

    /**
     * @param versionable
     *            versionable or not
     */
    public void setVersionable(boolean versionable) {
        this.versionable = versionable;
    }

    /**
     * @return the checksum
     */
    public String getChecksum() {
        return checksum;
    }

    /**
     * @param checksum
     *            the checksum
     */
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    /**
     * @return type of checksum
     */
    public checksumTypes getChecksumType() {
        return checksumTypeHolder;
    }

    /**
     * @param checksumType
     *            type of checksum
     */
    public void setChecksumType(checksumTypes checksumType) {
        this.checksumTypeHolder = checksumType;
    }

    /**
     * @param checksumType
     *            string of checksum type
     */
    public void setChecksumType(String checksumType) {
        checksumTypes checkType = checksumTypes.fromString(checksumType);
        setChecksumType(checkType);
    }

    /**
     * Handle null checksumType
     */
    public void setChecksumType() {
        setChecksumType(checksumTypes.DISABLED);
    }

    /**
     * @return array of datastream versions
     */
    public IslandoraDatastream[] getVersions() {
        return versions;
    }

    /**
     * @param index
     *            version index
     * @return the datastream at index
     */
    public IslandoraDatastream getVersionAt(int index) {
        return versions[index];
    }
}
