package by.technosoft.bonus.domain.converter;

import by.technosoft.bonus.domain.RulePeriodicity;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RulePeriodicityConverter extends PersistentEnumConverter<RulePeriodicity> {

    public RulePeriodicityConverter() {
        super(RulePeriodicity.class);
    }
}
