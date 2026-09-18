package org.acme.inventory.database;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.acme.inventory.model.Car;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Getter
@ApplicationScoped
public class CarInventory {

    private List<Car> cars;

    public static final AtomicLong ids = new AtomicLong(0);

    @PostConstruct
    void initialize() {
        cars = new CopyOnWriteArrayList<>();
        initialData();
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
    }
}