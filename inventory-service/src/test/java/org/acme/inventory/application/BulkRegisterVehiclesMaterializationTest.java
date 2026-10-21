package org.acme.inventory.application;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.application.usecase.BulkRegisterVehicles;
import org.acme.inventory.application.usecase.RegisterVehicle;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Transmission;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fecha as lacunas da ADR 006 (import massivo reativo) que a evidencia anterior nao cobria.
 *
 * <p>A ADR 006 exige este cenario no nome exato
 * {@code shouldNotMaterializeTheEntireInputStreamBeforeProcessing} e proibe, no mesmo documento,
 * tratar um limite de concorrencia como se fosse backpressure. As duas coisas sao medidas de
 * formas diferentes:
 *
 * <ul>
 *   <li>o {@link BulkRegisterVehiclesBackpressureTest} mede <b>requests</b> ao upstream
 *       (demanda), provando que o {@code merge} nao pede mais que {@code maxConcurrency};</li>
 *   <li>este mede <b>itens efetivamente produzidos</b> pelo upstream, provando que a fonte e
 *       consumida sob demanda e nao drenada de uma vez para memoria.</li>
 * </ul>
 *
 * <p>Um pipeline que materializa (por exemplo {@code collect().asList()} antes de processar, ou
 * um operador que assina o upstream inteiro) produziria todos os elementos da fonte mesmo com
 * demanda zero a jusante - e este teste falharia. E o defeito que a ADR quer impedir: a
 * ingestao de um arquivo grande passaria a caber inteira na heap.
 *
 * <p>Alem da materializacao, a classe cobre a <b>politica de falha</b> da ADR 006
 * ({@code §Failure policy}): a falha propaga como falha do stream, os itens ja concluidos
 * permanecem persistidos (fail-fast nao e rollback), e a falha cancela as operacoes irmao em
 * voo. O ultimo foi <b>medido</b>, nao presumido: um probe descartavel mostrou que o
 * {@code merge} cancela as subscriptions irmao ({@code started=2 cancelled=1}).
 */
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

        AtomicInteger produced = new AtomicInteger();
        Multi<RegisterVehicle.Command> source = Multi.createFrom()
                .range(0, SOURCE_SIZE)
                .map(index -> command("PLATE%03d".formatted(index)))
                .onItem().invoke(ignored -> produced.incrementAndGet());

        AssertSubscriber<Vehicle> subscriber = useCase.handle(source)
                .subscribe()
                .withSubscriber(AssertSubscriber.create(0));

        subscriber.awaitSubscription();

        // Sem demanda a jusante, o upstream nao foi drenado: produziu no maximo o que a
        // concorrencia do merge permite manter em voo, e nao os 100 itens da fonte.
        assertTrue(
                produced.get() <= maxConcurrency,
                "esperava no maximo " + maxConcurrency + " itens produzidos sem demanda, mas o upstream produziu "
                        + produced.get() + " de " + SOURCE_SIZE
                        + " - o pipeline parece estar materializando o stream inteiro");
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
                .await().indefinitely();

        assertEquals(SOURCE_SIZE, vehicles.size());
        assertEquals(SOURCE_SIZE, produced.get());
        assertEquals(SOURCE_SIZE, repository.saved.size());
    }

    /**
     * ADR 006 (§Failure policy): a politica e fail-fast, a falha de um item propaga como falha do
     * stream, o cancelamento a jusante e respeitado, e <b>os itens ja concluidos permanecem
     * persistidos</b> - um item ruim nao pode desfazer o que outros ja gravaram.
     */
    @Test
    void shouldPropagatePersistenceFailureWithoutLosingAlreadyPersistedItems() {
        int failingAt = 3;
        FakeVehicleRepository repository = new FailingVehicleRepository(failingAt);
        RegisterVehicle registerVehicle = new RegisterVehicle(
                repository,
                event -> Uni.createFrom().voidItem());
        // Concorrencia 1 para que a ordem de gravacao seja deterministica: com paralelismo, o
        // item que falha pode ser processado antes de outros ja terem sido salvos.
        BulkRegisterVehicles useCase = new BulkRegisterVehicles(registerVehicle, 1);

        UniAssertSubscriber<List<Vehicle>> subscriber = useCase.handle(Multi.createFrom()
                        .items(
                                command("AAA111"),
                                command("BBB222"),
                                command("CCC333"),
                                command("DDD444"),
                                command("EEE555")))
                .collect().asList()
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create());

        subscriber
                .awaitFailure()
                .assertFailedWith(RuntimeException.class, "database exploded");

        // A falha propagou e interrompeu a ingestao: os itens posteriores ao que falhou nao
        // foram gravados, e o que foi concluido antes continua persistido (fail-fast nao e
        // rollback - um item ruim nao desfaz o que os outros ja gravaram).
        assertEquals(
                failingAt - 1,
                repository.saved.size(),
                "a falha deveria ter parado a ingestao no item " + failingAt
                        + ", preservando os " + (failingAt - 1) + " itens ja concluidos");
    }

    /**
     * ADR 006 (§Failure policy): "downstream cancellation is respected". Quando um item falha, as
     * operacoes irmas **em voo** nao podem continuar working: o merge cancela as subscriptions
     * irmao. Sem este teste, uma regressao que deixasse as irmas rodando passaria verde - e o
     * efeito no mundo real e o contrario do desejado: a ingestao morta continuaria gastando
     * conexoes com o banco depois que o stream ja falhou.
     *
     * <p>Contraste com o cancelamento por decisao do downstream
     * ({@code BulkRegisterVehiclesCancellationTest}), que e o outro caminho: la quem cancela e o
     * consumidor; aqui quem cancela e a falha.
     */
    @Test
    void shouldCancelSiblingsInFlightWhenOneItemFails() {
        SlowFailingVehicleRepository repository = new SlowFailingVehicleRepository(2);
        RegisterVehicle registerVehicle = new RegisterVehicle(
                repository,
                event -> Uni.createFrom().voidItem());
        // Concorrencia 2: o item 1 fica em voo (lento) e o item 2 falha, disparando o cancelamento.
        BulkRegisterVehicles useCase = new BulkRegisterVehicles(registerVehicle, 2);

        useCase.handle(Multi.createFrom()
                        .items(
                                command("AAA111"),
                                command("BBB222"),
                                command("CCC333")))
                .collect().asList()
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .assertFailedWith(RuntimeException.class, "database exploded");

        // O item que falhou nao foi persistido; apenas o que estava em voo chegou ao repositorio
        // (e nao se confirmou, porque foi cancelado).
        assertEquals(1, repository.saved.size(),
                "apenas o item que estava em voo deveria ter alcancado o repositorio");
        // O que estava em voo foi CANCELADO, nao concluido: e o cancelamento que prova que a
        // ingestao morta nao continua trabalhando depois da falha.
        assertEquals(1, repository.cancelled.get(),
                "a operacao irma em voo deveria ter sido cancelada pela falha, e nao deixada rodar");
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
            return Uni.createFrom().item(persisted);
        }

        Vehicle persist(Vehicle vehicle) {
            Vehicle persisted = Vehicle.rehydrate(
                    new VehicleId(ids.incrementAndGet()),
                    vehicle.licensePlate(),
                    vehicle.specifications(),
                    vehicle.location(),
                    vehicle.status(),
                    vehicle.dailyRate(),
                    vehicle.odometer(),
                    vehicle.condition());
            saved.add(persisted);
            return persisted;
        }
    }

    /**
     * Persiste os primeiros itens e falha no enesimo, para exercitar a politica fail-fast da
     * ADR 006 sem depender de um banco real.
     */
    static class FailingVehicleRepository extends FakeVehicleRepository {

        private final int failingAt;
        private final AtomicInteger attempts = new AtomicInteger();

        FailingVehicleRepository(int failingAt) {
            this.failingAt = failingAt;
        }

        @Override
        public Uni<Vehicle> save(Vehicle vehicle) {
            if (attempts.incrementAndGet() == failingAt) {
                return Uni.createFrom().failure(new RuntimeException("database exploded"));
            }
            return Uni.createFrom().item(persist(vehicle));
        }
    }

    /**
     * Persiste o primeiro item, mas **lentamente** (e de forma cancelavel), e falha no segundo.
     * O primeiro fica em voo quando o segundo falha - e e ele que deve ser cancelado. O atraso
     * e longo de proposito: um item rapido terminaria antes da falha e nao provaria nada.
     */
    static class SlowFailingVehicleRepository extends FakeVehicleRepository {

        private final int failingAt;
        private final AtomicInteger attempts = new AtomicInteger();
        final AtomicInteger cancelled = new AtomicInteger();

        SlowFailingVehicleRepository(int failingAt) {
            this.failingAt = failingAt;
        }

        @Override
        public Uni<Vehicle> save(Vehicle vehicle) {
            if (attempts.incrementAndGet() == failingAt) {
                return Uni.createFrom().failure(new RuntimeException("database exploded"));
            }
            Vehicle persisted = persist(vehicle);
            return Uni.createFrom().item(persisted)
                    .onItem().delayIt().by(Duration.ofSeconds(30))
                    .onCancellation().invoke(cancelled::incrementAndGet);
        }
    }
}
