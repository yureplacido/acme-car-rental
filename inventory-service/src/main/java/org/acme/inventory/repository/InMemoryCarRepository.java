package org.acme.inventory.repository;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.inventory.model.Car;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class InMemoryCarRepository implements CarRepository {

    private final List<Car> cars = new CopyOnWriteArrayList<>();
    private static final AtomicLong ids = new AtomicLong(0);

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
    public long nextId() {
        return ids.incrementAndGet();
    }

    @Override
    public Car save(Car car) {
        cars.add(car);
        return car;
    }

    @Override
    public void remove(Car car) {
        cars.remove(car);
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