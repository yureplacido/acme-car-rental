package org.acme.inventory.application;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
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
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Politica de falha do import massivo reativo (ADR 006, §Failure policy).
 *
 * <p>Sao tres compromissos, em dois eixos distintos:
 *
 * <ul>
 *   <li>a falha de um item <b>propaga como falha do stream</b>, e os itens ja <b>concluidos</b>
 *       permanecem persistidos - fail-fast nao e rollback: um item ruim nao desfaz o que os
 *       outros ja gravaram;</li>
 *   <li>o cancelamento e respeitado, e ha <b>dois caminhos</b> para ele: o consumidor cancela
 *       ({@link BulkRegisterVehiclesCancellationTest}) ou a falha de uma irma cancela as demais
 *       (aqui). Quem cancela muda o cenario, entao os dois precisam de prova propria.</li>
 * </ul>
 *
 * <p>O comportamento do {@code merge} ao cancelar as subscriptions irmao foi <b>medido</b>, nao
 * presumido da documentacao: um probe descartavel mostrou {@code started=2 cancelled=1} antes de
 * este teste ser escrito.
 */
@Timeout(30)
class BulkRegisterVehiclesFailurePolicyTest {

    /**
     * Atraso do item que fica em voo: longo o bastante para que a falha o alcance antes da
     * conclusao, curto o bastante para nao segurar a suite.
     */
    private static final Duration IN_FLIGHT_DELAY = Duration.ofSeconds(10);

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
     * ADR 006 (§Failure policy): quando um item falha, as operacoes irmas <b>em voo</b> nao
     * podem continuar working: o {@code merge} cancela as subscriptions irmao. Sem este teste,
     * uma regressao que deixasse as irmas rodando passaria verde - e o efeito no mundo real e o
     * contrario do desejado: a ingestao morta continuaria gastando conexoes com o banco depois
     * que o stream ja falhou.
     *
     * <p>Contraste com o cancelamento por decisao do downstream
     * ({@link BulkRegisterVehiclesCancellationTest}), que e o outro caminho: la quem cancela e o
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

        // O item que falhou nao foi persistido, e o que estava em voo tambem nao: foi cancelado
        // antes de concluir. Nada foi gravado - a ingestao morta nao deixa registro parcial.
        assertEquals(0, repository.saved.size(),
                "nenhum item deveria ter sido persistido: o que falhou e o que foi cancelado");
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

    /**
     * Repositorio fake que so registra o item como persistido <b>quando ele e concluido</b>.
     * Registrar na invocacao mediria "chamou save()", e nao "gravou" - e a distincao importa
     * aqui: um item cancelado nunca foi persistido.
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
            return super.save(vehicle);
        }
    }

    /**
     * Persiste o primeiro item, mas <b>lentamente</b> (e de forma cancelavel), e falha no
     * segundo. O primeiro fica em voo quando o segundo falha - e e ele que deve ser cancelado.
     * O atraso e longo de proposito: um item rapido terminaria antes da falha e nao provaria
     * nada.
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
            // O registro em `saved` vem DEPOIS do atraso: se a operacao for cancelada, o item
            // nunca foi persistido - e e exatamente isso que o teste de cancelamento afirma.
            return Uni.createFrom().item(persisted)
                    .onItem().delayIt().by(IN_FLIGHT_DELAY)
                    .onItem().invoke(saved::add)
                    .onCancellation().invoke(cancelled::incrementAndGet);
        }
    }
}
