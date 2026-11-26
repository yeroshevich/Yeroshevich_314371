@echo off
rem TechnoSoft Bonus 1.0 - create the database and restore it from the backup (Windows 8 and later).
rem
rem   restore-db.bat [host] [port] [user] [database]
rem   defaults:       localhost 5432 postgres technosoft_bonus
rem
rem The PostgreSQL user must be allowed to create databases (for example, postgres).
rem Password: PGPASSWORD environment variable, otherwise the script asks for it once.
rem PostgreSQL client tools are searched in PATH, then in PG_BIN, then in
rem "%ProgramFiles%\PostgreSQL\<version>\bin".
rem db\technosoft_bonus.backup is restored with pg_restore; if pg_restore is missing,
rem db\technosoft_bonus.sql is executed with psql.
rem An existing database is never overwritten; to recreate it: set FORCE=1 and run again.
setlocal EnableExtensions
cd /d "%~dp0"

set "DB_HOST=%~1"
if "%DB_HOST%"=="" set "DB_HOST=localhost"
set "DB_PORT=%~2"
if "%DB_PORT%"=="" set "DB_PORT=5432"
set "DB_USER=%~3"
if "%DB_USER%"=="" set "DB_USER=postgres"
set "DB_NAME=%~4"
if "%DB_NAME%"=="" set "DB_NAME=technosoft_bonus"
set "BACKUP=db\technosoft_bonus.backup"
set "SQLFILE=db\technosoft_bonus.sql"

rem ---- PostgreSQL client tools -----------------------------------------------------------------
if defined PG_BIN goto :pgbin_set
where psql >nul 2>&1
if not errorlevel 1 goto :pgbin_path
for /d %%D in ("%ProgramFiles%\PostgreSQL\*") do if exist "%%~D\bin\psql.exe" set "PG_BIN=%%~D\bin"
if defined PG_BIN goto :pgbin_set
echo ERROR: PostgreSQL client tools (psql, pg_restore) not found.
echo Add "C:\Program Files\PostgreSQL\<version>\bin" to PATH or set PG_BIN to that folder.
exit /b 1

:pgbin_path
set "PSQL=psql"
set "CREATEDB=createdb"
set "DROPDB=dropdb"
set "PGRESTORE=pg_restore"
where pg_restore >nul 2>&1
if errorlevel 1 set "PGRESTORE="
goto :tools_ok

:pgbin_set
set "PSQL=%PG_BIN%\psql.exe"
set "CREATEDB=%PG_BIN%\createdb.exe"
set "DROPDB=%PG_BIN%\dropdb.exe"
set "PGRESTORE=%PG_BIN%\pg_restore.exe"
if not exist "%PSQL%" goto :no_psql
if not exist "%PGRESTORE%" set "PGRESTORE="

:tools_ok
set "MODE=sql"
if defined PGRESTORE if exist "%BACKUP%" set "MODE=backup"
if "%MODE%"=="sql" if not exist "%SQLFILE%" goto :no_backup

if not defined PGPASSWORD set /p "PGPASSWORD=Password of PostgreSQL user %DB_USER% (Enter if not needed): "

echo PostgreSQL server %DB_HOST%:%DB_PORT%, user %DB_USER%, database %DB_NAME%

if not "%FORCE%"=="1" goto :create
echo FORCE=1: dropping existing database %DB_NAME%
"%DROPDB%" -h %DB_HOST% -p %DB_PORT% -U %DB_USER% --if-exists %DB_NAME%
if errorlevel 1 goto :failed_drop

:create
echo 1/3 Creating database %DB_NAME% (UTF8)
"%CREATEDB%" -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -E UTF8 -T template0 %DB_NAME%
if errorlevel 1 goto :failed_create

if "%MODE%"=="sql" goto :restore_sql
echo 2/3 Restoring from %BACKUP% (pg_restore)
"%PGRESTORE%" -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% --no-owner --no-privileges --exit-on-error "%BACKUP%"
if errorlevel 1 goto :failed_restore
goto :verify

:restore_sql
echo 2/3 Restoring from %SQLFILE% (psql)
"%PSQL%" -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% -q -v ON_ERROR_STOP=1 -f "%SQLFILE%" >nul
if errorlevel 1 goto :failed_restore

:verify
echo 3/3 Check (employees, user accounts, operations):
"%PSQL%" -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% -At -c "select count(*) from employee union all select count(*) from app_user union all select count(*) from operation"
if errorlevel 1 goto :failed_restore
echo Done. Database %DB_NAME% is restored; start the server with start.bat.
exit /b 0

:no_psql
echo ERROR: "%PSQL%" not found. Check PG_BIN.
exit /b 1

:no_backup
echo ERROR: backup file not found (%BACKUP% or %SQLFILE%).
exit /b 1

:failed_drop
echo ERROR: could not drop database %DB_NAME%. Is the server stopped and are the credentials right?
exit /b 1

:failed_create
echo ERROR: database %DB_NAME% was not created. If it already exists, drop it or run again with FORCE=1:
echo   set FORCE=1
echo   restore-db.bat %DB_HOST% %DB_PORT% %DB_USER% %DB_NAME%
exit /b 1

:failed_restore
echo ERROR: restore failed. The partially created database %DB_NAME% is being dropped.
"%DROPDB%" -h %DB_HOST% -p %DB_PORT% -U %DB_USER% --if-exists %DB_NAME%
exit /b 1
