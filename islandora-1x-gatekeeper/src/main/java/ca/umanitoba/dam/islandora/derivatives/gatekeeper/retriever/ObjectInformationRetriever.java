package ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Header;
import org.apache.camel.PropertyInject;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ObjectInformationRetriever
    implements ObjectInformationInterface {

    private static final Logger LOGGER = getLogger(ObjectInformationRetriever.class);


    protected CredentialsProvider credProvider = new BasicCredentialsProvider();

    protected CookieStore cookieStore = new BasicCookieStore();

    protected CloseableHttpClient httpClient;

    protected HttpClientContext context = HttpClientContext.create();

    protected ObjectMapper jsonMapper = new ObjectMapper();

    private IslandoraObject islandoraObject = null;

    /**
     * Islandora endpoint for providing JSON information about objects.
     */
    @PropertyInject(value = "islandora.rest.infoUri")
    private static final String OBJECT_INFORMATION_URI = "rest/v1/object/%/full_info";

    /**
     * Remote username from properties
     */
    @PropertyInject(value = "islandora.username")
    protected static String USERNAME;

    /**
     * Remote password from properties
     */
    @PropertyInject(value = "islandora.password")
    protected static String PASSWORD;

    /**
     * remote hostname string from properties
     */
    @PropertyInject(value = "islandora.hostname")
    protected String HOSTNAME = "http://localhost/";

    /**
     * remote islandora baseUrl, in case of multi-sites.
     */
    @PropertyInject(value = "islandora.basePath")
    protected String BASE_PATH = "islandora/";

    /**
     * URI of remote server's islandora instance
     */
    protected URI ISLANDORA_HOST;

    /**
     * Port on remote server
     */
    protected int SERVER_PORT;

    /**
     * Constructor
     */
    public ObjectInformationRetriever() {
        parseHostName();
        RequestConfig globalConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD).build();
        context.setCookieStore(cookieStore);
        context.setRequestConfig(globalConfig);
        httpClient = HttpClients.custom().setDefaultRequestConfig(globalConfig)
                .setDefaultCookieStore(cookieStore)
                .build();
    }

    /**
     * Inject a specific client configuration.
     *
     * @param client custom http client
     */
    public ObjectInformationRetriever(CloseableHttpClient client) {
        this();
        httpClient = client;
    }

    /**
     * @return the hostname
     */
    public String getHostname() {
        return HOSTNAME;
    }

    /**
     * @param hostname the hostname to set
     */
    public void setHostname(String hostname) {
        LOGGER.debug("Setting hostname to {}", hostname);
        HOSTNAME = hostname;
        parseHostName();
    }

    @Override
    public Set<String> getTypes(@Header("pid") final String id) {
        if (islandoraObject == null || !islandoraObject.getPid().equals(id)) {
            getInfo(id);
        }
        return islandoraObject.getContentModels();
    }

    @Override
    public Map<String, String> getDerivativeMap(
            @Header("pid") final String id) {
        Map<String, String> derivatives = new HashMap<String, String>();
        if (islandoraObject == null || !islandoraObject.getPid().equals(id)) {
            getInfo(id);
        }
        for (IslandoraDerivativeMapping m : islandoraObject
                .getDerivativeMap()) {
            derivatives.put(m.getDestination_dsid(), m.getSource_dsid());
        }
        return derivatives;
    }

    @Override
    public String getObjectJson(@Header("pid") final String id) {
        return getInfo(id);
    }

    /**
     * Perform our GET request with fail over to authenticate,
     *
     * @param request
     *            The request to execute
     * @return the response
     * @throws ClientAccessException
     *             wraps all exceptions and adds for 403 and persistant 401
     *
     */
    private String getResponseBody(HttpRequestBase request) {
        return getResponseBody(request, true);
    }

    /**
     * Perform our GET request with option for fail over to authenticate,
     *
     * @param request
     *            The request to execute
     * @param retry
     *            If we fail with 401 should we authenticate and try again
     * @return the response
     * @throws ClientAccessException
     *             wraps all exceptions and adds for 403 and persistant 401
     *
     */
    private String getResponseBody(HttpRequestBase request, boolean retry)
            throws ClientAccessException {

        try (CloseableHttpResponse response = httpClient.execute(request,
                context)) {
            int respCode = getStatus(response);
            LOGGER.debug("doGet with retry {} and responseCode {}",
                    (retry ? "true" : "false"), respCode);
            if (respCode == HttpStatus.SC_OK) {
                LOGGER.debug("got our response, entity length is {}",
                        response.getEntity().getContentLength());
                return IOUtils.toString(response.getEntity().getContent());
            } else if (respCode == HttpStatus.SC_UNAUTHORIZED) {
                if (retry) {
                    drupalLogin();
                    return getResponseBody(request, false);
                } else {
                    throw new ClientAccessException(
                            "Unable to login to remote system, check your settings. Received 401 Forbidden");
                }
            } else if (respCode == HttpStatus.SC_FORBIDDEN) {

                throw new ClientAccessException(
                        String.format(
                                "User not authorized at {}, returned 403 {}",
                                request.getURI().toString(),
                                response.getStatusLine().getReasonPhrase()));
            } else {
                throw new ClientAccessException(String.format(
                        "Unexpected response from request: {}", respCode));
            }
        } catch (ClientProtocolException e) {
            throw new ClientAccessException("Protocol error", e);
        } catch (IOException e) {
            throw new ClientAccessException("Error with closing response", e);
        }
    }

    /**
     * Login to remote Drupal site
     *
     * @throws ClientAccessException
     *             Wraps any exceptions or in the case of invalid credentials or
     *             unexpected results.
     */
    private void drupalLogin() throws ClientAccessException {
        HttpUriRequest login = RequestBuilder.post()
                .setUri(ISLANDORA_HOST.resolve("/islandora/user/login"))
                .addParameter("name", USERNAME).addParameter("pass", PASSWORD)
                .addParameter("form_id", "user_login")
                .addParameter("op", "Log in")
                .setHeader("Content-type", "multipart/form-data").build();
        try (CloseableHttpResponse response = httpClient.execute(login,
                context)) {
            int respCode = getStatus(response);
            if ((respCode == 200 || respCode == 302)
                    && response.containsHeader("Set-Cookie")) {
            } else if (respCode == 200 || respCode == 302) {
                throw new ClientAccessException(
                        "Authentication failed, check username/password.");
            } else {
                throw new ClientAccessException(
                        String.format("Could not authenticate, responded {} {}",
                                getStatus(response),
                                response.getStatusLine().getReasonPhrase()));
            }

        } catch (ClientProtocolException e) {
            throw new ClientAccessException("Error in HTTP request", e);
        } catch (IOException e) {
            throw new ClientAccessException(
                    "Error communicating with remote host", e);
        }
    }

    /**
     * Utility function to get status code
     *
     * @param response
     * @return the response code
     */
    private static int getStatus(CloseableHttpResponse response) {
        return response.getStatusLine().getStatusCode();
    }

    /**
     * Parse the provided HOSTNAME into a URI with the BASE_PATH for islandora.
     */
    private void parseHostName() {
        try {
            if (!BASE_PATH.endsWith("/")) {
                BASE_PATH = BASE_PATH + "/";
            }
            if (!HOSTNAME.endsWith("/") && !BASE_PATH.startsWith("/")) {
                ISLANDORA_HOST = new URI(HOSTNAME + "/" + BASE_PATH);
            } else {
                ISLANDORA_HOST = new URI(HOSTNAME + BASE_PATH);
            }
            LOGGER.debug("Set ISLANDORA_HOST to ({})",
                    ISLANDORA_HOST.toString());

        } catch (URISyntaxException e) {
            LOGGER.error("Could not parse hostname to URI {}", HOSTNAME);
            throw new ClientAccessException(String
                    .format("Could not parse hostname to URI {}", HOSTNAME), e);
        }
        SERVER_PORT = (ISLANDORA_HOST.getPort() > 0 ? ISLANDORA_HOST.getPort()
                : 80);
        HOSTNAME = ISLANDORA_HOST.getHost();
    }


    /**
     * Get the information from the remote server. Instantiates local objects with the information for later calls.
     *
     * @param id the PID of the object
     * @return string of the response body
     */
    public String getInfo(String id) {
        URI requestURI = ISLANDORA_HOST
                .resolve(OBJECT_INFORMATION_URI.replace("%", id));
        HttpGet request = new HttpGet(requestURI);
        request.setHeader("Accept", "application/json");
        String responseBody = getResponseBody(request);
        try {
            JsonNode responseObj = jsonMapper.readTree(responseBody);
            JsonNode objectInfo = responseObj.at("/object_info");
            JsonNode derivatives = responseObj.at("/derivative_info");
            IslandoraObject object = jsonMapper.readValue(objectInfo.toString(),
                    IslandoraObject.class);
            IslandoraDerivativeMapping[] map = jsonMapper.readValue(
                    derivatives.toString(),
                    IslandoraDerivativeMapping[].class);
            object.setDerivativeMap(map);
            this.islandoraObject = object;

        } catch (IOException e) {
            LOGGER.error("Error parsing/writing response from/to json: {}",
                    e.getMessage(), e);
        }
        return responseBody;

    }
}
