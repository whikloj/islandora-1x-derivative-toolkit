package ca.umanitoba.dam.islandora.derivativetoolkit.gatekeeper;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.slf4j.Logger;
import org.w3c.dom.Document;

public class ValidHeaderPredicate implements Predicate {

    private static final Logger LOGGER = getLogger(ValidHeaderPredicate.class);

    protected static final Map<String, String> namespaces = new HashMap<>(){{
        put("atom", "http://www.w3.org/2005/Atom");
        put("fedora-types", "http://www.fedora.info/definitions/1/0/types/");
    }
    };

    protected static NsContext context;

    protected static final XPathFactory xPathfactory = XPathFactory.newInstance();

    protected static final XPath xpath = xPathfactory.newXPath();

    /**
     * Constructor
     */
    public ValidHeaderPredicate() {
        context = this.new NsContext(namespaces);
        xpath.setNamespaceContext(context);
    }

    @Override
    public boolean matches(final Exchange exchange) {
        final Message inMessage = exchange.getIn();
        if (inMessage.hasHeaders() && inMessage.getHeader("methodName", String.class) != null) {
            final String methodName = inMessage
                    .getHeader("methodName", String.class);
            LOGGER.debug("Message has methodName header ({})", methodName);
            if (methodName.equalsIgnoreCase("ingest")) {
                LOGGER.debug("return true");
                return true;
            }
            final String dsID = getDSIDs(inMessage.getBody(Document.class));
            LOGGER.debug("Message has DSID ({})", dsID);
            if ((methodName.equals("addDatastream") || methodName.equals("modifyDatastreamByReference")) &&
                    dsID != null && dsID.equalsIgnoreCase("OBJ")) {
                LOGGER.debug("return true");
                return true;
            }
        }
        LOGGER.debug("return false");
        return false;
    }

    /**
     * Parse the XML body of the event message for elements. Elements have format
     * <category term="OBJ" scheme="fedora-types:dsID" label= "xsd:string"></category>
     *
     * @param xmlBody The JMS Message
     * @return string match of DSID
     * @throws XPathExpressionException
     */
    private String getDSIDs(final Document xmlBody) {

        LOGGER.debug("message is {}", xmlBody.toString());
        try {
            final XPathExpression expr = xpath.compile(
                    "//atom:category[@scheme=\"fedora-types:dsID\"]/@term");
            return (String) expr.evaluate(xmlBody, XPathConstants.STRING);
        } catch (final XPathExpressionException e) {
            LOGGER.trace("Did not find category in message {}: {}",
                    xmlBody,
                    e.getMessage());
        }
        return null;
    }

    /**
     * Simple namespace context inner class.
     *
     * @author whikloj
     */
    class NsContext implements NamespaceContext {

        private final Map<String, String> PREF_MAP = new HashMap<>();

        public NsContext(final Map<String, String> prefMap) {
            PREF_MAP.putAll(prefMap);
        }

        @Override
        public String getNamespaceURI(final String prefix) {
            return PREF_MAP.get(prefix);
        }

        @Override
        public String getPrefix(final String namespaceURI) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<String> getPrefixes(final String namespaceURI) {
            throw new UnsupportedOperationException();
        }

    }

}
