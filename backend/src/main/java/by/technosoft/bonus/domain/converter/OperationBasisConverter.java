package by.technosoft.bonus.domain.converter;

import by.technosoft.bonus.domain.OperationBasis;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class OperationBasisConverter extends PersistentEnumConverter<OperationBasis> {

    public OperationBasisConverter() {
        super(OperationBasis.class);
    }
}
