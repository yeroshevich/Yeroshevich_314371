package by.technosoft.bonus.domain.converter;

import by.technosoft.bonus.domain.OperationType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class OperationTypeConverter extends PersistentEnumConverter<OperationType> {

    public OperationTypeConverter() {
        super(OperationType.class);
    }
}
