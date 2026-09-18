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
         Car mazda = new Car();
        mazda.setId(ids.incrementAndGet());
        mazda.setManufacturer("Mazda");
        mazda.setModel("6");
        mazda.setLicensePlateNumber("ABC123");
        cars.add(mazda);
        Car ford = new Car();
        ford.setId(ids.incrementAndGet());
        ford.setManufacturer("Ford");
        ford.setModel("Mustang");
        ford.setLicensePlateNumber("XYZ987");
        cars.add(ford);
    }
}