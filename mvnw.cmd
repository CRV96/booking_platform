@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Optional ENV vars
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose log; others: silence the output
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_PSMODULEP_SAVE__=%PSModulePath%
@SET PSModulePath=
@FOR /F "usebackq tokens=1* delims==" %%A IN (`powershell -noprofile "& {$scriptDir='%~dp0teleporter'; $, $, $hash = ([Security.Cryptography.MD5]::Create().ComputeHash([IO.File]::ReadAllBytes('%~f0')) | ForEach-Object ToString x2); Get-Content -Raw '%~dp0.mvn/wrapper/maven-wrapper.properties' | ForEach-Object { if ($_ -match '^\s*distributionUrl\s*=\s*(.+)$') { 'MVNW_REPOURL='+$Matches[1].Trim(); ''; return }}}"`) DO @(
  IF NOT "%%A"=="" (
    IF "%%A"=="MVNW_REPOURL" (SET "MVNW_REPOURL=%%B")
  )
)
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE__%

@IF "%MVNW_REPOURL%"=="" (
  @SET __MVNW_ERROR__=distributionUrl is not set in .mvn/wrapper/maven-wrapper.properties
  @GOTO error
)

@SET __MVNW_PATH_SEPARATOR__=;
@SET "MAVEN_USER_HOME=%MAVEN_USER_HOME:~0,-1%"
@IF "%MAVEN_USER_HOME%"=="" SET "MAVEN_USER_HOME=%USERPROFILE%\.m2"
@FOR /F "usebackq tokens=1,2 delims==" %%A IN (`powershell -noprofile "& {$h = [System.BitConverter]::ToString([System.Text.Encoding]::UTF8.GetBytes('%MVNW_REPOURL%')).Replace('-', '').ToLower(); $h = $h.Substring(0, [Math]::Min(8, $h.Length)); Write-Output \"MVNW_HASH=$h\"}"`) DO @(
  IF "%%A"=="MVNW_HASH" SET "MVNW_HASH=%%B"
)

@FOR %%A IN ("%MVNW_REPOURL%") DO @SET "__MVNW_NAME__=%%~nxA"
@SET "__MVNW_NAME__=%__MVNW_NAME__:-bin.zip=%"
@SET "MAVEN_HOME=%MAVEN_USER_HOME%\wrapper\dists\%__MVNW_NAME__%\%MVNW_HASH%"

@IF EXIST "%MAVEN_HOME%\bin\mvn.cmd" GOTO execute

@ECHO Downloading Maven from: %MVNW_REPOURL%
@SET "__MVNW_TMP__=%TEMP%\mvnw_%RANDOM%_%TIME:~6,2%%TIME:~9,2%"
@MKDIR "%__MVNW_TMP__%"

@powershell -noprofile "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%MVNW_REPOURL%' -OutFile '%__MVNW_TMP__%\maven.zip' }"
@IF %ERRORLEVEL% NEQ 0 (
  @SET __MVNW_ERROR__=Download failed
  @GOTO error
)

@ECHO Unzipping Maven distribution
@powershell -noprofile "& { Expand-Archive -Path '%__MVNW_TMP__%\maven.zip' -DestinationPath '%__MVNW_TMP__%' }"
@IF %ERRORLEVEL% NEQ 0 (
  @SET __MVNW_ERROR__=Unzip failed
  @GOTO error
)

@MKDIR "%MAVEN_HOME%\.." 2>NUL
@MOVE "%__MVNW_TMP__%\%__MVNW_NAME__%" "%MAVEN_HOME%"
@IF %ERRORLEVEL% NEQ 0 (
  @SET __MVNW_ERROR__=Could not move Maven distribution
  @GOTO error
)

@RMDIR /S /Q "%__MVNW_TMP__%"

:execute
@SET "PATH=%MAVEN_HOME%\bin;%PATH%"
@SET MAVEN_CMD_LINE_ARGS=%*
"%MAVEN_HOME%\bin\mvn.cmd" %*
@GOTO :eof

:error
@ECHO.
@ECHO Error: %__MVNW_ERROR__%
@EXIT /B 1
