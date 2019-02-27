package dk.hindsholm.threadless;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import org.eclipse.californium.core.CaliforniumLogger;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.ScandiumLogger;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;
import org.eclipse.californium.scandium.dtls.pskstore.InMemoryPskStore;

public class Main {

    static {
        CaliforniumLogger.initialize();
        CaliforniumLogger.setLevel(Level.WARNING);
        ScandiumLogger.initialize();
        ScandiumLogger.setLevel(Level.WARNING);
    }

    private static final int ERR_BAD_URI = 1;
    private static final int ERR_REQUEST_FAILED = 2;
    private static final int ERR_RESPONSE_FAILED = 3;

    public static void main(String[] args) throws IOException, GeneralSecurityException {

        if (args.length != 2) {
            usage();
            return;
        }

        String key = args[0];
        URI uri = null;
        try {
            uri = new URI(args[1]);
        } catch (URISyntaxException e) {
            System.err.println("Failed to parse URI: " + e.getMessage());
            System.exit(ERR_BAD_URI);
        }

        Request request = Request.newGet();
        request.setURI(uri);
        request.getOptions().setContentFormat(MediaTypeRegistry.TEXT_PLAIN);

        DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder(new InetSocketAddress(0));
        InMemoryPskStore pskStore = new InMemoryPskStore();
        InetSocketAddress address = new InetSocketAddress(request.getDestination(), request.getDestinationPort());
        pskStore.addKnownPeer(address, "", key.getBytes());
        builder.setPskStore(pskStore);
        builder.setSupportedCipherSuites(new CipherSuite[]{CipherSuite.TLS_PSK_WITH_AES_128_CCM_8});
        DTLSConnector dtlsconnector = new DTLSConnector(builder.build(), null);

        Endpoint dtlsEndpoint = new CoapEndpoint(dtlsconnector, NetworkConfig.getStandard());
        dtlsEndpoint.start();
        EndpointManager.getEndpointManager().setDefaultSecureEndpoint(dtlsEndpoint);

        try {
            request.send();

            Response response = null;
            try {
                response = request.waitForResponse();
            } catch (InterruptedException e) {
                System.err.println("Failed to receive response: " + e.getMessage());
                System.exit(ERR_RESPONSE_FAILED);
            }

            if (response != null) {
                switch (response.getOptions().getContentFormat()) {
                    case MediaTypeRegistry.APPLICATION_LINK_FORMAT:
                        String linkFormat = response.getPayloadString();
                        System.out.println("Discovered resources:\n" + linkFormat);
                        break;
                    case MediaTypeRegistry.APPLICATION_JSON:
                        String json = response.getPayloadString();
                        JsonReader jsonReader = Json.createReader(new StringReader(json));
                        JsonValue value = jsonReader.readValue();
                        String formatted = formatJson(value);
                        System.out.println("JSON payload:\n" + formatted);
                        break;
                    default:
                        System.err.println("Unknown content format: " + response.getOptions().getContentFormat());
                }
            } else {
                System.err.println("Request timed out");
            }

        } catch (Exception e) {
            System.err.println("Failed to execute request: " + e.getMessage());
            System.exit(ERR_REQUEST_FAILED);
        }

        System.exit(0);
    }

    static String formatJson(JsonValue value) {
        Map<String, String> config = new HashMap<>();
        config.put(JsonGenerator.PRETTY_PRINTING, "");
        JsonWriterFactory factory = Json.createWriterFactory(config);
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = factory.createWriter(stringWriter)) {
            jsonWriter.write(value);
        }
        return stringWriter.toString();
    }

    public static void usage() {
        System.out.println("Traadfri Client");
        System.out.println();
        System.out.println("Usage: " + Main.class.getSimpleName() + " KEY URI");
        System.out.println("  KEY     : The key printed at the bottom of the Traadfri Gateway");
        System.out.println("  URI     : The CoAP URI of the remote endpoint or resource");
        System.out.println("            A coaps URI will automatically use CoAP over DTLS");
    }

}
