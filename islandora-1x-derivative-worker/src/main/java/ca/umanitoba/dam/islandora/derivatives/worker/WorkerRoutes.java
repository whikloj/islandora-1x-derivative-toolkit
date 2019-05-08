package ca.umanitoba.dam.islandora.derivatives.worker;

import static org.apache.camel.Exchange.ACCEPT_CONTENT_TYPE;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.apache.camel.Exchange.FILE_NAME;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.ERROR;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.LoggingLevel.WARN;
import static org.apache.camel.builder.PredicateBuilder.and;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_ARGS;
import static org.apache.camel.component.exec.ExecBinding.EXEC_EXIT_VALUE;
import static org.apache.camel.component.exec.ExecBinding.EXEC_STDERR;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_WORKING_DIR;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.PropertyInject;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.exec.ExecResult;
import org.apache.http.Consts;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.slf4j.Logger;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import net.minidev.json.JSONArray;


public class WorkerRoutes extends RouteBuilder {

    private static final Logger LOGGER = getLogger(WorkerRoutes.class);

    private static final String TESS_OPTS_HEADER = "TesseractOptions";

    private static final String FEDORA_DS_LABEL = "UmlFedoraDsLabel";

    private static final String HEADER_FILENAME = "UmlFedoraFileName";

    private static final String HEADER_PROCESS_FILE = "UmlFedoraCurrentFile";

    @PropertyInject("jms.prefetchSize")
    private String prefetchSize;

    /**
     * Map DSID to content type.
     */
    private static final Map<String, String> destinationContentType;

    /**
     * Map DSID to expected tesseract file extensions.
     */
    private static final Map<String, String> outputFileExtensions;

    static {
        destinationContentType = new HashMap<>();
        outputFileExtensions = new HashMap<>();
    }

    @Override
    public void configure() throws Exception {
        destinationContentType.put("OCR", "text/plain");
        destinationContentType.put("HOCR", "application/xml");

        outputFileExtensions.put("OCR", "txt");
        outputFileExtensions.put("HOCR", "hocr");

        final String activemq_options = (Integer.valueOf(prefetchSize) > 0 ? "?destination.consumer.prefetchSize=" +
                prefetchSize : "");

        // Error/Redelivery handler
        onException(Exception.class)
            .maximumRedeliveries("{{error.maxRedeliveries}}")
            .handled(true)
            .log(ERROR, LOGGER,
                "Error with message ${property.message}: ${exception.message}\n\n${exception.stacktrace}");

        /**
         * Queue subscriber.
         */
        from("{{input.queue}}" + activemq_options)
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
                        final HashMap<String, Object> props = exchange.getIn().getBody(HashMap.class);
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
            .setHeader(TESS_OPTS_HEADER).constant("-c tessedit_create_hocr=1 -c tessedit_create_txt=1")
            .to("direct:doTesseract")
            .setProperty("destination_dsid").constant("OCR")
            .setHeader(FEDORA_DS_LABEL, constant("OCR datastream"))
            .to("direct:putDSID")
            .setProperty("destination_dsid").constant("HOCR")
            .setHeader(FEDORA_DS_LABEL, constant("JOCR datastream"))
            .to("direct:generateHOCR");

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
            .setHeader(TESS_OPTS_HEADER).constant("-c tessedit_create_hocr=1 -c tessedit_create_txt=0")
            .to("direct:doTesseract")
            .setHeader(FEDORA_DS_LABEL, constant("HOCR datastream"))
            .to("direct:putDSID");

        /**
         * Execute tesseract with the provided parameters.
         */
        from("direct:doTesseract").routeId("UmlDerivativeWorkerTesseract")
            .setHeader(EXEC_COMMAND_WORKING_DIR, exchangeProperty("temporary.directory"))
            .process(exchange -> {
                final String cmdOptions = cleanTesseractOptions(exchange);
                LOGGER.debug("Additional Tesseract options are ({})", cmdOptions);
                exchange.getIn().setHeader(EXEC_COMMAND_ARGS, header(HEADER_FILENAME) + " " + header(HEADER_FILENAME) + " " + cmdOptions);
            })
            .removeHeaders("CamelHttp*")
            .to("exec:{{tesseract.path}}")
            .choice()
            .when(body().isInstanceOf(ExecResult.class))
                .to("direct:makeGreyTiff")
                .to("direct:doTesseract")
            .when(header(EXEC_EXIT_VALUE).not().isEqualTo(0))
                .log(ERROR, "Problem creating HOCR - ${header." + EXEC_STDERR + "}")
                .endChoice()
            .end()
            .setHeader(EXEC_COMMAND_ARGS).header(HEADER_FILENAME)
            .to("exec:rm");

        /**
         * Make a Greyscale version of the Tiff without an alpha channel.
         */
        from("direct:makeGreyTiff")
            .description("Make a greyscale Tiff for Tesseract")
            .log(DEBUG, "Making a Tiff greyscale for ${property[PID]}")
            .setBody(constant(null))
                .setHeader(EXEC_COMMAND_ARGS, simple("${header[" + HEADER_FILENAME +
                        "]} -alpha Off -set colorspace Gray ${header[" + HEADER_FILENAME + "]}_2"))
                .to("exec:{{convert.path}}")
                .filter(header(EXEC_EXIT_VALUE).not().isEqualTo(0))
                .log(ERROR, "Problem creating Greyscale Tiff - ${header." + EXEC_STDERR + "}")
                .setHeader(EXEC_COMMAND_ARGS, simple("${header[" + HEADER_FILENAME + "]}_2 ${header[" +
                        HEADER_FILENAME + "]}"))
                .to("exec:mv")
                .filter(header(EXEC_EXIT_VALUE).not().isEqualTo(0))
                .log(ERROR, "Problem creating Greyscale Tiff - ${header." + EXEC_STDERR + "}");

        /**
         * Get the source file from Fedora.
         */
        from("direct:getSourceFile")
            .routeId("UmlDerivativeWorkerHeadSource")
            .errorHandler(deadLetterChannel("direct:failedSource")
                .useOriginalMessage()
                .maximumRedeliveries(10)
                .maximumRedeliveryDelay(30000))
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
            .to("log:ca.umanitoba.dam.islandora.derivatives.worker?level=TRACE&showHeaders=true&showBody=false")
            .setHeader(FILE_NAME, simple("${property[pid]}.replace(':', '_'"))
            .setHeader(HEADER_FILENAME).header(FILE_NAME)
            .to("file:{{temporary.directory}}");


        from("direct:putDSID")
            .routeId("UmlDerivativeWorkerUpload")
            .errorHandler(deadLetterChannel("direct:failedUpload")
                .useOriginalMessage()
                .maximumRedeliveries(10)
                .maximumRedeliveryDelay(30000))
            .streamCaching()
                .process(exchange -> {
                    final String output = exchange.getIn().getBody(String.class);
                    exchange.setProperty("FileHolder", output);
                })
                // .setProperty("FileHolder", body())
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
                    final String tempDirectory = exchange.getProperty("temporary.directory", String.class);
                final String baseFileName = exchange.getProperty(HEADER_FILENAME, String.class);
                final String dest_dsid = exchange.getProperty("destination_dsid", String.class);
                    final String uploadFileName = baseFileName + "." + outputFileExtensions.get(dest_dsid);
                    exchange.setProperty(HEADER_PROCESS_FILE, uploadFileName);
                final String outputMime = destinationContentType.get(dest_dsid);
                if (outputMime == null) {
                    throw new RuntimeCamelException(
                        String.format("Unable to PUT DSID (%s) without content-type mapping", dest_dsid));
                }
                exchange.getIn().setHeader(CONTENT_TYPE, outputMime);
                //final String stream = exchange.getProperty("FileHolder", String.class);
                    final File uploadFile = new File(tempDirectory + "/" + uploadFileName);
                if (!uploadFile.exists() || !uploadFile.canRead()) {
                        throw new FileNotFoundException(String.format("Cannot find or access file %s", tempDirectory +
                                "/" + uploadFile));
                }
                final FileEntity entity = new FileEntity(
                    uploadFile,
                    ContentType.create(exchange.getIn().getHeader(CONTENT_TYPE, String.class), Consts.UTF_8)
                );
                //final StringEntity entity = new StringEntity(
                //    stream,
                //    ContentType.create(exchange.getIn().getHeader(CONTENT_TYPE, String.class), Consts.UTF_8));
                entity.setContentType(outputMime);
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
            .end()
                .setHeader(EXEC_COMMAND_WORKING_DIR).simple("{{temporary.directory}}")
                .setHeader(EXEC_COMMAND_ARGS).header(HEADER_PROCESS_FILE)
                .to("exec:rm");

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
