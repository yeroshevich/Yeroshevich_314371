package by.technosoft.bonus.service;

/** Форматирование сумм в баллах для сообщений пользователю: «1 500 Б». */
public final class PointsFormat {

    private PointsFormat() {
    }

    public static String format(long points) {
        String digits = String.format("%,d", Math.abs(points)).replace(',', ' ');
        return (points < 0 ? "−" : "") + digits + " Б";
    }
}
