package by.technosoft.bonus.service.exception;

/** Бонусный счёт заблокирован: операции по нему недоступны (альт. поток UC1, UC3, UC5). HTTP 423. */
public class AccountBlockedException extends BonusDomainException {

    public static final String CODE = "ACCOUNT_BLOCKED";

    public AccountBlockedException(String message) {
        super(CODE, message);
    }
}
