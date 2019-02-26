package dk.hindsholm.threadless;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.logging.Level;

import org.eclipse.californium.core.CaliforniumLogger;
import org.eclipse.californium.core.Utils;
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
        ScandiumLogger.setLevel(Level.INFO);
    }

    // indices of command line parameters
    private static final int IDX_KEY = 0;
    private static final int IDX_URI = 1;

    // exit codes for runtime errors
    private static final int ERR_MISSING_URI = 1;
    private static final int ERR_MISSING_KEY = 2;
    private static final int ERR_BAD_URI = 3;
    private static final int ERR_REQUEST_FAILED = 4;
    private static final int ERR_RESPONSE_FAILED = 5;

    // parameters
    static URI uri;
    static String key;

    // for coaps
    private static Endpoint dtlsEndpoint;

    public static void main(String[] args) throws IOException, GeneralSecurityException {

        if (args.length == 0) {
            printInfo();
            return;
        }

        // input parameters
        int idx = 0;
        for (String arg : args) {
            switch (idx) {
                case IDX_KEY:
                    key = arg;
                    break;
                case IDX_URI:
                    try {
                        uri = new URI(arg);
                    } catch (URISyntaxException e) {
                        System.err.println("Failed to parse URI: " + e.getMessage());
                        System.exit(ERR_BAD_URI);
                    }
                    break;
                default:
                    System.out.println("Unexpected argument: " + arg);
            }
            ++idx;
        }

        // check if mandatory parameters specified
        if (uri == null) {
            System.err.println("URI not specified");
            System.exit(ERR_MISSING_URI);
        }
        if (key == null) {
            System.err.println("KEY not specified");
            System.exit(ERR_MISSING_KEY);
        }

        // create request
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

        dtlsEndpoint = new CoapEndpoint(dtlsconnector, NetworkConfig.getStandard());
        dtlsEndpoint.start();
        EndpointManager.getEndpointManager().setDefaultSecureEndpoint(dtlsEndpoint);

        // execute request
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
                System.out.println(Utils.prettyPrint(response));
                System.out.println("Time elapsed (ms): " + response.getRTT());
                // check if response contains resources
                if (response.getOptions().isContentFormat(MediaTypeRegistry.APPLICATION_LINK_FORMAT)) {
                    String linkFormat = response.getPayloadString();
                    System.out.println("\nDiscovered resources:");
                    System.out.println(linkFormat);
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

    /*
	 * Outputs user guide of this program.
     */
    public static void printInfo() {
        System.out.println("Traadfri Client");
        System.out.println();
        System.out.println("Usage: " + Main.class.getSimpleName() + " KEY URI");
        System.out.println("  KEY     : The key printed at the bottom of the Traadfri Gateway");
        System.out.println("  URI     : The CoAP URI of the remote endpoint or resource");
        System.out.println("            A coaps URI will automatically use CoAP over DTLS");
    }

}
