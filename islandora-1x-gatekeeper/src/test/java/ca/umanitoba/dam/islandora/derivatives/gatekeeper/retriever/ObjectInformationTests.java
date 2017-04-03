package ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever;

import static org.junit.Assert.assertEquals;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.apache.http.localserver.LocalServerTestBase;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;

@RunWith(MockitoJUnitRunner.class)
public class ObjectInformationTests extends LocalServerTestBase {
    private static final Logger LOGGER = getLogger(
            ObjectInformationTests.class);

    @Test(expected = ClientAccessException.class)
    public void testGetObjectInfoAuthenticationFail() throws Exception {
        String id = "test:123";
        serverBootstrap.registerHandler("/islandora/user/login",
                new HttpRequestHandler() {
                    @Override
                    public void handle(HttpRequest request,
                            HttpResponse response, HttpContext context)
                            throws HttpException, IOException {
                        response.setStatusCode(HttpStatus.SC_OK);
                        response.setHeader("Set-Cookie",
                                "FakeAuthCookie=FakeAuthToken");
                    }
                });
        serverBootstrap.registerHandler(
                "/islandora/rest/v1/object/" + id + "/full_info",
                new HttpRequestHandler() {
                    @Override
                    public void handle(HttpRequest request,
                            HttpResponse response, HttpContext context)
                            throws HttpException, IOException {
                        response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
                    }
                });
        // start the server
        start();
        String testHost = "http://" + server.getInetAddress().getHostName()
                + ":" + server.getLocalPort();

        ObjectInformationRetriever retriever = new ObjectInformationRetriever();
        retriever.setHostname(testHost);
        retriever.getTypes("test:123");
    }

    @Test
    public void testGetObjectInfoAuthenticationOk() throws Exception {
        String id = "test:123";
        final String sessID = UUID.randomUUID().toString().replaceAll("-", "");
        final String sessVal = UUID.randomUUID().toString().replaceAll("-", "");
        final String sCookie = sessID + "=" + sessVal;
        InputStream responseBody = getClass()
                .getResourceAsStream(
                "/rest_responses/large_image_w_OCR.json");
        String responseString = IOUtils.toString(responseBody);

        Set<String> expectedTypes = new HashSet<String>(Arrays.asList(
            "islandora:sp_large_image_cmodel", "fedora-system:FedoraObject-3.0"));

        serverBootstrap.registerHandler("/islandora/user/login",
                new HttpRequestHandler() {
                    @Override
                    public void handle(HttpRequest request,
                            HttpResponse response, HttpContext context)
                            throws HttpException, IOException {
                        response.setStatusCode(HttpStatus.SC_OK);
                        response.addHeader("Set-Cookie",
                                sCookie
                                        + "; Max-Age=2000000; path=/; HttpOnly");
                        context.setAttribute("testLogin", "testLoginValue");
                    }
                });
        serverBootstrap.registerHandler(
                "/islandora/rest/v1/object/" + id + "/full_info",
                new HttpRequestHandler() {
                    @Override
                    public void handle(HttpRequest request,
                            HttpResponse response, HttpContext context)
                            throws HttpException, IOException {
                        boolean authorized = false;
                        for (Header h : request.getAllHeaders()) {
                            if (h.getName().equalsIgnoreCase("cookie")
                                    && h.getValue().equals(sCookie)) {
                                authorized = true;
                            }
                        }
                        if (authorized) {
                            response.setStatusCode(HttpStatus.SC_OK);
                            response.setEntity(
                                    new StringEntity(responseString));
                        } else {
                            response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
                        }
                    }
                });
        // start the server
        start();
        String testHost = "http://" + server.getInetAddress().getHostName()
                + ":" + server.getLocalPort();
        ObjectInformationRetriever retriever = new ObjectInformationRetriever();
        retriever.setHostname(testHost);
        Set<String> types = retriever.getTypes("test:123");
        assertEquals("Did not get expected types", expectedTypes, types);
    }

    @Test
    public void testGetDerivativeMapAuthenticationOk() throws Exception {
        String id = "test:123";
        final String sessID = UUID.randomUUID().toString().replaceAll("-", "");
        final String sessVal = UUID.randomUUID().toString().replaceAll("-", "");
        final String sCookie = sessID + "=" + sessVal;
        InputStream responseBody = getClass()
                .getResourceAsStream(
                "/rest_responses/large_image_w_OCR.json");
        String responseString = IOUtils.toString(responseBody);

        Map<String, String> expectedMap = new HashMap<String, String>()
        {
            private static final long serialVersionUID = 1L;
            {
                put("TN", "OBJ");
                put("TECHMD", "OBJ");
                put("JPG", "OBJ");
                put("JP2", "OBJ");
                put("OCR", "OBJ");
                put("HOCR", "OBJ");
            }
        };

        serverBootstrap.registerHandler("/islandora/user/login",
                new HttpRequestHandler() {
                    @Override
                    public void handle(HttpRequest request,
                            HttpResponse response, HttpContext context)
                            throws HttpException, IOException {
                        response.setStatusCode(HttpStatus.SC_OK);
                        response.addHeader("Set-Cookie",
                                sCookie
                                        + "; Max-Age=2000000; path=/; HttpOnly");
                        context.setAttribute("testLogin", "testLoginValue");
                    }
                });
        serverBootstrap.registerHandler(
                "/islandora/rest/v1/object/" + id + "/full_info",
                new HttpRequestHandler() {
                    @Override
                    public void handle(HttpRequest request,
                            HttpResponse response, HttpContext context)
                            throws HttpException, IOException {
                        boolean authorized = false;
                        for (Header h : request.getAllHeaders()) {
                            if (h.getName().equalsIgnoreCase("cookie")
                                    && h.getValue().equals(sCookie)) {
                                authorized = true;
                            }
                        }
                        if (authorized) {
                            response.setStatusCode(HttpStatus.SC_OK);
                            response.setEntity(
                                    new StringEntity(responseString));
                        } else {
                            LOGGER.debug("Not authorized");
                            response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
                        }
                    }
                });
        // start the server
        start();
        String testHost = "http://" + server.getInetAddress().getHostName()
                + ":" + server.getLocalPort();
        ObjectInformationRetriever retriever = new ObjectInformationRetriever();
        retriever.setHostname(testHost);
        Map<String, String> derivatives = retriever
                .getDerivativeMap("test:123");
        assertEquals("Did not get expected types", expectedMap, derivatives);
    }

}
