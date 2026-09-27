package org.acme.inventory.adapter.out.persistence;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova o mapeamento JPA contra o MySQL de verdade. Falha de DDL no Hibernate Reactive
 * aparece so como WARN (HR000021) e a aplicacao sobe do mesmo jeito, entao um teste que
 * nao toca o banco nao enxerga schema nenhum — foi assim que o mapeamento sobreviveu.
 *
 * <p>A escrita passa pela mutation GraphQL porque o repositorio e reativo e exige um
 * contexto Vert.x duplicado, que a thread do JUnit nao tem.</p>
 */
@QuarkusTest
class VehicleEntityMappingTest {

    @Inject
    MySQLPool pool;

    @Test
    void shouldExposeTheFleetOnATableNamedVehicles() {
        List<String> tables = rows("select table_name as name from information_schema.tables where table_schema = database()")
                .stream()
                .map(row -> row.getString("name"))
                .toList();

        assertTrue(tables.contains("vehicles"),
                "a entidade deve mapear para a tabela vehicles, mas o schema tem: " + tables);
        assertFalse(tables.contains("cars"),
                "o nome antigo 'cars' nao deve sobrar no schema: " + tables);

        Long fleet = rows("select count(*) as total from vehicles").stream()
                .findFirst()
                .orElseThrow()
                .getLong("total");

        assertTrue(fleet >= 100, "o import.sql deve popular a frota, veio " + fleet + " linhas");
    }

    @Test
    void shouldRoundTripTheConditionColumn() {
        pool.query("update vehicles set `condition` = 'DAMAGED' where licensePlateNumber = 'ABC123'")
                .execute().await().indefinitely();

        String stored = rows("select `condition` from vehicles where licensePlateNumber = 'ABC123'")
                .stream()
                .findFirst()
                .orElseThrow()
                .getString("condition");

        assertEquals("DAMAGED", stored,
                "'condition' e palavra reservada no MySQL: sem escape o DDL falha com 1064");

        given()
                .contentType("application/json")
                .body("""
                        {"query":"{ findCar(plate: \\"ABC123\\") { plateNumber condition } }"}
                        """)
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("data.findCar.plateNumber", equalTo("ABC123"))
                .body("data.findCar.condition", equalTo("DAMAGED"));
    }

    private List<Row> rows(String sql) {
        RowSet<Row> result = pool.query(sql).execute().await().indefinitely();
        List<Row> rows = new ArrayList<>();
        result.forEach(rows::add);
        return rows;
    }
}
