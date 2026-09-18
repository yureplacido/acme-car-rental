package org.acme.inventory.api;

import io.smallrye.graphql.api.Context;
import jakarta.inject.Inject;
import org.acme.inventory.model.Car;
import org.acme.inventory.model.CarFilter;
import org.acme.inventory.model.CarSortField;
import org.acme.inventory.model.Page;
import org.acme.inventory.model.SortOrder;
import org.acme.inventory.repository.CarRepository;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.GraphQLException;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@GraphQLApi
@Description("API de exemplo para controle e monitoramento de frotas e inventário de veículos")
public class GraphQLInventoryService {

    private final CarRepository carRepository;

    // Injeção do contexto para rastrear metadados da requisição

    @Inject
    private final Context context;

    // Injeção por construtor recomendada pelo Quarkus
    public GraphQLInventoryService(CarRepository carRepository, Context context) {
        this.carRepository = carRepository;
        this.context = context;
    }

    @Query("allCars")
    @Description("Retorna a lista de veículos disponíveis no inventário, com busca, filtro e ordenação opcionais e paginação via offset/limit")
    public List<Car> cars(@Name("offset") Integer offset,
                          @Name("limit") Integer limit,
                          @Name("search") String search,
                          @Name("filter") CarFilter filter,
                          @Name("sort") @DefaultValue("ID") CarSortField sort,
                          @Name("order") @DefaultValue("ASC") SortOrder order) {
        // Exemplo prático de telemetria de campos selecionados pelo cliente no Dev UI
        System.out.println("Campos solicitados no inventário de carros: " + context.getSelectedFields());

        List<Car> all = carRepository.findAll();
        if (offset == null && limit == null && search == null && filter == null) {
            return all;
        }
        return Page.of(all, offset == null ? 0 : offset, limit == null ? Page.MAX_LIMIT : limit,
                matching(search, filter), sortedBy(sort, order)).getItems();
    }

    @Query("allCarsPage")
    @Description("Retorna uma página de veículos do inventário, com busca, filtro e ordenação opcionais e metadados de paginação (total, hasNextPage)")
    public Page<Car> carsPage(@Name("offset") @DefaultValue("0") int offset,
                              @Name("limit") @DefaultValue("20") int limit,
                              @Name("search") String search,
                              @Name("filter") CarFilter filter,
                              @Name("sort") @DefaultValue("ID") CarSortField sort,
                              @Name("order") @DefaultValue("ASC") SortOrder order) {
        return Page.of(carRepository.findAll(), offset, limit, matching(search, filter), sortedBy(sort, order));
    }

    private Predicate<Car> matching(String search, CarFilter filter) {
        String term = search == null ? "" : search.trim().toLowerCase();
        return car -> {
            boolean termMatches = term.isEmpty()
                    || car.getLicensePlateNumber().toLowerCase().contains(term)
                    || car.getManufacturer().toLowerCase().contains(term)
                    || car.getModel().toLowerCase().contains(term);
            boolean filterMatches = filter == null
                    || (filter.getManufacturer() == null
                    || filter.getManufacturer().isBlank()
                    || car.getManufacturer().equalsIgnoreCase(filter.getManufacturer().trim()))
                    && (filter.getModel() == null
                    || filter.getModel().isBlank()
                    || car.getModel().equalsIgnoreCase(filter.getModel().trim()))
                    && (filter.getPlate() == null
                    || filter.getPlate().isBlank()
                    || car.getLicensePlateNumber().equalsIgnoreCase(filter.getPlate().trim()));
            return termMatches && filterMatches;
        };
    }

    private Comparator<Car> sortedBy(CarSortField sort, SortOrder order) {
        Comparator<Car> byField = switch (sort == null ? CarSortField.ID : sort) {
            case PLATE_NUMBER -> Comparator.comparing(Car::getLicensePlateNumber);
            case MANUFACTURER -> Comparator.comparing(Car::getManufacturer);
            case MODEL -> Comparator.comparing(Car::getModel);
            case ID -> Comparator.comparing(Car::getId);
        };
        return order == SortOrder.DESC ? byField.reversed() : byField;
    }

    @Query("findCar")
    @Description("Busca um veículo específico no inventário utilizando o número da placa")
    public Car findCarByPlate(@Name("plate") String licensePlateNumber) throws GraphQLException {
        return carRepository.findAll().stream()
                .filter(car -> car.getLicensePlateNumber().equals(licensePlateNumber))
                .findAny()
                .orElseThrow(() -> new GraphQLException("Carro com a placa " + licensePlateNumber + " não encontrado."));
    }

    @Mutation
    @Description("Cadastra e atribui um ID sequencial para um novo veículo no inventário")
    public Car register(Car car) {
        // Mapeia e cria a instância de forma limpa usando o padrão Builder
        Car newCar = Car.builder()
                .id(carRepository.nextId())
                .manufacturer(car.getManufacturer())
                .model(car.getModel())
                .licensePlateNumber(car.getLicensePlateNumber())
                .build();

        carRepository.findAll().add(newCar);
        return newCar;
    }

    @Mutation
    @Description("Remove um veículo do inventário com base na placa informada")
    public boolean remove(@Name("plate") String licensePlateNumber) {
        List<Car> cars = carRepository.findAll();
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
