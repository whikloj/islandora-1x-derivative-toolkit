package ca.umanitoba.dam.islandora.derivatives.worker;

import static org.apache.camel.Exchange.ACCEPT_CONTENT_TYPE;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.ERROR;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.LoggingLevel.WARN;
import static org.apache.camel.builder.PredicateBuilder.and;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_ARGS;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_WORKING_DIR;
import static org.apache.camel.component.exec.ExecBinding.EXEC_EXIT_VALUE;
import static org.apache.camel.component.exec.ExecBinding.EXEC_STDERR;
import static org.slf4j.LoggerFactory.getLogger;

import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.Consts;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import net.minidev.json.JSONArray;


public class WorkerRoutes extends RouteBuilder {

    private static final Logger LOGGER = getLogger(WorkerRoutes.class);

    private static final String TESS_OPTS_HEADER = "TesseractOptions";

    private static final String FEDORA_DS_LABEL = "UmlFedoraDsLabel";

    private HashMap<String, String> destinationContentType;

    @Override
    public void configure() throws Exception {
        destinationContentType = new HashMap<String, String>();
        destinationContentType.put("OCR", "text/plain");
        destinationContentType.put("HOCR", "application/xml");

        // Error/Redelivery handler
        onException(Exception.class)
            .maximumRedeliveries("{{error.maxRedeliveries}}")
            .handled(true)
            .log(ERROR, LOGGER,
                "Error with message ${property.message}: ${exception.message}\n\n${exception.stacktrace}");

        /**
         * Queue subscriber.
         */
        from("{{input.queue}}")
            .routeId("UmlDerivativeWorkerMain")
            .setProperty("pid").jsonpath("$.pid")
            .log(DEBUG, LOGGER, "Processing pid ${headers.pid}")
            .choice()
                .when(exchange -> {
                    final String json = exchange.getIn().getBody(String.class);
                    final DocumentContext ctx = JsonPath.parse(json);
                    final JSONArray dsids = ctx.read("$.derivatives[*].destination_dsid");
                    final Set<String> destinations = dsids.stream().peek(t -> LOGGER.debug("Peek {}", t))
                        .map(String::valueOf).collect(Collectors.toSet());
                    LOGGER.trace("ContentType {}", destinationContentType);
                    final Set<String> HocrAndOcr = destinationContentType.keySet();
                    LOGGER.trace("destinations {}", destinations);
                    LOGGER.trace("HocrAndOcr {}", HocrAndOcr);
                    HocrAndOcr.retainAll(destinations);
                    LOGGER.trace("returning {}", HocrAndOcr);
                    return (HocrAndOcr.size() == 2);
                }).to("direct:generateBoth").endChoice()
                .otherwise()
                    .split().jsonpath("$.derivatives[*]")
                    .log(DEBUG, LOGGER, "split result is ${body}")
                    .process(exchange -> {
                        @SuppressWarnings("unchecked")
                        HashMap<String, Object> props = exchange.getIn().getBody(HashMap.class);
                        exchange.getIn().setHeaders(props);
                        exchange.getIn().setBody("", String.class);
                })
                    .choice()
                        .when(simple("${headers[destination_dsid]} == 'HOCR'"))
                            .to("direct:getSourceFile").to("direct:generateHOCR")
                        .when(simple("${headers[destination_dsid]} == 'OCR'"))
                            .to("direct:getSourceFile").to("direct:generateOCR")
                        .otherwise()
                            .log(WARN, LOGGER, "Message got a request for derivative (${headers[destination_dsid]})")
                    .endChoice()
                .end();

        /**
         * Do both HOCR and OCR for this resource.
         */
        from("direct:generateBoth").routeId("UmlDerivativeWorkerBoth")
            .setProperty("source_dsid").constant("OBJ")
            .to("direct:getSourceFile")
            .streamCaching()
            .multicast().to("direct:generateHOCR", "direct:generateOCR");

        /**
         * Do OCR for this resource.
         */
        from("direct:generateOCR").routeId("UmlDerivativeWorkerOCR")
            .setExchangePattern(ExchangePattern.InOnly)
            .setProperty("source_dsid").constant("OBJ")
            .setProperty("destination_dsid").constant("OCR")
            .removeHeader(TESS_OPTS_HEADER)
            .to("direct:doTesseract")
            .setHeader(FEDORA_DS_LABEL, constant("OCR datastream"))
            .to("direct:putDSID");

        /**
         * Do HOCR for this resource.
         */
        from("direct:generateHOCR").routeId("UmlDerivativeWorkerHOCR")
            .setExchangePattern(ExchangePattern.InOnly)
            .setProperty("source_dsid").constant("OBJ")
            .setProperty("destination_dsid").constant("HOCR")
            .removeHeader(TESS_OPTS_HEADER)
            .setHeader(TESS_OPTS_HEADER).constant("hocr")
            .to("direct:doTesseract")
            .setHeader(FEDORA_DS_LABEL, constant("HOCR datastream"))
            .to("direct:putDSID");

        /**
         * Execute tesseract with the provided parameters.
         */
        from("direct:doTesseract").routeId("UmlDerivativeWorkerTesseract")
            .process(exchange -> {
                final String cmdOptions = cleanTesseractOptions(exchange);
                LOGGER.debug("Additional Tesseract options are ({})", cmdOptions);
                exchange.getIn().setHeader(EXEC_COMMAND_ARGS, " stdin stdout " + cmdOptions);
            })
            .removeHeaders("CamelHttp*")
            .to("exec:{{tesseract.path}}")
            .filter(header(EXEC_EXIT_VALUE).not().isEqualTo(0))
            .log(ERROR, "Problem creating HOCR - ${header." + EXEC_STDERR + "}")
            // .to("direct:makeGreyTiff")
            // .to("direct:OcrFromGray")
            // .removeHeaders("*")
            // .setHeader(EXEC_COMMAND_WORKING_DIR, simple("${property.workingDir}"))
            // .setBody(constant(null))
            // .setHeader(EXEC_COMMAND_ARGS, simple(" ${property.workingDir}/OBJ_gray.tiff"))
            // .to("exec:/bin/rm")
            .end();

        /**
         * Make a Greyscale version of the Tiff
         */
        from("direct:makeGreyTiff")
            .description("Make a greyscale Tiff for Tesseract")
            .log(DEBUG, "Making a Tiff greyscale for ${property[PID]}")
            .removeHeaders("*")
            .setHeader(EXEC_COMMAND_WORKING_DIR, simple("${property.workingDir}"))
            .setBody(constant(null))
            .setHeader(EXEC_COMMAND_ARGS,
                simple("${property.tiffFile} -colorspace gray -quality 100 ${property.workingDir}/OBJ_gray.tiff"))
            .to("exec:{{apps.convert}}")
            .filter(header(EXEC_EXIT_VALUE).not().isEqualTo(0))
            .log(ERROR, "Problem creating Greyscale Tiff - ${header." + EXEC_STDERR + "}");

        /**
         * Get the source file from Fedora.
         */
        from("direct:getSourceFile")
            .routeId("UmlDerivativeWorkerHeadSource")
            .streamCaching()
            .removeHeaders("CamelHttp*")
            .removeHeader(ACCEPT_CONTENT_TYPE)
            .setHeader(HTTP_URI,
                simple("{{fedora.url}}/objects/${property[pid]}/datastreams/${property[source_dsid]}/content"))
            .setHeader(HTTP_METHOD).constant("HEAD")
            .to("http4://localhost?authUsername={{fedora.authUsername}}" +
                "&authPassword={{fedora.authPassword}}&throwExceptionOnFailure=false")
            .to("log:ca.umanitoba.dam.islandora.derivatives.worker?level=TRACE&showHeaders=true")
            .choice()
                .when(and(header(HTTP_RESPONSE_CODE).isEqualTo(200),header(CONTENT_TYPE).startsWith("image/")))
                    .log(INFO, LOGGER, "Image Processing ${headers[CamelHttpPath]}")
                    .to("direct:doDownload")
                .otherwise()
                    .log(WARN, LOGGER, "Cannot process an item with Content-Type ${header[Content-Type]}")
                    .throwException(
                        new RuntimeCamelException(
                    String.format("Cannot deal with a non-image on item %s DSID %s", exchangeProperty("pid"),
                        exchangeProperty("destination_dsid"))
                        )
                    )
            .end();


        from("direct:doDownload")
            .routeId("UmlDerivativeWorkerGetSource")
            .removeHeaders("CamelHttp*")
            .setHeader(HTTP_URI,
                simple("{{fedora.url}}/objects/${property[pid]}/datastreams/${property[source_dsid]}/content"))
            .setHeader(HTTP_METHOD).constant("GET")
            .to("http4://localhost?authUsername={{fedora.authUsername}}" +
                "&authPassword={{fedora.authPassword}}&throwExceptionOnFailure=false")
            .to("log:ca.umanitoba.dam.islandora.derivatives.worker?level=TRACE&showHeaders=true&showBody=false");


        from("direct:putDSID")
            .routeId("UmlDerivativeWorkerUpload")
            .streamCaching()
            .setProperty("FileHolder", body())
            .setHeader(HTTP_URI,
                simple("{{fedora.url}}/objects/${property[pid]}/datastreams/${property[destination_dsid]}"))
            .setHeader(HTTP_METHOD).constant("HEAD")
            .to("http4://localhost?authUsername={{fedora.authUsername}}" +
                "&authPassword={{fedora.authPassword}}&throwExceptionOnFailure=false")
            .to("log:ca.umanitoba.dam.islandora.derivatives.worker?level=TRACE&showHeaders=true")
            .choice()
                .when(header(HTTP_RESPONSE_CODE).isEqualTo(401))
                    .log(ERROR, LOGGER, "Received a 401 Unauthorized on HEAD request to ${headers[CamelHttpUri]}")
                    .throwException(RuntimeCamelException.class, "Received 401 response on HEAD request")
                .when(header(HTTP_RESPONSE_CODE).isEqualTo(200))
                    .removeHeaders("CamelHttp*")
                    .removeHeader(FEDORA_DS_LABEL)
                    .setHeader(HTTP_URI,
                        simple("{{fedora.url}}/objects/${property[pid]}/datastreams/${property[destination_dsid]}"))
                    .setHeader(HTTP_METHOD).constant("PUT")
                .endChoice()
                .otherwise()
                    .removeHeaders("CamelHttp*")
                    .setHeader(HTTP_URI,
                        simple("{{fedora.url}}/objects/${property[pid]}/datastreams/${property[destination_dsid]}"))
                    .process(exchange -> {
                        // Add the datastream label and ControlGroup if this is our first time.
                        final String label = exchange.getIn().getHeader(FEDORA_DS_LABEL, String.class);
                        final String uri = exchange.getIn().getHeader(HTTP_URI, String.class);
                        if (label != null && label.length() > 0) {
                            final String new_uri = uri + "?controlGroup=M&dsLabel=" + URLEncoder.encode(label, "UTF-8");
                            exchange.getIn().setHeader(HTTP_URI, new_uri);
                        } else {
                            final String new_uri = uri + "?controlGroup=M";
                            exchange.getIn().setHeader(HTTP_URI, new_uri);
                        }
                    })
                    .removeHeader(FEDORA_DS_LABEL)
                    .setHeader(HTTP_METHOD).constant("POST")
            .end()
            .log(DEBUG, LOGGER, "Uploading file with a ${headers[CamelHttpMethod]}")
            .process(exchange -> {
                final String dest_dsid = exchange.getProperty("destination_dsid", String.class);
                final String outputMime = destinationContentType.get(dest_dsid);
                if (outputMime == null) {
                    throw new RuntimeCamelException(
                        String.format("Unable to PUT DSID (%s) without content-type mapping", dest_dsid));
                }
                exchange.getIn().setHeader(CONTENT_TYPE, outputMime);
                    final String stream = exchange.getProperty("FileHolder", String.class);
                    final StringEntity entity = new StringEntity(
                        stream,
                        ContentType.create(exchange.getIn().getHeader(CONTENT_TYPE, String.class), Consts.UTF_8));
                    exchange.getIn().setBody(entity);
            })
            .removeProperty("FileHolder")
            .to("log:ca.umanitoba.dam.islandora.derivatives.worker?level=TRACE&showHeaders=true")
            .to("http4://localhost?authUsername={{fedora.authUsername}}" +
            "&authPassword={{fedora.authPassword}}&throwExceptionOnFailure=true")
            .to("log:ca.umanitoba.dam.islandora.derivatives.worker?level=TRACE&showHeaders=true")
            .choice()
                .when(header(HTTP_RESPONSE_CODE).startsWith("20"))
                .log(INFO, LOGGER, "Added/Updated dsid ${property[destination_dsid]} on item ${property[pid]}")
            .otherwise()
                .log(ERROR, LOGGER, "Did not publish dsid ${property[destination_dsid]} on item ${property[pid]}")
            .end();

    }

    // Hack - cmdline options are often an array of repeated elements. Fix that
    @SuppressWarnings("unchecked")
    static final String cleanTesseractOptions(final Exchange e) {
        final Object optHdr = e.getIn().getHeader(TESS_OPTS_HEADER);
        if (optHdr instanceof Collection) {
            return String.join(" ", new HashSet<>((Collection<String>) optHdr));
        } else if (optHdr != null) {
            return (String) optHdr;
        }
        return "";
    }

}
