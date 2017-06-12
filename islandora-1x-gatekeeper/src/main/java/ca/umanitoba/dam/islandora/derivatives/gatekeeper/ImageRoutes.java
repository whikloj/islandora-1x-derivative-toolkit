package ca.umanitoba.dam.islandora.derivatives.gatekeeper;

import static org.apache.camel.Exchange.ACCEPT_CONTENT_TYPE;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.ERROR;
import static org.apache.camel.LoggingLevel.TRACE;
import static org.apache.camel.LoggingLevel.WARN;
import static org.apache.camel.builder.PredicateBuilder.and;
import static org.apache.camel.builder.PredicateBuilder.or;
import static org.slf4j.LoggerFactory.getLogger;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.BeanInject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;

public class ImageRoutes extends RouteBuilder {

    // Logger
    private static final Logger LOGGER = getLogger(ImageRoutes.class);

    private static final String XCSRF_Token = "X-CSRF-Token";

    private static final String SESSION_NAME = "DrupalSessionName";

    private static final String SESSION_ID = "DrupalSessionId";

    private static final String SESSION_TOKEN = "DrupalSessionToken";

    private static final String SESSION_COOKIE = "DrupalSessionCookie";

    private ObjectMapper mapper = new ObjectMapper();

    @PropertyInject(value = "gatekeeper.process_dsids")
    private String process_dsids;

    @PropertyInject(value = "error.maxRedeliveries")
    private int maxRedeliveries;

    @BeanInject(value = "staticStore")
    protected StaticMap staticStore;

    @Override
    public void configure() throws Exception {

        onException(Exception.class)
        .maximumRedeliveries("{{error.maxRedeliveries}}")
        .handled(true)
        .log(
            ERROR,
            LOGGER,
            "Error processing object through gatekeeper: ${exception.message}\n\n${exception.stacktrace}"
        );

		/**
		 * Input queue and main route.
		 */
		from("{{input.queue}}")
            .routeId("UmlDerivativeQueueConsumer")
            .to("seda:mainRouter");

        /**
         * Do the main routing.
         */
        from("seda:mainRouter?blockWhenFull=true&concurrentConsumers={{concurrent.consumers}}")
            .routeId("UmlDerivativeMainRouter")
            .streamCaching()
            .log(DEBUG, LOGGER, "Received message on input queue for ${headers[pid]}")
            .startupOrder(15)
            .filter(method("validInbound"))
            .log(TRACE, LOGGER, "Has valid headers")
            .setProperty("PID", header("PID"))
            .removeHeaders("*")
            .to("direct:getObjectInfo")
            .log(TRACE, LOGGER, "Got information ${body}")
            .filter(method("infoFilter"))
            .to("direct:formatOutput")
            .end();

		/**
		 * Get the resource's information from Islandora.
		 */
		from("direct:getObjectInfo")
		    .routeId("UmlDerivativeGetInfo")
            .startupOrder(10)
            .log(TRACE, LOGGER, "in direct:getObjectInfo")
            .errorHandler(deadLetterChannel("direct:getObjectInfo-error")
                .useOriginalMessage()
                .maximumRedeliveries(maxRedeliveries))
            .filter(new Predicate() {

                @Override
                public boolean matches(final Exchange exchange) {
                    if (exchange.getProperty(SESSION_TOKEN, String.class) == null &&
                        staticStore.containsKey(SESSION_TOKEN)) {
                        exchange.setProperty(SESSION_TOKEN, staticStore.get(SESSION_TOKEN));
                    }
                    return (exchange.getProperty(SESSION_TOKEN) == null);
                }
            })
               .log(TRACE, LOGGER, "Going to login to Drupal first")
               .to("direct:drupalLogin")
               .end()

            .removeHeaders("*")
            .setHeader("Accept", constant("application/json"))
            .setHeader(XCSRF_Token, exchangeProperty(SESSION_TOKEN))
            .setHeader(HTTP_METHOD, constant("GET"))
            .setHeader(HTTP_URI, simple("{{islandora.hostname}}{{islandora.basepath}}{{islandora.rest.infoUri}}"))
            .process(new Processor() {

                @Override
                public void process(final Exchange exchange) throws Exception {
                    final String pid = URLEncoder.encode(exchange.getProperty("PID", String.class), "UTF-8");
                    final String uri = exchange.getIn().getHeader(HTTP_URI, String.class).replaceAll("%PID%", pid);
                    exchange.getIn().setHeader(HTTP_URI, uri);
                }
            })

            .log(DEBUG, LOGGER, "HTTP_URI is (${headers[CamelHttpUri]})")
            .to("http4://localhost?throwExceptionOnFailure=false")
            .log(DEBUG, LOGGER, "RESPONSE CODE is (${headers[CamelHttpResponseCode]})")
            .choice()
                .when(and(or(header(HTTP_RESPONSE_CODE).isEqualTo(403), header(HTTP_RESPONSE_CODE).isEqualTo(401)),
                exchangeProperty("loginCompleted").isEqualTo(true)))
                    .log(ERROR, LOGGER, "Can't get information for ${property[PID]} from Islandora.")
                    .stop()
                .when(and(or(header(HTTP_RESPONSE_CODE).isEqualTo(403), header(HTTP_RESPONSE_CODE).isEqualTo(401)),
                exchangeProperty("loginCompleted").isNull()))
                    .log(DEBUG, LOGGER, "Got a ${header[CamelHttpResponseCode]}, logging into Islandora")
                    .to("direct:drupalLogin")
                    .to("direct:getObjectInfo")
                .when(header(HTTP_RESPONSE_CODE).isEqualTo(404))
                    .log(WARN, LOGGER, "Object ${property[PID]} was not found in Islandora, received a ${header[CamelHttpResponseCode]}")
                    .stop()
                .when(header(HTTP_RESPONSE_CODE).not().isEqualTo(200))
                    .log(ERROR, LOGGER, "Unexpected response from Islandora, ${header[CamelHttpResponseCode]}: ${header[CamelHttpResponseText]}")
                    .stop()
            .end();

		/**
		 * Log dead messages and store them.
		 */
		from("direct:getObjectInfo-error")
		    .routeId("UmlGetObjectInfoError")
		    .log(WARN, LOGGER, "Cannot get object information for PID ${property[PID]}, leaving in {{objectInfo.dead.queue}}")
		    .to("{{objectInfo.dead.queue}}");


        /**
         * Log in to Islandora to get the required cookies.
         */
		from("direct:drupalLogin")
		  .routeId("UmlDerivativeLogin")
          .log(TRACE, LOGGER, "in drupalLogin")
          .to("direct:getToken")
          .setHeader(CONTENT_TYPE, constant("application/json"))
          .setHeader(ACCEPT_CONTENT_TYPE, constant("application/json"))
          .setHeader(HTTP_METHOD, constant("POST"))
            .setBody(simple("{ \"username\": \"{{islandora.username}}\", \"password\": \"{{islandora.password}}\"}"))
          .setHeader(HTTP_URI, simple("{{islandora.hostname}}{{islandora.login.service}}/user/login"))
          .log(DEBUG, LOGGER, "Login to URI ${header[CamelHttpUri]}")
          .to("http4://localhost?throwExceptionOnFailure=false")
          .setProperty("loginCompleted", constant(true))
          .convertBodyTo(String.class, "UTF-8")
          .log(TRACE, LOGGER, "body is ${body}")
          .log(DEBUG,  LOGGER, "Got a ${header[CamelHttpResponseCode]} from login")
          .choice()
            .when(header(HTTP_RESPONSE_CODE).isEqualTo(200))
                 .process(new Processor(){
                     @Override
                    public void process(final Exchange exchange){
                         final String json = exchange.getIn().getBody(String.class);
                         final ReadContext ctx = JsonPath.parse(json);
                         final String sessionName = ctx.read("$.session_name");
                         final String sessionId = ctx.read("$.sessid");
                         final String sessionToken = ctx.read("$.token");
                         exchange.setProperty(SESSION_NAME, sessionName);
                         exchange.setProperty(SESSION_ID, sessionId);
                         exchange.setProperty(SESSION_TOKEN, sessionToken);
                         exchange.setProperty(SESSION_COOKIE, sessionName + "=" + sessionId);
                         LOGGER.debug(
                             "Drupal login return ID ({}), name ({}), token ({})",
                             sessionId,
                             sessionName,
                             sessionToken
                         );
                     }
            })
            .when(header(HTTP_RESPONSE_CODE).isEqualTo(406))
                // 406 is you are already logged in, need to logout first
                .log(DEBUG, LOGGER, "Received 406, need to logout first")
                .removeHeaders("HttpCamel*")
                .removeProperty("loginComplete")
                .setHeader(HTTP_METHOD, constant("POST"))
                .setHeader(HTTP_URI, simple("{{islandora.hostname}}{{islandora.login.service}}/user/logout"))
                .to("http4://localhost?throwExceptionOnFailure=true")
                .to("direct:drupalLogin")
            .otherwise()
                .log(ERROR, LOGGER,
                "Could not login to Islandora, received a (${header[CamelHttpResponseCode]})")
                 .stop();

        /**
         * Get a X-CSRF-Token
         */
		from("direct:getToken")
		.routeId("UmlDerivativeGateToken")
		.removeHeaders("*")
            .setHeader(HTTP_URI, simple("{{islandora.hostname}}/services/session/token"))
		.setHeader(HTTP_METHOD, constant("GET"))
		.to("http4://localhost?throwExceptionOnFailure=false")
		.choice()
		  .when(header(HTTP_RESPONSE_CODE).isEqualTo(200))
		    .convertBodyTo(String.class)
		    .removeHeaders("*")
            .process(new Processor() {

                @Override
                public void process(final Exchange exchange) {
                    staticStore.put(SESSION_TOKEN, exchange.getIn().getBody(String.class));
                }
            })
		    .setHeader(XCSRF_Token, body())
		    .log(DEBUG, LOGGER, "Got session token ${header[X-CSRF-Token]}")
		  .otherwise()
		    .log(ERROR, LOGGER, "Could not get session token, HTTP response ${header[CamelHttpResponseCode]}")
		    .stop();

        /**
         * Take the message (which should be object information) and form a small json message for output.
         */
        from("direct:formatOutput").routeId("UmlDerivativeFormatOutput")
            .log(DEBUG, LOGGER, "Formatting output message")
            .process(new Processor() {

                @Override
                public void process(final Exchange exchange) throws Exception {
                    // Transform to a new smaller message for our workers.
                    final Set<String> dsidSet = new HashSet<String>(Arrays.asList(process_dsids.split(",")));
                    final String json = exchange.getIn().getBody(String.class);
                    LOGGER.trace("json body is {}", json);
                    final ReadContext ctx = JsonPath.parse(json);

                    final String pid = ctx.read("$.object_info.pid");
                    final List<Map<String, Object>> derivative_map = ctx.read("$.derivative_info[*]");
                    LOGGER.trace("derivative map is {}", derivative_map);
                    List<Map<String, Object>> clean_map = new ArrayList<Map<String, Object>>();

                    for (Map<String, Object> e : derivative_map) {
                        if (dsidSet.contains(e.get("destination_dsid"))) {
                            e.remove("weight");
                            e.remove("function");
                            e.remove("file");
                            clean_map.add(e);
                        }
                    }
                    try {
                        String map = mapper.writeValueAsString(clean_map);
                        LOGGER.trace("map is ({})", map);
                        String outputJson = String.format("{ \"pid\": \"%s\", \"derivatives\" : %s }", pid, map);
                        exchange.getIn().setBody(outputJson);
                        LOGGER.debug("Outputting message {}", exchange.getIn().getBody(String.class));
                        exchange.getIn().setHeader(CONTENT_TYPE, "application/json");
                    } catch (JsonProcessingException e) {
                        LOGGER.error("Error writing out formatted JSON, {}", e);
                        throw e;
                    }
                }
            }).to("{{output.queue}}");
    }
}
