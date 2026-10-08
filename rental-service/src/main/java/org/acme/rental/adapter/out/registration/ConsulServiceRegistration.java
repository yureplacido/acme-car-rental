package org.acme.rental.adapter.out.registration;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Optional;

/**
 * Cap.10 item 9 - publica este servico no catalogo do Consul como "rentals"
 * (mesmo nome que o reservation-service procura em stork://rentals) com health check
 * HTTP de URL absoluta, e deregistra no shutdown.
 *
 * <p>Um adapter proprio, apesar de o Quarkus ter o registrar Stork, por conta de dois
 * defectos do auto-registro na 3.39.3 (detalhados em
 * docs/knowledge/14-cloud-native-patterns.md secao 1.7): o recorder injeta
 * {@code health-check-url} <b>relativa</b> (derivada dos defaults do SmallRye Health),
 * que o Consul marca como critical desde o boot e remove do catalogo apos 1m; e o
 * deregister roda como ultima shutdown task, depois do CDI fechado, e explode com
 * "No CDI container is available" quando o classpath tem narayana-jta. Nao ha fix de
 * configuracao viavel na versao pinada, entao o registro (e apenas ele - a discovery
 * continua Stork) e assumido pelo servico.
 *
 * <p>Blocante e intencional: roda no main thread no startup ({@code @Observes
 * StartupEvent}) e no shutdown ({@code @PreDestroy}, garantido pelo container), nunca
 * no event loop. Para "falha nao derruba o app" ser pacto e nao esperanca: as chamadas
 * HTTP tem prazos curtos (connect 5s + request 5s), toda excecao do startup/shutdown e
 * contida (o boot e o graceful shutdown seguem) e o deregister trata 404 como sucesso
 * (remove-eu-duas-vezes nao e erro).
 */
@ApplicationScoped
public class ConsulServiceRegistration {

    private static final Logger LOGGER = Logger.getLogger(ConsulServiceRegistration.class.getName());

    private static final java.time.Duration CONSUL_CONNECT_TIMEOUT = java.time.Duration.ofSeconds(5);
    private static final java.time.Duration CONSUL_REQUEST_TIMEOUT = java.time.Duration.ofSeconds(5);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONSUL_CONNECT_TIMEOUT)
            .build();

    @ConfigProperty(name = "acme.consul.registration.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "acme.consul.registration.consul-host", defaultValue = "localhost")
    String consulHost;

    @ConfigProperty(name = "acme.consul.registration.consul-port", defaultValue = "8500")
    int consulPort;

    @ConfigProperty(name = "acme.consul.registration.service-name")
    Optional<String> serviceName;

    @ConfigProperty(name = "acme.consul.registration.address")
    Optional<String> address;

    @ConfigProperty(name = "acme.consul.registration.port")
    Optional<Integer> port;

    @ConfigProperty(name = "acme.consul.registration.health-check-path", defaultValue = "/q/health/live")
    String healthCheckPath;

    @ConfigProperty(name = "acme.consul.registration.health-check-interval", defaultValue = "5s")
    String healthCheckInterval;

    @ConfigProperty(name = "acme.consul.registration.health-check-deregister-after", defaultValue = "1m")
    String healthCheckDeregisterAfter;

    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;

    @ConfigProperty(name = "quarkus.http.port")
    int httpPort;

    @ConfigProperty(name = "quarkus.http.test-port")
    int httpTestPort;

    void registerOnStartup(@Observes StartupEvent event) {
        if (enabled) {
            register();
        }
    }

    @PreDestroy
    void deregisterOnShutdown() {
        if (enabled) {
            try {
                deregister();
            } catch (IOException e) {
                LOGGER.warn("nao foi possivel deregistrar do Consul no shutdown", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("deregistro do Consul interrompido no shutdown", e);
            } catch (RuntimeException e) {
                LOGGER.warn("nao foi possivel deregistrar do Consul no shutdown", e);
            }
        }
    }

    /**
     * Registra (ou sobrescreve) a instancia no catalogo com health check HTTP apontando
     * para {@code http://<address>:<port><health-check-path>}, alcancavel pelo Consul. No
     * default, o endereco e localhost em dev/test e o IP detectado em prod; o teste de
     * registro troca para host.docker.internal para o Consul (de testcontainers) alcancar
     * o /q/health/live do proprio JVM e o check sair "passing".
     */
    public void register() {
        String service = resolvedServiceName();
        String resolvedAddress = resolvedAddress();
        int resolvedPort = resolvedPort();
        String id = consulId(service, resolvedAddress, resolvedPort);

        JsonObject check = new JsonObject()
                .put("HTTP", "http://" + resolvedAddress + ":" + resolvedPort + healthCheckPath)
                .put("Interval", healthCheckInterval)
                .put("DeregisterCriticalServiceAfter", healthCheckDeregisterAfter);
        JsonObject payload = new JsonObject()
                .put("Name", service)
                .put("ID", id)
                .put("Address", resolvedAddress)
                .put("Port", resolvedPort)
                .put("Check", check);

        try {
            HttpResponse<String> response = send("/v1/agent/service/register", "PUT", payload.encode());
            if (response.statusCode() / 100 != 2) {
                LOGGER.error("registro no Consul falhou: HTTP " + response.statusCode() + " " + response.body());
                return;
            }
            LOGGER.info("Instance " + id + " of service " + service + " has been registered in Consul");
        } catch (IOException | InterruptedException e) {
            LOGGER.error("nao foi possivel registrar " + service + " no Consul "
                    + consulHost + ":" + consulPort, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } catch (RuntimeException e) {
            LOGGER.error("registro no Consul abortado por erro inesperado", e);
        }
    }

    /**
     * Remove a instancia do catalogo. Idempotente: recompoe o mesmo id do register, e o
     * Consul responde 404 para id ja ausente - tratado aqui como sucesso (medido: a
     * resposta de sucesso do deregister e 200; para id inexistente o agente responde 404,
     * nao 200).
     */
    public void deregister() throws IOException, InterruptedException {
        String service = resolvedServiceName();
        String id = consulId(service, resolvedAddress(), resolvedPort());
        HttpResponse<String> response = send("/v1/agent/service/deregister/" + id, "PUT", "");
        if (response.statusCode() / 100 == 2 || response.statusCode() == 404) {
            LOGGER.info("Instance " + id + " of service " + service + " has been deregistered in Consul");
            return;
        }
        throw new IllegalStateException("deregistro no Consul falhou: HTTP " + response.statusCode()
                + " " + response.body());
    }

    private HttpResponse<String> send(String path, String method, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://" + consulHost + ":" + consulPort + path))
                .timeout(CONSUL_REQUEST_TIMEOUT);
        HttpRequest request = method.equals("PUT")
                ? builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build()
                : builder.GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String consulId(String service, String address, int port) {
        return service + "-" + address + "-" + port;
    }

    private String resolvedServiceName() {
        return serviceName.orElse(applicationName);
    }

    private String resolvedAddress() {
        String configured = address.orElse("").trim();
        if (!configured.isEmpty()) {
            return configured;
        }
        if (LaunchMode.current().isDevOrTest()) {
            return "localhost";
        }
        return detectAddress();
    }

    private int resolvedPort() {
        return port.orElseGet(() -> LaunchMode.current() == LaunchMode.TEST ? httpTestPort : httpPort);
    }

    private static String detectAddress() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            LOGGER.error("failed to detect IP address", e);
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            LOGGER.error("fallback to localhost failed", e);
            return "127.0.0.1";
        }
    }
}