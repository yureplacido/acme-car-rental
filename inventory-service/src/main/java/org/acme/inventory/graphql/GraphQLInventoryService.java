package org.acme.inventory.graphql;

import io.smallrye.graphql.api.Context;
import jakarta.inject.Inject;
import org.acme.inventory.domain.CarInventoryService;
import org.acme.inventory.model.graphql.Car;
import org.acme.inventory.model.graphql.CarFilter;
import org.acme.inventory.model.graphql.CarSortField;
import org.acme.inventory.model.graphql.CarStatus;
import org.acme.inventory.model.graphql.Page;
import org.acme.inventory.model.graphql.SortOrder;
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
import java.util.function.Predicate;

@GraphQLApi
@Description("API de exemplo para controle e monitoramento de frotas e inventário de veículos")
public class GraphQLInventoryService {

    private final CarInventoryService carInventory;

    // Injeção do contexto para rastrear metadados da requisição

    @Inject
    private final Context context;

    // Injeção por construtor recomendada pelo Quarkus
    public GraphQLInventoryService(CarInventoryService carInventory, Context context) {
        this.carInventory = carInventory;
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

        List<Car> all = carInventory.all();
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
        return Page.of(carInventory.all(), offset, limit, matching(search, filter), sortedBy(sort, order));
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
            boolean statusMatches = (filter == null || filter.getStatus() == null)
                    ? car.getStatus() != CarStatus.DECOMMISSIONED
                    : car.getStatus() == filter.getStatus();
            return termMatches && filterMatches && statusMatches;
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
    @Description("Busca um veículo específico no inventário utilizando o número da placa (inclui veículos baixados)")
    public Car findCarByPlate(@Name("plate") String licensePlateNumber) throws GraphQLException {
        return carInventory.findByPlate(licensePlateNumber)
                .orElseThrow(() -> new GraphQLException("Carro com a placa " + licensePlateNumber + " não encontrado."));
    }

    @Mutation
    @Description("Cadastra um novo veículo no inventário. O ID é atribuído pelo repositório e o status vira AVAILABLE se não for informado.")
    public Car register(Car car) {
        return carInventory.register(car);
    }

    @Mutation
    @Description("Baixa (descomissiona) um veículo com base na placa; o veículo sai da oferta de disponibilidade e a linha não é apagada")
    public boolean remove(@Name("plate") String licensePlateNumber) {
        return carInventory.decommission(licensePlateNumber).isPresent();
    }

    // --- FIELD RESOLVER DINÂMICO (@Source) ---
    // Cria um campo virtual combinado chamado 'fullDescription' sem alterar a estrutura de dados original
    @Description("Gera uma string descritiva combinando fabricante e modelo do veículo")
    public String getFullDescription(@Source Car car) {
        return car.getManufacturer() + " " + car.getModel();
    }
}
