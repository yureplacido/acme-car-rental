package org.acme.inventory.api;

import io.smallrye.graphql.api.Context;
import jakarta.inject.Inject;
import org.acme.inventory.model.Person;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.GraphQLException;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@GraphQLApi
// @Description adiciona documentação global para a API que aparece no GraphiQL Docs
@Description("API de exemplo para gerenciamento do inventário de pessoas utilizando MicroProfile GraphQL")
public class GraphQLPeopleService {

    // Contexto injetado para obter metadados da requisição atual (ex: quais campos o cliente pediu)
    @Inject
    Context context;

    private final List<Person> people = new ArrayList<>(List.of(
            Person.builder().id("1").name("Yure Plácido").age(34).build(),
            Person.builder().id("2").name("John Petrucci").age(56).build()
    ));

    @Query("allPeople")
    @Description("Retorna a lista completa de todas as pessoas cadastradas na memória")
    public List<Person> getAllPeople() {
        // Exemplo de uso do Context: descobrindo o que o cliente solicitou na Query
        System.out.println("Campos solicitados pelo cliente: " + context.getSelectedFields());
        return people;
    }

    // @Query com filtro e parâmetro nomeado customizado
    @Query("findPerson")
    @Description("Busca uma pessoa específica através do seu identificador único")
    public Person findPersonById(@Name("personId") String id) throws GraphQLException {
        return people.stream()
                .filter(p -> p.getId().equals(id))
                .findAny()
                .orElseThrow(() -> new GraphQLException("Pessoa com o ID " + id + " não foi encontrada."));
    }

    @Mutation
    @Description("Cadastra uma nova pessoa no sistema")
    public Person addPerson(Person person) {
        Person newPerson = Person.builder()
                .id(person.getId())
                .name(person.getName())
                .age(person.getAge())
                .build();
        people.add(newPerson);
        return newPerson;
    }

    @Mutation
    @Description("Atualiza os dados cadastrais de uma pessoa existente")
    public Person updatePerson(Person person) throws GraphQLException {
        Optional<Person> existingPerson = people.stream()
                .filter(p -> p.getId().equals(person.getId()))
                .findAny();

        if (existingPerson.isPresent()) {
            Person current = existingPerson.get();
            if (person.getName() != null) current.setName(person.getName());
            if (person.getAge() != null) current.setAge(person.getAge());
            return current;
        }
        throw new GraphQLException("Não foi possível atualizar: ID inexistente.");
    }

    @Mutation
    @Description("Remove uma pessoa da memória com base no ID fornecido")
    public boolean removePerson(@Name("id") String id) throws GraphQLException {
        Optional<Person> toBeRemoved = people.stream()
                .filter(p -> p.getId().equals(id))
                .findAny();
        if (toBeRemoved.isPresent()) {
            people.remove(toBeRemoved.get());
            return true;
        }
        throw new GraphQLException("Não foi possível remover: ID inexistente.");
    }

    // --- RESOLVER DE CAMPOS DINÂMICOS ---
    // A anotação @Source estende o tipo "Person" dinamicamente sem alterar a classe original do banco de dados.
    // Útil para campos calculados, joins ou carregamento tardio (lazy loading).
    @Description("Calcula dinamicamente o ano aproximado de nascimento baseado na idade atual")
    public int getBirthYear(@Source Person person, @DefaultValue("2026") int currentYear) {
        if (person.getAge() == null) return 0;
        return currentYear - person.getAge();
    }
}
