$ErrorActionPreference = 'Stop'
mvn -f (Join-Path $PSScriptRoot 'pom.xml') -DskipTests package dependency:copy-dependencies
