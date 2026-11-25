package by.technosoft.bonus.service.exception;

/** Email уже занят другой учётной записью (альт. поток UC12). HTTP 409. */
public class DuplicateEmailException extends ConflictException {

    public static final String CODE = "EMAIL_EXISTS";

    public DuplicateEmailException(String email) {
        super(CODE, "Пользователь с email " + email + " уже есть.");
    }
}
