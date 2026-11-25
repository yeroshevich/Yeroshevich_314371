package by.technosoft.bonus.service.exception;

/** На счёте не хватает баллов (UC3; подтверждение корректировки). HTTP 409. */
public class InsufficientBalanceException extends BonusDomainException {

    public static final String CODE = "INSUFFICIENT_FUNDS";

    private final int balance;
    private final int required;

    public InsufficientBalanceException(int balance, int required, String message) {
        super(CODE, message);
        this.balance = balance;
        this.required = required;
    }

    /** Баланс счёта на момент проверки. */
    public int getBalance() {
        return balance;
    }

    /** Сколько баллов требовалось. */
    public int getRequired() {
        return required;
    }

    /** Недостающая сумма. */
    public int getShortage() {
        return required - balance;
    }
}
