package ca.umanitoba.dam.islandora.derivatives.gatekeeper;

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

        LOGGER.trace("process_dsids is {}", process_dsid);
        LOGGER.trace("process_contentTypes is {}", process_contentTypes);
        validDSIDs = new HashSet<String>(Arrays.asList(process_dsid.split(",")));
        validTypes = new HashSet<String>(Arrays.asList(process_contentTypes.split(",")));
        String json = exchange.getIn().getBody(String.class);
        ReadContext ctx = JsonPath.parse(json);
        boolean result = (matchDSIDs(ctx) && matchContentTypes(ctx));
        if (result) {
            Map<String, Object> headers = exchange.getOut().getHeaders();
            headers.remove("X-Islandora-Process-Dsids");
            headers.put("X-Islandora-Process-Dsids", validDSIDs.stream().collect(Collectors.joining(",")));
            exchange.getOut().setBody(exchange.getIn().getBody());
            LOGGER.info("Object matches DSID/ContentModel requirements, sending to derivative workers");
        } else {
            LOGGER.info("Object does not match DSID/ContentModel requirements, exiting.");
        }
        return result;
    }

    /**
     * Match DSIDs from the configuration properties against the incoming message.
     *
     * @param json Parsed JSON message.
     * @return True if there is a matching DSID in the message.
     */
    private boolean matchDSIDs(ReadContext json) {
        Set<String> receivedDSIDs = new HashSet<String>(json.read("$.derivative_info[*].destination_dsid"));
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
    private boolean matchContentTypes(ReadContext json) {
        Set<String> objectTypes = new HashSet<String>(json.read("$.object_info.models"));

        LOGGER.trace("contentTypes retrieved ({})", objectTypes);
        LOGGER.trace("valid contentTypes ({})", validTypes);
        validTypes.retainAll(objectTypes);
        LOGGER.trace("set of types returned ({})", validTypes);
        return (validTypes.size() > 0);
    }
}
