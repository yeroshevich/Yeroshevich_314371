package by.technosoft.bonus.domain.converter;

import by.technosoft.bonus.domain.ReviewDecision;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReviewDecisionConverter extends PersistentEnumConverter<ReviewDecision> {

    public ReviewDecisionConverter() {
        super(ReviewDecision.class);
    }
}
