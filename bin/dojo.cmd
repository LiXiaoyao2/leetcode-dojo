@echo off
rem dojo 启动器（Windows 版）：工具源码有改动时自动重编译，然后把参数原样交给 jar。
rem 用法与 bin/dojo 相同：dojo doctor / dojo new 1 / dojo hint ...

setlocal
set "BIN_DIR=%~dp0"
set "DOJO_HOME=%BIN_DIR%.."
for %%I in ("%DOJO_HOME%") do set "DOJO_HOME=%%~fI"
set "JAR=%DOJO_HOME%\tool\target\dojo.jar"

rem jar 不存在，或有源文件比它新，就重编一次
set "NEED_BUILD=0"
if not exist "%JAR%" set "NEED_BUILD=1"
if "%NEED_BUILD%"=="0" (
  for /f "usebackq delims=" %%f in (`powershell -NoProfile -Command "$jar=Get-Item '%JAR%'; $newest=Get-ChildItem -Path '%DOJO_HOME%\tool\src\main' -Recurse -Filter *.java | Sort-Object LastWriteTime -Descending | Select-Object -First 1; if ($newest -and $newest.LastWriteTime -gt $jar.LastWriteTime) { 'yes' }"` ) do set "NEED_BUILD=1"
)

if "%NEED_BUILD%"=="1" (
  echo 编译 dojo 工具… 1>&2
  where mvn >nul 2>&1
  if errorlevel 1 (
    echo 找不到 mvn。dojo 需要 Maven 3.9+ 来编译工具；装好后再运行。 1>&2
    exit /b 1
  )
  call mvn -q -B -ntp -f "%DOJO_HOME%\pom.xml" -pl tool "-Dmaven.test.skip=true" package 1>&2
  if errorlevel 1 exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
  echo 找不到 java。dojo 需要 JDK 21；装好后再运行。 1>&2
  exit /b 1
)

rem 中文输出：Windows 控制台默认 GBK，这里把控制台与 JVM 一起切到 UTF-8
chcp 65001 >nul 2>&1
java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -jar "%JAR%" %*
