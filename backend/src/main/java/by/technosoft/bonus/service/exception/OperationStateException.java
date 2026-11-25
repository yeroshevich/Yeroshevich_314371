package by.technosoft.bonus.service.exception;

/** Действие недопустимо в текущем статусе операции (UC7, контроль финотдела). HTTP 409. */
public class OperationStateException extends BonusDomainException {

    public static final String CODE = "OPERATION_STATE_CONFLICT";

    public OperationStateException(String message) {
        super(CODE, message);
    }
}
