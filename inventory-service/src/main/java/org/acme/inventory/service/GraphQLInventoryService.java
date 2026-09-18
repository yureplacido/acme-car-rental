package org.acme.inventory.service;

import io.smallrye.graphql.api.Context;
import jakarta.inject.Inject;
import org.acme.inventory.database.CarInventory;
import org.acme.inventory.model.Car;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.GraphQLException;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;

import java.util.List;
import java.util.Optional;

@GraphQLApi
@Description("API de exemplo para controle e monitoramento de frotas e inventário de veículos")
public class GraphQLInventoryService {

    private final CarInventory inventory;

    // Injeção do contexto para rastrear metadados da requisição
    @Inject
    Context context;

    // Injeção por construtor recomendada pelo Quarkus
    public GraphQLInventoryService(CarInventory inventory) {
        this.inventory = inventory;
    }

    @Query("allCars")
    @Description("Retorna a lista completa de todos os veículos disponíveis no inventário")
    public List<Car> cars() {
        // Exemplo prático de telemetria de campos selecionados pelo cliente no Dev UI
        System.out.println("Campos solicitados no inventário de carros: " + context.getSelectedFields());
        return inventory.getCars();
    }

    @Query("findCar")
    @Description("Busca um veículo específico no inventário utilizando o número da placa")
    public Car findCarByPlate(@Name("plate") String licensePlateNumber) throws GraphQLException {
        return inventory.getCars().stream()
                .filter(car -> car.getLicensePlateNumber().equals(licensePlateNumber))
                .findAny()
                .orElseThrow(() -> new GraphQLException("Carro com a placa " + licensePlateNumber + " não encontrado."));
    }

    @Mutation
    @Description("Cadastra e atribui um ID sequencial para um novo veículo no inventário")
    public Car register(Car car) {
        // Mapeia e cria a instância de forma limpa usando o padrão Builder
        Car newCar = Car.builder()
                .id(CarInventory.ids.incrementAndGet())
                .manufacturer(car.getManufacturer())
                .model(car.getModel())
                .licensePlateNumber(car.getLicensePlateNumber())
                .build();

        inventory.getCars().add(newCar);
        return newCar;
    }

    @Mutation
    @Description("Remove um veículo do inventário com base na placa informada")
    public boolean remove(@Name("plate") String licensePlateNumber) {
        List<Car> cars = inventory.getCars();
        Optional<Car> toBeRemoved = cars.stream()
                .filter(car -> car.getLicensePlateNumber().equals(licensePlateNumber))
                .findAny();
        return toBeRemoved.map(cars::remove).orElse(false);
    }

    // --- FIELD RESOLVER DINÂMICO (@Source) ---
    // Cria um campo virtual combinado chamado 'fullDescription' sem alterar a estrutura de dados original
    @Description("Gera uma string descritiva combinando fabricante e modelo do veículo")
    public String getFullDescription(@Source Car car) {
        return car.getManufacturer() + " " + car.getModel();
    }
}
