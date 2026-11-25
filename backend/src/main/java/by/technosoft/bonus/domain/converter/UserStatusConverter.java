package by.technosoft.bonus.domain.converter;

import by.technosoft.bonus.domain.UserStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserStatusConverter extends PersistentEnumConverter<UserStatus> {

    public UserStatusConverter() {
        super(UserStatus.class);
    }
}
