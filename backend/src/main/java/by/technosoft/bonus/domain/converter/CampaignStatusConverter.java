package by.technosoft.bonus.domain.converter;

import by.technosoft.bonus.domain.CampaignStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignStatusConverter extends PersistentEnumConverter<CampaignStatus> {

    public CampaignStatusConverter() {
        super(CampaignStatus.class);
    }
}
