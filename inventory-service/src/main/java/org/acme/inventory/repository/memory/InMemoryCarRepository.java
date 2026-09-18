package org.acme.inventory.repository.memory;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.inventory.model.Car;
import org.acme.inventory.repository.CarRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
@IfBuildProperty(name = "app.repository", stringValue = "memory", enableIfMissing = true)
public class InMemoryCarRepository implements CarRepository {

    private final List<Car> cars = new CopyOnWriteArrayList<>();
    private final AtomicLong ids = new AtomicLong(0);

    private static final String[] MANUFACTURERS = {
            "Mazda", "Ford", "Chevrolet", "Volkswagen",
            "Toyota", "Honda", "Fiat", "Renault",
            "Hyundai", "Nissan", "Jeep", "BMW",
            "Mercedes-Benz", "Audi", "Kia", "Peugeot",
            "Citroën", "Volvo", "Mitsubishi", "Subaru"
    };

    private static final String[] MODELS = {
            "6", "Mustang", "Camaro", "Gol",
            "Corolla", "Civic", "Uno", "Clio",
            "HB20", "Kicks", "Compass", "320i",
            "C180", "A3", "Sportage", "208",
            "C3", "XC40", "L200", "Impreza"
    };

    @PostConstruct
    void initialize() {
        initialData();
    }

    @Override
    public List<Car> findAll() {
        return cars;
    }

    @Override
    public Optional<Car> findByPlate(String licensePlateNumber) {
        return cars.stream()
                .filter(car -> car.getLicensePlateNumber().equals(licensePlateNumber))
                .findAny();
    }

    @Override
    public Car save(Car car) {
        if (car.getId() == null) {
            car.setId(ids.incrementAndGet());
        }
        cars.add(car);
        return car;
    }

    @Override
    public Optional<Car> deleteByPlate(String licensePlateNumber) {
        Optional<Car> toBeRemoved = findByPlate(licensePlateNumber);
        toBeRemoved.ifPresent(cars::remove);
        return toBeRemoved;
    }

    private void initialData() {
        cars.addAll(List.of(
                Car.builder().id(ids.incrementAndGet()).manufacturer("Mazda").model("6").licensePlateNumber("ABC123").build(),
                Car.builder().id(ids.incrementAndGet()).manufacturer("Ford").model("Mustang").licensePlateNumber("XYZ987").build(),
                Car.builder().id(ids.incrementAndGet()).manufacturer("Chevrolet").model("Camaro").licensePlateNumber("QWE321").build(),
                Car.builder().id(ids.incrementAndGet()).manufacturer("Volkswagen").model("Gol").licensePlateNumber("ASD456").build(),
                Car.builder().id(ids.incrementAndGet()).manufacturer("Toyota").model("Corolla").licensePlateNumber("ZXC789").build(),
                Car.builder().id(ids.incrementAndGet()).manufacturer("Honda").model("Civic").licensePlateNumber("RTY654").build(),
                Car.builder().id(ids.incrementAndGet()).manufacturer("Fiat").model("Uno").licensePlateNumber("UIO852").build(),
                Car.builder().id(ids.incrementAndGet()).manufacturer("Renault").model("Clio").licensePlateNumber("FGH741").build()
        ));

        for (int i = 0; i < 100; i++) {
            String manufacturer = MANUFACTURERS[i % MANUFACTURERS.length];
            String model = MODELS[(i / MANUFACTURERS.length) % MODELS.length];
            String plate = String.format("GEN%04d", i + 1);
            cars.add(
                    Car.builder()
                            .id(ids.incrementAndGet())
                            .manufacturer(manufacturer)
                            .model(model)
                            .licensePlateNumber(plate)
                            .build()
            );
        }
    }
}