@ECHO OFF
SETLOCAL
SET "BASE_DIR=%~dp0"
SET "BASE_DIR=%BASE_DIR:~0,-1%"
IF DEFINED JAVA_HOME (
  SET "JAVACMD=%JAVA_HOME%\bin\java.exe"
) ELSE (
  SET "JAVACMD=java.exe"
)
IF DEFINED JAVA_HOME IF NOT EXIST "%JAVACMD%" (
  ECHO DND Master requires a Java 21 executable. Set JAVA_HOME to a Java 21 JDK. 1>&2
  EXIT /B 1
)
"%JAVACMD%" -version 2>&1 | FINDSTR /R /C:"version .*21\." >NUL
IF ERRORLEVEL 1 (
  ECHO DND Master requires Java 21 LTS. JAVA_HOME or PATH selected a different JDK. 1>&2
  "%JAVACMD%" -version 1>&2
  EXIT /B 1
)
"%JAVACMD%" "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" -classpath "%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*
EXIT /B %ERRORLEVEL%
