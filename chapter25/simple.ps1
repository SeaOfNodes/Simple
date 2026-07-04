$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Release = Join-Path $Here "build\release"
$Jar = Join-Path $Release "Simple.jar"

if (-not $env:SIMPLE_HOME) {
    $env:SIMPLE_HOME = $Release
}

& java -jar $Jar @args
exit $LASTEXITCODE
