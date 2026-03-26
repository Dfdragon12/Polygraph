@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements. See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership. The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License. You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied. See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM Apache Maven Wrapper startup batch script, version 3.3.2

@SETLOCAL
@SET "BASE_DIR=%~dp0"
@SET "MAVEN_WRAPPER_JAR=%BASE_DIR%.mvn\wrapper\maven-wrapper.jar"
@SET "MAVEN_WRAPPER_PROPERTIES=%BASE_DIR%.mvn\wrapper\maven-wrapper.properties"
@SET "WRAPPER_URL="
@SET "DISTRIBUTION_URL="

@FOR /F "usebackq tokens=1,* delims==" %%A IN ("%MAVEN_WRAPPER_PROPERTIES%") DO (
  @IF "%%A"=="wrapperUrl" @SET "WRAPPER_URL=%%B"
  @IF "%%A"=="distributionUrl" @SET "DISTRIBUTION_URL=%%B"
)

@IF NOT EXIST "%MAVEN_WRAPPER_JAR%" (
  @IF NOT "%WRAPPER_URL%"=="" (
    @ECHO Descargando maven-wrapper.jar...
    @curl -o "%MAVEN_WRAPPER_JAR%" "%WRAPPER_URL%" -f -s
    @IF ERRORLEVEL 1 (
      @ECHO ERROR: No se pudo descargar maven-wrapper.jar
      @EXIT /B 1
    )
  ) ELSE (
    @ECHO ERROR: No se encontro maven-wrapper.jar ni wrapperUrl
    @EXIT /B 1
  )
)

@IF "%JAVA_HOME%"=="" (
  @SET "JAVA_CMD=java"
) ELSE (
  @SET "JAVA_CMD=%JAVA_HOME%\bin\java"
)

@"%JAVA_CMD%" ^
  -classpath "%MAVEN_WRAPPER_JAR%" ^
  "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" ^
  org.apache.maven.wrapper.MavenWrapperMain ^
  "%DISTRIBUTION_URL%" ^
  %*

@ENDLOCAL
