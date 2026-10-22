package org.acme.inventory.application;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.application.usecase.BulkRegisterVehicles;
import org.acme.inventory.application.usecase.RegisterVehicle;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Transmission;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova que o pipeline de import massivo <b>nao materializa o stream inteiro</b> antes de
 * comecar a processar.
 *
 * <p>A ADR 006 (import massivo reativo) exige este cenario no nome exato
 * {@code shouldNotMaterializeTheEntireInputStreamBeforeProcessing} e proibe, no mesmo documento,
 * tratar um limite de concorrencia como se fosse backpressure. As duas coisas sao medidas de
 * formas diferentes:
 *
 * <ul>
 *   <li>o {@link BulkRegisterVehiclesBackpressureTest} mede <b>requests</b> ao upstream
 *       (demanda), provando que o {@code merge} nao pede mais que {@code maxConcurrency};</li>
 *   <li>este mede os <b>itens efetivamente produzidos</b> pelo upstream, provando que a fonte e
 *       consumida sob demanda e nao drenada de uma vez para memoria.</li>
 * </ul>
 *
 * <p>A prova aqui e uma <b>identidade</b>, nao uma amostragem: o upstream so produz o que foi
 * pedido, e o que foi pedido e limitado pela concorrencia do {@code merge}. Um pipeline que
 * materializa (por exemplo {@code collect().asList()} antes de processar) pediria a fonte
 * inteira e a primeira identidade quebraria.
 */
@Timeout(30)
class BulkRegisterVehiclesMaterializationTest {

    /**
     * Fonte grande o bastante para que materializar seja inconfundivel: se o pipeline drenasse a
     * fonte, produziria os 100 itens. A prova e que ele produz muito menos.
     */
    private static final int SOURCE_SIZE = 100;

    @Test
    void shouldNotMaterializeTheEntireInputStreamBeforeProcessing() {
        FakeVehicleRepository repository = new FakeVehicleRepository();
        RegisterVehicle registerVehicle = new RegisterVehicle(
                repository,
                event -> Uni.createFrom().voidItem());
        int maxConcurrency = 4;
        BulkRegisterVehicles useCase = new BulkRegisterVehicles(registerVehicle, maxConcurrency);

        AtomicLong requested = new AtomicLong();
        AtomicInteger produced = new AtomicInteger();
        Multi<RegisterVehicle.Command> source = Multi.createFrom()
                .range(0, SOURCE_SIZE)
                .map(index -> command("PLATE%03d".formatted(index)))
                .onItem().invoke(ignored -> produced.incrementAndGet())
                .onRequest().invoke(requested::addAndGet);

        AssertSubscriber<Vehicle> subscriber = useCase.handle(source)
                .subscribe()
                .withSubscriber(AssertSubscriber.create(0));

        subscriber.awaitSubscription();

        // Identidade 1: a demanda ao upstream e limitada pela concorrencia. Um pipeline que
        // materializasse pediria a fonte inteira (Long.MAX_VALUE) e esta leitura quebraria.
        assertTrue(
                requested.get() <= maxConcurrency,
                "sem demanda a jusante, o upstream deveria receber no maximo " + maxConcurrency
                        + " pedidos, mas recebeu " + requested.get()
                        + " - o pipeline parece estar materializando o stream inteiro");
        // Identidade 2: o upstream produz no maximo o que foi pedido. E o par das duas
        // identidades que prova a nao-materializacao: produced <= requested <= maxConcurrency.
        assertTrue(
                produced.get() <= requested.get(),
                "o upstream produziu " + produced.get() + " itens com apenas " + requested.get()
                        + " pedidos - produziu mais do que a demanda permite");
        subscriber.assertHasNotReceivedAnyItem();

        // Ao liberar demanda, o processamento acompanha - a fonte continua sendo consumida
        // sob demanda, um item por vez.
        subscriber.request(1);
        subscriber.awaitItems(1);
        assertEquals(1, subscriber.getItems().size());
        assertTrue(
                produced.get() < SOURCE_SIZE,
                "apos 1 item de demanda o upstream nao deveria ter produzido a fonte inteira");

        subscriber.cancel();
    }

    @Test
    void shouldConsumeTheWholeSourceWhenDemandIsUnbounded() {
        // Contraponto do teste acima: com demanda ilimitada, a fonte e consumida por inteiro.
        // Sem este caso, um pipeline que simplesmente nao consumisse nada passaria no primeiro.
        FakeVehicleRepository repository = new FakeVehicleRepository();
        RegisterVehicle registerVehicle = new RegisterVehicle(
                repository,
                event -> Uni.createFrom().voidItem());
        BulkRegisterVehicles useCase = new BulkRegisterVehicles(registerVehicle, 4);

        AtomicInteger produced = new AtomicInteger();
        Multi<RegisterVehicle.Command> source = Multi.createFrom()
                .range(0, SOURCE_SIZE)
                .map(index -> command("PLATE%03d".formatted(index)))
                .onItem().invoke(ignored -> produced.incrementAndGet());

        List<Vehicle> vehicles = useCase.handle(source)
                .collect().asList()
                .await().atMost(Duration.ofSeconds(5));

        assertEquals(SOURCE_SIZE, vehicles.size());
        assertEquals(SOURCE_SIZE, produced.get());
        assertEquals(SOURCE_SIZE, repository.saved.size());
    }

    private static RegisterVehicle.Command command(String plate) {
        return new RegisterVehicle.Command(
                plate,
                "Ford",
                "Mustang",
                VehicleCategory.SUV,
                Transmission.AUTOMATIC,
                null,
                2025,
                "black",
                5,
                null,
                null,
                "BRL");
    }

    /**
     * Repositorio fake que so registra o item como persistido <b>quando ele e concluido</b>.
     * Registrar na invocacao mediria "chamou save()", e nao "gravou" - e a distincao importa
     * na politica de falha: um item cancelado nunca foi persistido.
     */
    static class FakeVehicleRepository implements VehicleRepository {

        final List<Vehicle> saved = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Uni<List<Vehicle>> all() {
            return Uni.createFrom().item(List.copyOf(saved));
        }

        @Override
        public Uni<Optional<Vehicle>> findByLicensePlate(LicensePlate plate) {
            return Uni.createFrom().item(
                    saved.stream()
                            .filter(vehicle -> vehicle.licensePlate().equals(plate))
                            .findFirst());
        }

        @Override
        public Uni<Vehicle> save(Vehicle vehicle) {
            Vehicle persisted = persist(vehicle);
            return Uni.createFrom().item(persisted)
                    .onItem().invoke(saved::add);
        }

        Vehicle persist(Vehicle vehicle) {
            return Vehicle.rehydrate(
                    new VehicleId(ids.incrementAndGet()),
                    vehicle.licensePlate(),
                    vehicle.specifications(),
                    vehicle.location(),
                    vehicle.status(),
                    vehicle.dailyRate(),
                    vehicle.odometer(),
                    vehicle.condition());
        }
    }
}
