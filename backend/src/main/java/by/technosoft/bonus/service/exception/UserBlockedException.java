package by.technosoft.bonus.service.exception;

/** Учётная запись заблокирована администратором (UC12): вход запрещён. HTTP 403. */
public class UserBlockedException extends BonusDomainException {

    public static final String CODE = "USER_BLOCKED";

    public UserBlockedException() {
        super(CODE, "Учётная запись заблокирована. Обратитесь к администратору системы.");
    }
}
