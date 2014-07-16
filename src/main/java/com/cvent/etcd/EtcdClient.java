package com.cvent.etcd;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.apache.http.HttpStatus;

/**
 * A simple EtcdClient built around async http client from apache. Originally take from here, but modified to work with
 * our dropwizard stack: https://github.com/justinsb/jetcd
 *
 * @author bryan
 */
public class EtcdClient {

    private static final CloseableHttpAsyncClient HTTP_CLIENT = buildDefaultHttpClient();
    /**
     * The object mapper from jackson json parser which is responsible for parsing json using annotations
     */
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    private final URI baseUri;

    public EtcdClient(URI baseUri) {
        String uri = baseUri.toString();
        if (!uri.endsWith("/")) {
            uri += "/";
            baseUri = URI.create(uri);
        }
        this.baseUri = baseUri;
    }

    private static CloseableHttpAsyncClient buildDefaultHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom().build();
        CloseableHttpAsyncClient closeableHttpAsyncClient = HttpAsyncClients.custom().setDefaultRequestConfig(
                requestConfig).build();
        closeableHttpAsyncClient.start();
        return closeableHttpAsyncClient;
    }

    /**
     * Retrieves a key. Returns null if not found.
     *
     * @param key
     * @return
     * @throws com.cvent.etcd.EtcdClientException
     */
    public EtcdResult get(String key) throws EtcdClientException {
        URI uri = buildKeyUri("v2/keys", key, "");
        HttpGet request = new HttpGet(uri);

        EtcdResult result = syncExecute(request, new int[]{HttpStatus.SC_OK, HttpStatus.SC_NOT_FOUND},
                EtcdStatusCode.EcodeKeyNotFound.value());
        if (result.isError()) {
            if (result.getErrorCode() == EtcdStatusCode.EcodeKeyNotFound.value()) {
                return null;
            }
        }
        return result;
    }

    /**
     * Deletes the given key
     *
     * @param key
     * @return
     * @throws com.cvent.etcd.EtcdClientException
     */
    public EtcdResult delete(String key) throws EtcdClientException {
        URI uri = buildKeyUri("v2/keys", key, "");
        HttpDelete request = new HttpDelete(uri);

        return syncExecute(request, new int[]{HttpStatus.SC_OK, HttpStatus.SC_NOT_FOUND});
    }

    /**
     * Sets a key to a new value
     *
     * @param key
     * @param value
     * @return
     * @throws com.cvent.etcd.EtcdClientException
     */
    public EtcdResult set(String key, String value) throws EtcdClientException {
        return set(key, value, null);
    }

    /**
     * Sets a key to a new value with an (optional) ttl
     *
     * @param key
     * @param value
     * @param ttl
     * @return
     * @throws com.cvent.etcd.EtcdClientException
     */
    public EtcdResult set(String key, String value, Integer ttl) throws EtcdClientException {
        List<BasicNameValuePair> data = Lists.newArrayList();
        data.add(new BasicNameValuePair("value", value));
        if (ttl != null) {
            data.add(new BasicNameValuePair("ttl", Integer.toString(ttl)));
        }

        return set0(key, data, new int[]{HttpStatus.SC_OK, HttpStatus.SC_CREATED});
    }

    /**
     * Creates a directory
     *
     * @param key
     * @return
     * @throws com.cvent.etcd.EtcdClientException
     */
    public EtcdResult createDirectory(String key) throws EtcdClientException {
        List<BasicNameValuePair> data = Lists.newArrayList();
        data.add(new BasicNameValuePair("dir", "true"));
        return set0(key, data, new int[]{HttpStatus.SC_OK, HttpStatus.SC_CREATED});
    }

    /**
     * Lists a directory
     *
     * @param key
     * @return
     * @throws com.cvent.etcd.EtcdClientException
     */
    public List<EtcdNode> listDirectory(String key) throws EtcdClientException {
        EtcdResult result = get(key + "/");
        if (result == null || result.getNode() == null) {
            return null;
        }
        return result.getNode().getNodes();
    }

    /**
     * Delete a directory
     *
     * @param key
     * @return
     * @throws com.cvent.etcd.EtcdClientException
     */
    public EtcdResult deleteDirectory(String key) throws EtcdClientException {
        URI uri = buildKeyUri("v2/keys", key, "?dir=true");
        HttpDelete request = new HttpDelete(uri);
        return syncExecute(request, new int[]{HttpStatus.SC_ACCEPTED});
    }

    /**
     * Sets a key to a new value, if the value is a specified value
     *
     * @param key
     * @param prevValue
     * @param value
     * @return
     * @throws com.cvent.etcd.EtcdClientException
     */
    public EtcdResult cas(String key, String prevValue, String value) throws EtcdClientException {
        List<BasicNameValuePair> data = Lists.newArrayList();
        data.add(new BasicNameValuePair("value", value));
        data.add(new BasicNameValuePair("prevValue", prevValue));

        return set0(key, data, new int[]{HttpStatus.SC_OK, HttpStatus.SC_PRECONDITION_FAILED},
                EtcdStatusCode.EcodeTestFailed.value());
    }

    /**
     * Watches the given subtree
     *
     * @param key
     * @return
     * @throws com.cvent.etcd.EtcdClientException
     */
    public ListenableFuture<EtcdResult> watch(String key) throws EtcdClientException {
        return watch(key, null, false);
    }

    /**
     * Watches the given subtree
     *
     * @param key
     * @param index
     * @param recursive
     * @return
     * @throws com.cvent.etcd.EtcdClientException
     */
    public ListenableFuture<EtcdResult> watch(String key, Long index, boolean recursive) throws EtcdClientException {
        String suffix = "?wait=true";
        if (index != null) {
            suffix += "&waitIndex=" + index;
        }
        if (recursive) {
            suffix += "&recursive=true";
        }
        URI uri = buildKeyUri("v2/keys", key, suffix);

        HttpGet request = new HttpGet(uri);

        return asyncExecute(request, new int[]{HttpStatus.SC_OK});
    }

    /**
     * Gets the etcd version
     *
     * @return
     * @throws com.cvent.etcd.EtcdClientException
     */
    public String getVersion() throws EtcdClientException {
        URI uri = baseUri.resolve("version");

        HttpGet request = new HttpGet(uri);

        // Technically not JSON, but it'll work
        // This call is the odd one out
        JsonResponse s = syncExecuteJson(request, HttpStatus.SC_OK);
        if (s.httpStatusCode != HttpStatus.SC_OK) {
            throw new EtcdClientException("Error while fetching versions", s.httpStatusCode);
        }
        return s.json;
    }

    private EtcdResult set0(String key, List<BasicNameValuePair> data, int[] httpErrorCodes, int... expectedErrorCodes)
            throws EtcdClientException {
        URI uri = buildKeyUri("v2/keys", key, "");

        HttpPut request = new HttpPut(uri);

        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(data, Charsets.UTF_8);
        request.setEntity(entity);

        return syncExecute(request, httpErrorCodes, expectedErrorCodes);
    }

    public EtcdResult listChildren(String key) throws EtcdClientException {
        URI uri = buildKeyUri("v2/keys", key, "/");
        HttpGet request = new HttpGet(uri);

        EtcdResult result = syncExecute(request, new int[]{HttpStatus.SC_OK});
        return result;
    }

    protected ListenableFuture<EtcdResult> asyncExecute(HttpUriRequest request, int[] expectedHttpStatusCodes,
            final int... expectedErrorCodes)
            throws EtcdClientException {
        ListenableFuture<JsonResponse> json = asyncExecuteJson(request, expectedHttpStatusCodes);
        return Futures.transform(json, new AsyncFunction<JsonResponse, EtcdResult>() {
            @Override
            public ListenableFuture<EtcdResult> apply(JsonResponse json) throws Exception {
                EtcdResult result = jsonToEtcdResult(json, expectedErrorCodes);
                return Futures.immediateFuture(result);
            }
        });
    }

    protected EtcdResult syncExecute(HttpUriRequest request, int[] expectedHttpStatusCodes, int... expectedErrorCodes)
            throws EtcdClientException {
        try {
            return asyncExecute(request, expectedHttpStatusCodes, expectedErrorCodes).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new EtcdClientException("Interrupted during request", e);
        } catch (ExecutionException e) {
            throw unwrap(e);
        }
    }

    private EtcdClientException unwrap(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof EtcdClientException) {
            return (EtcdClientException) cause;
        }
        return new EtcdClientException("Error executing request", e);
    }

    private EtcdResult jsonToEtcdResult(JsonResponse response, int... expectedErrorCodes) throws EtcdClientException {
        if (response == null || response.json == null) {
            return null;
        }
        EtcdResult result = parseEtcdResult(response.json);

        if (result.isError()) {
            if (!contains(expectedErrorCodes, result.getErrorCode())) {
                throw new EtcdClientException(result.getMessage(), result);
            }
        }
        return result;
    }

    private EtcdResult parseEtcdResult(String json) throws EtcdClientException {
        EtcdResult result;
        try {
            result = MAPPER.readValue(json, EtcdResult.class);
        } catch (IOException e) {
            throw new EtcdClientException("Error parsing response from etcd", e);
        }
        return result;
    }

    private static boolean contains(int[] list, int find) {
        for (int i = 0; i < list.length; i++) {
            if (list[i] == find) {
                return true;
            }
        }
        return false;
    }

    protected List<EtcdResult> syncExecuteList(HttpUriRequest request) throws EtcdClientException {
        JsonResponse response = syncExecuteJson(request, HttpStatus.SC_OK);
        if (response.json == null) {
            return null;
        }

        if (response.httpStatusCode != HttpStatus.SC_OK) {
            EtcdResult etcdResult = parseEtcdResult(response.json);
            throw new EtcdClientException("Error listing keys", etcdResult);
        }

        try {
            return MAPPER.readValue(response.json, EtcdResultList.class).getResultList();
        } catch (IOException e) {
            throw new EtcdClientException("Error parsing response from etcd", e);
        }
    }

    private JsonResponse syncExecuteJson(HttpUriRequest request, int... expectedHttpStatusCodes) throws
            EtcdClientException {
        try {
            return asyncExecuteJson(request, expectedHttpStatusCodes).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EtcdClientException("Interrupted during request processing", e);
        } catch (ExecutionException e) {
            throw unwrap(e);
        }
    }

    private ListenableFuture<JsonResponse> asyncExecuteJson(HttpUriRequest request,
            final int[] expectedHttpStatusCodes) throws EtcdClientException {
        ListenableFuture<HttpResponse> response = asyncExecuteHttp(request);

        return Futures.transform(response, new AsyncFunction<HttpResponse, JsonResponse>() {
            @Override
            public ListenableFuture<JsonResponse> apply(HttpResponse httpResponse) throws Exception {
                JsonResponse json = extractJsonResponse(httpResponse, expectedHttpStatusCodes);
                return Futures.immediateFuture(json);
            }
        });
    }

    /**
     * We need the status code & the response to parse an error response.
     */
    private static class JsonResponse {

        private final String json;
        private final int httpStatusCode;

        public JsonResponse(String json, int statusCode) {
            this.json = json;
            this.httpStatusCode = statusCode;
        }

    }

    private JsonResponse extractJsonResponse(HttpResponse httpResponse, int[] expectedHttpStatusCodes) throws
            EtcdClientException {
        try {
            StatusLine statusLine = httpResponse.getStatusLine();
            int statusCode = statusLine.getStatusCode();

            String json = null;

            if (httpResponse.getEntity() != null) {
                try {
                    json = EntityUtils.toString(httpResponse.getEntity());
                } catch (IOException e) {
                    throw new EtcdClientException("Error reading response", e);
                }
            }

            if (!contains(expectedHttpStatusCodes, statusCode)) {
                if (statusCode != HttpStatus.SC_BAD_REQUEST || json == null) {
                    throw new EtcdClientException("Error response from etcd: " + statusLine.getReasonPhrase(),
                            statusCode);
                }
            }

            return new JsonResponse(json, statusCode);
        } finally {
            close(httpResponse);
        }
    }

    private URI buildKeyUri(String prefix, String key, String suffix) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        if (key.startsWith("/")) {
            key = key.substring(1);
        }
        for (String token : Splitter.on('/').split(key)) {
            sb.append("/");
            sb.append(urlEscape(token));
        }
        sb.append(suffix);

        URI uri = baseUri.resolve(sb.toString());
        return uri;
    }

    protected ListenableFuture<HttpResponse> asyncExecuteHttp(HttpUriRequest request) {
        final SettableFuture<HttpResponse> future = SettableFuture.create();

        HTTP_CLIENT.execute(request, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse result) {
                future.set(result);
            }

            @Override
            public void failed(Exception ex) {
                future.setException(ex);
            }

            @Override
            public void cancelled() {
                future.setException(new InterruptedException());
            }
        });

        return future;
    }

    public static void close(HttpResponse response) {
        if (response == null) {
            return;
        }
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            EntityUtils.consumeQuietly(entity);
        }
    }

    protected static String urlEscape(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException();
        }
    }

}
