package org.acme.rental.repository.mongo;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.rental.model.Rental;
import org.acme.rental.repository.RentalRepository;

import java.util.List;

/**
 * Adapter MongoDB para produção, ativo quando {@code app.repository=mongo}. Skeleton:
 * a implementação real (mapa {@code Rental} ↔ {@code RentalEntity} via Panache) entra
 * na migração do ch.7, quando o tipo do id do domínio (hoje {@code Long}) for alinhado
 * ao {@code ObjectId} gerado pelo MongoDB e a conexão for configurada.
 */
@ApplicationScoped
@IfBuildProperty(name = "app.repository", stringValue = "mongo", enableIfMissing = false)
public class MongoRentalRepository implements RentalRepository {

    @Override
    public List<Rental> findAll() {
        throw new UnsupportedOperationException(
                "Persistência MongoDB ainda não ativada. Migração prevista no livro (ch.7) com alinhamento do id (Long -> ObjectId).");
    }

    @Override
    public Rental save(Rental rental) {
        throw new UnsupportedOperationException(
                "Persistência MongoDB ainda não ativada. Migração prevista no livro (ch.7) com alinhamento do id (Long -> ObjectId).");
    }
}