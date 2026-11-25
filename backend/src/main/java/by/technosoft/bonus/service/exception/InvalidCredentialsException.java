package by.technosoft.bonus.service.exception;

/** Неверный email или пароль при входе (SEC-1). HTTP 401. */
public class InvalidCredentialsException extends BonusDomainException {

    public static final String CODE = "BAD_CREDENTIALS";

    public InvalidCredentialsException() {
        super(CODE, "Неверный email или пароль. Проверьте раскладку и попробуйте ещё раз.");
    }
}
