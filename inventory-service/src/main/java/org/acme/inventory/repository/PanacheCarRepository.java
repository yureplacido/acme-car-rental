package org.acme.inventory.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.inventory.entity.CarEntity;
import org.acme.inventory.model.graphql.Car;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementação Panache (Repository pattern via {@link PanacheRepository}) sobre
 * {@link CarEntity}, mapeando para o modelo de domínio com {@link CarMapper}.
 * É a impl padrão do contrato {@link CarRepository}; pode ser trocada por uma
 * Active Record sem alterar a API.
 */
@ApplicationScoped
public class PanacheCarRepository implements CarRepository, PanacheRepository<CarEntity> {

    @Override
    public List<Car> all() {
        return listAll().stream()
                .map(CarMapper::toModel)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Car> findByLicensePlateNumberOptional(String licensePlateNumber) {
        return find("licensePlateNumber", licensePlateNumber)
                .firstResultOptional()
                .map(CarMapper::toModel);
    }

    @Override
    public Car save(Car car) {
        CarEntity entity = CarMapper.toEntity(car);
        if (entity.id == null) {
            // Insert: o id é atribuído pelo banco (IDENTITY) e volta no model.
            persist(entity);
            return CarMapper.toModel(entity);
        }
        // Update: o carro veio de outra requisição (ex.: descomissionamento),
        // então é uma entidade detached — merge reconcilia com o contexto atual.
        return CarMapper.toModel(getEntityManager().merge(entity));
    }
}