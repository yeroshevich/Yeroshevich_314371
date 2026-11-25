package by.technosoft.bonus.domain.converter;

import by.technosoft.bonus.domain.AccountStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AccountStatusConverter extends PersistentEnumConverter<AccountStatus> {

    public AccountStatusConverter() {
        super(AccountStatus.class);
    }
}
