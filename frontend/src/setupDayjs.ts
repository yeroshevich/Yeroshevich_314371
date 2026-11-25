import dayjs from 'dayjs';
import 'dayjs/locale/ru';

// Подключается первым импортом в main.tsx: мок-данные форматируют даты при загрузке модуля.
dayjs.locale('ru');
