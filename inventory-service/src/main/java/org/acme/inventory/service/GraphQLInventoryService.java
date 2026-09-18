package org.acme.inventory.service;

import io.smallrye.graphql.api.Context;
import jakarta.inject.Inject;
import org.acme.inventory.database.CarInventory;
import org.acme.inventory.model.Car;
import org.acme.inventory.model.Page;
import org.eclipse.microprofile.graphql.DefaultValue;
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
    private final Context context;

    // Injeção por construtor recomendada pelo Quarkus
    public GraphQLInventoryService(CarInventory inventory, Context context) {
        this.inventory = inventory;
        this.context = context;
    }

    @Query("allCars")
    @Description("Retorna a lista de veículos disponíveis no inventário, com paginação opcional via offset/limit")
    public List<Car> cars(@Name("offset") Integer offset,
                          @Name("limit") Integer limit) {
        // Exemplo prático de telemetria de campos selecionados pelo cliente no Dev UI
        System.out.println("Campos solicitados no inventário de carros: " + context.getSelectedFields());

        List<Car> all = inventory.getCars();
        if (offset == null && limit == null) {
            return all;
        }
        return Page.of(all, offset == null ? 0 : offset, limit == null ? Page.MAX_LIMIT : limit).getItems();
    }

    @Query("allCarsPage")
    @Description("Retorna uma página de veículos do inventário, com metadados de paginação (total, hasNextPage)")
    public Page<Car> carsPage(@Name("offset") @DefaultValue("0") int offset,
                              @Name("limit") @DefaultValue("20") int limit) {
        return Page.of(inventory.getCars(), offset, limit);
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
