kill -9 74539; подтверждено сервером до сбоя: 2706 операций
Перезапуск до «Started BonusApplication»: 3.17 с
Операций в БД: до нагрузки 159, после перезапуска 2867
Подтверждённые до сбоя операции найдены после перезапуска (status=accrued): 2706 из 2706
Счета, у которых баланс не равен сумме проведённых операций (проверочный запрос): 0
Сервер PostgreSQL: localhost:5432, пользователь valeriyyerashevich, база данных technosoft_bonus_replay_restored
FORCE=1: удаление существующей БД technosoft_bonus_replay_restored
NOTICE:  database "technosoft_bonus_replay_restored" does not exist, skipping
1/3 Создание БД technosoft_bonus_replay_restored (UTF8)
2/3 Восстановление из db/technosoft_bonus.backup (pg_restore)
3/3 Проверка
версия схемы 2
сотрудников 27
учётных записей 27
операций 2867
Готово. БД technosoft_bonus_replay_restored восстановлена; запуск сервера — start.sh.
Резервная копия pg_dump -Fc: 0.09 с, 188311 байт
Восстановление restore-db.sh в technosoft_bonus_replay_restored: 0.55 с
Строк во всех таблицах: исходная БД 8667, восстановленная 8667 (все таблицы совпадают)
