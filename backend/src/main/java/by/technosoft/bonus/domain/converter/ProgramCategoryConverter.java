package by.technosoft.bonus.domain.converter;

import by.technosoft.bonus.domain.ProgramCategory;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProgramCategoryConverter extends PersistentEnumConverter<ProgramCategory> {

    public ProgramCategoryConverter() {
        super(ProgramCategory.class);
    }
}
