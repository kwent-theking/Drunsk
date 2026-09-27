@echo off
REM Builds Drunsk (MC 26.2, Java 25) with cached Gradle 9.6.1.
REM Sobiraet Drunsk (MC 26.2, Java 25) keshirovannym Gradle 9.6.1.
set "JAVA_HOME=C:\Program Files\Java\jdk-25.0.2"
set "GRADLE=C:\Users\kwent\.gradle\wrapper\dists\gradle-9.6.1-bin\4ticwg1pgcbps2hj28r8so764\gradle-9.6.1\bin\gradle.bat"
call "%GRADLE%" build --console=plain %*
