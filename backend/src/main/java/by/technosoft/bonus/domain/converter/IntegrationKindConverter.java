package by.technosoft.bonus.domain.converter;

import by.technosoft.bonus.domain.IntegrationKind;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class IntegrationKindConverter extends PersistentEnumConverter<IntegrationKind> {

    public IntegrationKindConverter() {
        super(IntegrationKind.class);
    }
}
