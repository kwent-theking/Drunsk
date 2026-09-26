@echo off
REM Builds Drunsk with the cached Gradle 9.6.1 on JDK 23 (same recipe as LangCrypt).
REM Ð¡Ð¾Ð±Ð¸Ñ€Ð°ÐµÑ‚ Drunsk ÐºÑÑˆÐ¸Ñ€Ð¾Ð²Ð°Ð½Ð½Ñ‹Ð¼ Gradle 9.6.1 Ð½Ð° JDK 23 (Ñ‚Ð¾Ñ‚ Ð¶Ðµ Ñ€ÐµÑ†ÐµÐ¿Ñ‚, Ñ‡Ñ‚Ð¾ LangCrypt).
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-23.0.2.7-hotspot"
set "GRADLE=C:\Users\kwent\.gradle\wrapper\dists\gradle-9.6.1-bin\4ticwg1pgcbps2hj28r8so764\gradle-9.6.1\bin\gradle.bat"
call "%GRADLE%" build --console=plain %*
