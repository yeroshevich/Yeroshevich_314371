package by.technosoft.bonus.service.exception;

/** Программа мотивации неактивна (UC3). HTTP 409. */
public class ProgramUnavailableException extends BonusDomainException {

    public static final String CODE = "PROGRAM_INACTIVE";

    public ProgramUnavailableException(String message) {
        super(CODE, message);
    }
}
