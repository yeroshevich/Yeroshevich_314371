package by.technosoft.bonus.domain.converter;

import by.technosoft.bonus.domain.OperationStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class OperationStatusConverter extends PersistentEnumConverter<OperationStatus> {

    public OperationStatusConverter() {
        super(OperationStatus.class);
    }
}
