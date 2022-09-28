package ca.umanitoba.dam.islandora.derivativetoolkit.gatekeeper;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.PropertyInject;
import org.slf4j.Logger;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;

public class IslandoraInfoFilter implements Predicate {

    private static final Logger LOGGER = getLogger(IslandoraInfoFilter.class);

    @PropertyInject(value = "gatekeeper.process_dsids")
    private String process_dsid;

    @PropertyInject(value = "gatekeeper.process_contentTypes")
    private String process_contentTypes;

    private Set<String> validDSIDs;

    private Set<String> validTypes;

    @Override
    public boolean matches(Exchange exchange) {
        final String pid = exchange.getProperty("PID", String.class);
        LOGGER.trace("PID is {}", pid);
        LOGGER.trace("process_dsids is {}", process_dsid);
        LOGGER.trace("process_contentTypes is {}", process_contentTypes);
        validDSIDs = new HashSet<>(Arrays.asList(process_dsid.split(",")));
        validTypes = new HashSet<>(Arrays.asList(process_contentTypes.split(",")));
        final String json = exchange.getIn().getBody(String.class);
        final ReadContext ctx = JsonPath.parse(json);
        final boolean result = (matchDSIDs(ctx) && matchContentTypes(ctx));
        if (result) {
            final Map<String, Object> headers = exchange.getIn().getHeaders();
            headers.remove("X-Islandora-Process-Dsids");
            headers.put("X-Islandora-Process-Dsids", validDSIDs.stream().collect(Collectors.joining(",")));
            LOGGER.info("Object {} matches DSID/ContentModel requirements, sending to derivative workers", pid);
        } else {
            LOGGER.info("Object {} does not match DSID/ContentModel requirements, exiting.", pid);
        }
        return result;
    }

    /**
     * Match DSIDs from the configuration properties against the incoming message.
     *
     * @param json Parsed JSON message.
     * @return True if there is a matching DSID in the message.
     */
    private boolean matchDSIDs(final ReadContext json) {
        final Set<String> receivedDSIDs = new HashSet<>(json.read("$.derivative_info[*].destination_dsid"));
        LOGGER.trace("DSIDs received ({})", receivedDSIDs);
        LOGGER.trace("valid DSIDs ({})", validDSIDs);
        validDSIDs.retainAll(receivedDSIDs);
        LOGGER.trace("Set of DSIDs returned ({})", validDSIDs);
        return (validDSIDs.size() > 0);
    }

    /**
     * Match contentModels from the configuration properties against the incoming message.
     *
     * @param json Parsed JSON message.
     * @return True if there is a matching content model in the message.
     */
    private boolean matchContentTypes(final ReadContext json) {
        final Set<String> objectTypes = new HashSet<>(json.read("$.object_info.models"));

        LOGGER.trace("contentTypes retrieved ({})", objectTypes);
        LOGGER.trace("valid contentTypes ({})", validTypes);
        validTypes.retainAll(objectTypes);
        LOGGER.trace("set of types returned ({})", validTypes);
        return (validTypes.size() > 0);
    }
}

