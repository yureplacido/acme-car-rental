package org.acme.billing.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.acme.billing.domain.model.InvoiceLine;

import java.util.List;

/**
 * Serializa as linhas de fatura (value objects de domínio) como JSON na coluna
 * "lines". Isolamento: o modelo de domínio não conhece Jackson (ADRs).
 */
@Converter
public class InvoiceLinesConverter implements AttributeConverter<List<InvoiceLine>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<InvoiceLine>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<InvoiceLine> attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize invoice lines", e);
        }
    }

    @Override
    public List<InvoiceLine> convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not deserialize invoice lines", e);
        }
    }
}