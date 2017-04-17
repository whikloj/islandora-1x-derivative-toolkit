package ca.umanitoba.dam.islandora.derivatives.worker;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.ERROR;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.LoggingLevel.WARN;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_ARGS;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import net.minidev.json.JSONArray;


public class WorkerRoutes extends RouteBuilder {

    private static final Logger LOGGER = getLogger(WorkerRoutes.class);

    private static final String TESS_OPTS_HEADER = "TesseractOptions";

    private final HashMap<String, String> destinationContentType;

    public WorkerRoutes() {
        destinationContentType = new HashMap<String, String>();
        destinationContentType.put("OCR", "text/plain");
        destinationContentType.put("HOCR", "text/html");
    };

    @Override
    public void configure() throws Exception {

        // Error/Redelivery handler
        onException(Exception.class)
            .maximumRedeliveries("{{error.maxRedeliveries}}")
            .handled(true)
            .log(ERROR, LOGGER,
                "Error with message ${property.message}: ${exception.message}\n\n${exception.stacktrace}");

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
                LOGGER.debug("ContentType {}", destinationContentType);
                final Set<String> HocrAndOcr = destinationContentType.keySet();
                LOGGER.debug("destinations {}", destinations);
                LOGGER.debug("HocrAndOcr {}", HocrAndOcr);
                HocrAndOcr.retainAll(destinations);
                LOGGER.debug("returning {}", HocrAndOcr);
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

        from("direct:generateBoth").routeId("UmlDerivativeWorkerBoth")
            .to("direct:getSourceFile")
            .streamCaching()
            .to("direct:generateHOCR", "direct:generateOCR");

        from("direct:generateOCR").routeId("UmlDerivativeWorkerOCR")
            .setProperty("source_dsid").constant("OBJ")
            .setProperty("destination_dsid").constant("OCR")
            .removeHeader(TESS_OPTS_HEADER)
            .to("direct:doTesseract")
            .to("direct:putDSID");

        from("direct:generateHOCR").routeId("UmlDerivativeWorkerHOCR")
            .setProperty("source_dsid").constant("OBJ")
            .setProperty("destination_dsid").constant("HOCR")
            .removeHeader(TESS_OPTS_HEADER)
            .setHeader(TESS_OPTS_HEADER).constant("hocr")
            .to("direct:doTesseract")
            .to("direct:putDSID");

        from("direct:doTesseract").routeId("UmlDerivativeWorkerTesseract")
            .process(exchange -> {
                final String cmdOptions = cleanTesseractOptions(exchange);
                LOGGER.debug("Additional Tesseract options are ({})", cmdOptions);
                exchange.getIn().setHeader(EXEC_COMMAND_ARGS, " stdin stdout " + cmdOptions);
            })
            .removeHeaders("CamelHttp*")
            .to("exec:{{tesseract.path}}")
            .process(
                exchange -> exchange.getOut().setBody(exchange.getIn().getBody(InputStream.class), InputStream.class));

        from("direct:getSourceFile")
            .routeId("UmlDerivativeWorkerHeadSource")
            .removeHeaders("CamelHttp*")
            .setHeader(HTTP_URI, simple("/objects/${headers[pid]}/datastreams/${headers[source_dsid]}/content"))
            .setHeader(HTTP_METHOD).constant("HEAD")
            .to("{{fedora.url}}?authUsername={{fedora.authUsername}}" +
                "&authPassword={{fedora.authPassword}}&throwExceptionOnFailure=false")
            .choice()
                .when(header(CONTENT_TYPE).startsWith("image/"))
                    .log(INFO, LOGGER, "Image Processing ${headers[CamelHttpPath]}")
                    .to("direct:doDownload")
                .otherwise()
                    .log(WARN, LOGGER, "Cannot process an item with Content-Type ${headers[CamelHttpPath]}")
                    .throwException(
                        new RuntimeCamelException(
                    String.format("Cannot deal with a non-image on item %s DSID %s", header("pid"),
                        header("destination_dsid"))
                        )
                    )
            .end();


        from("direct:doDownload")
            .routeId("UmlDerivativeWorkerGetSource")
            .removeHeaders("CamelHttp*")
            .setHeader(HTTP_URI, simple("/objects/${headers[pid]}/datastreams/${headers[source_dsid]}/content"))
            .setHeader(HTTP_METHOD).constant("GET")
            .to("{{fedora.url}}?authUsername={{fedora.authUsername}}" +
                "&authPassword={{fedora.authPassword}}&throwExceptionOnFailure=false");


        from("direct:putDSID").routeId("UmlDerivativeWorkerUpload")
        .setHeader("FileHolder").body()
        .process(exchange -> {
            final String dest_dsid = exchange.getProperty("destination_dsid", String.class);
            final String outputMime = destinationContentType.get(dest_dsid);
            if (outputMime == null) {
                throw new RuntimeCamelException(String.format("Unable to PUT DSID (%s) without content-type mapping", dest_dsid));
            }
            exchange.getIn().setHeader(CONTENT_TYPE, outputMime);
        })
            .setHeader(HTTP_URI, simple("/objects/${headers[pid]}/datastreams/${headers[destination_dsid]}"))
        .setHeader(HTTP_METHOD).constant("HEAD")
            .to("{{fedora.url}}?authUsername={{fedora.authUsername}}" +
                "&authPassword={{fedora.authPassword}}&throwExceptionOnFailure=false")
        .choice()
            .when(header(HTTP_RESPONSE_CODE).isEqualTo(200))
                .removeHeaders("CamelHttp*")
                .setHeader(HTTP_METHOD).constant("PUT")
            .endChoice()
            .otherwise()
                .removeHeaders("CamelHttp*")
                .setHeader(HTTP_METHOD).constant("POST")
        .end()
            .setHeader(HTTP_URI, simple("/objects/${headers[pid]}/datastreams/${headers[destination_dsid]}"))
        .setBody(header("FileHolder"))
            .removeHeader("FileHolder")
            .to("{{fedora.url}}?authUsername={{fedora.authUsername}}" +
            "&authPassword={{fedora.authPassword}}&throwExceptionOnFailure=true")
        .choice()
            .when(header(HTTP_RESPONSE_CODE).startsWith("20"))
            .log(INFO, LOGGER, "Added/Updated dsid ${headers[destination_dsid]} on item ${headers[pid]}")
          .otherwise()
            .log(ERROR, LOGGER, "Did not publish dsid ${headers[destination_dsid]} on item ${headers[pid]}")
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
