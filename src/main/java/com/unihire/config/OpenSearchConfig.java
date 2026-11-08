package com.unihire.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.Socket;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for setting up the OpenSearch client connection.
 * Customized to bypass strict SSL certificate validation for ease of local/development setup.
 */
@Configuration
public class OpenSearchConfig {

    @Value("${spring.opensearch.uris:http://localhost:9200}")
    private String uris;

    @Value("${spring.opensearch.username:}")
    private String username;

    @Value("${spring.opensearch.password:}")
    private String password;

    @Bean
    public OpenSearchClient openSearchClient() {
        try {
            String[] uriArray = uris.split(",");
            List<HttpHost> httpHosts = new ArrayList<>();
            boolean hasHttps = false;

            for (String uriStr : uriArray) {
                URI uri = URI.create(uriStr.trim());
                String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
                String host = uri.getHost() != null ? uri.getHost() : "localhost";
                int port = uri.getPort() != -1 ? uri.getPort() : 9200;

                if ("https".equalsIgnoreCase(scheme)) {
                    hasHttps = true;
                }
                httpHosts.add(new HttpHost(scheme, host, port));
            }

            HttpHost[] hostArray = httpHosts.toArray(new HttpHost[0]);
            ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(hostArray);

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            builder.setMapper(new JacksonJsonpMapper(objectMapper));

            final boolean useHttps = hasHttps;
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                if (username != null && !username.trim().isEmpty()) {
                    BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, (password != null ? password : "").toCharArray());
                    credentialsProvider.setCredentials(new AuthScope(null, -1), credentials);
                    for (HttpHost host : hostArray) {
                        credentialsProvider.setCredentials(new AuthScope(host), credentials);
                        credentialsProvider.setCredentials(new AuthScope(host.getHostName(), host.getPort()), credentials);
                    }
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                }

                if (useHttps) {
                    try {
                        TrustManager[] trustAllCerts = new TrustManager[]{
                                new X509ExtendedTrustManager() {
                                    @Override
                                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                                    @Override
                                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                                    @Override
                                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                                    @Override
                                    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {}
                                    @Override
                                    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {}
                                    @Override
                                    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
                                    @Override
                                    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
                                }
                        };

                        SSLContext sslContext = SSLContext.getInstance("TLS");
                        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

                        TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
                                .setSslContext(sslContext)
                                .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                .build();

                        PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                                .setTlsStrategy(tlsStrategy)
                                .build();

                        httpClientBuilder.setConnectionManager(connectionManager);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to configure SSL for OpenSearch client", e);
                    }
                }

                return httpClientBuilder;
            });

            OpenSearchTransport transport = builder.build();
            return new OpenSearchClient(transport);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OpenSearch client", e);
        }
    }
}
