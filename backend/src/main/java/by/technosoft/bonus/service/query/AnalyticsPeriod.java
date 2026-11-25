package by.technosoft.bonus.service.query;

/** Длина сравниваемого периода дашборда (UC11). */
public enum AnalyticsPeriod {
    MONTH(1),
    QUARTER(3),
    HALFYEAR(6);

    private final int months;

    AnalyticsPeriod(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }
}
