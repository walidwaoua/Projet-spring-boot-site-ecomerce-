param(
    [switch]$SkipStart
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot

function Get-ProjectSetting([string]$name, [string]$defaultValue) {
    $environmentValue = [Environment]::GetEnvironmentVariable($name)
    if (-not [string]::IsNullOrWhiteSpace($environmentValue)) {
        return $environmentValue
    }

    $envFile = Join-Path $projectRoot '.env'
    if (Test-Path $envFile) {
        $line = Get-Content $envFile | Where-Object { $_ -match "^\s*$name\s*=" } | Select-Object -First 1
        if ($line) {
            return (($line -split '=', 2)[1]).Trim().Trim('"').Trim("'")
        }
    }

    return $defaultValue
}

function Invoke-RabbitAdmin([string[]]$rabbitAdminArgs) {
    $output = docker compose exec -T rabbitmq rabbitmqadmin --username $rabbitUser --password $rabbitPassword @rabbitAdminArgs
    if ($LASTEXITCODE -ne 0) {
        throw "rabbitmqadmin a echoue : $($rabbitAdminArgs -join ' ')"
    }
    return ($output -join "`n")
}

try {
    if (-not $SkipStart) {
        docker compose up -d --wait
        if ($LASTEXITCODE -ne 0) {
            throw "Docker Compose n'a pas réussi à démarrer les dépendances."
        }
    }

    $postgresUser = Get-ProjectSetting 'POSTGRES_USER' 'postgres'
    $rabbitUser = Get-ProjectSetting 'RABBITMQ_USERNAME' 'appuser'
    $rabbitPassword = Get-ProjectSetting 'RABBITMQ_PASSWORD' 'app_password'
    $databaseQuery = "SELECT datname FROM pg_database WHERE datistemplate = false AND datname IN ('service_commande','service_stock','service_notification','service_catalogue','service_utilisateurs') ORDER BY datname;"
    $databases = @(docker compose exec -T postgres psql -U $postgresUser -d postgres -Atc $databaseQuery)
    if ($LASTEXITCODE -ne 0) {
        throw 'La vérification de PostgreSQL a échoué.'
    }
    $expectedDatabases = @('service_catalogue', 'service_commande', 'service_notification', 'service_stock', 'service_utilisateurs')
    if ((Compare-Object $expectedDatabases $databases).Count -gt 0) {
        throw "Bases PostgreSQL attendues : $($expectedDatabases -join ', '). Bases trouvées : $($databases -join ', ')."
    }
    foreach ($database in $expectedDatabases) {
        $connectedDatabase = docker compose exec -T postgres psql -U $postgresUser -d $database -Atc 'SELECT current_database();'
        if ($LASTEXITCODE -ne 0 -or (($connectedDatabase -join '').Trim() -ne $database)) {
            throw "Connexion impossible a la base PostgreSQL $database."
        }
    }
    Write-Host 'OK PostgreSQL: les cinq bases existent et acceptent les connexions.' -ForegroundColor Green

    $testId = [Guid]::NewGuid().ToString('N')
    $exchange = "codex.rabbitmq.smoke.$testId"
    $routingKey = 'codex.rabbitmq.smoke'
    $queue = "codex.rabbitmq.smoke.$testId"
    $queueCreated = $false
    $exchangeCreated = $false

    try {
        Invoke-RabbitAdmin @('declare', 'exchange', "name=$exchange", 'type=topic', 'durable=false', 'auto_delete=false') | Out-Null
        $exchangeCreated = $true
        Invoke-RabbitAdmin @('declare', 'queue', "name=$queue", 'durable=false', 'auto_delete=true') | Out-Null
        $queueCreated = $true
        Invoke-RabbitAdmin @('declare', 'binding', "source=$exchange", "destination=$queue", 'destination_type=queue', "routing_key=$routingKey") | Out-Null

        $marker = "rabbit-smoke-$([Guid]::NewGuid().ToString('N'))"
        $event = @{
            orderId    = 1
            userId     = 1
            status     = 'CONFIRMEE'
            message    = $marker
            occurredAt = [DateTime]::UtcNow.ToString('o')
        }
        $payload = ConvertTo-Json -InputObject $event -Compress
        Invoke-RabbitAdmin @('publish', "exchange=$exchange", "routing_key=$routingKey", "payload=$payload") | Out-Null
        $receivedJson = Invoke-RabbitAdmin @('--format=raw_json', 'get', "queue=$queue", 'count=1', 'ackmode=ack_requeue_false')
        $received = @($receivedJson | ConvertFrom-Json)
        if ($received.Count -eq 0 -or $received[0].payload -notlike "*$marker*") {
            throw 'Le message publie dans RabbitMQ est introuvable dans la file.'
        }
        Write-Host 'OK RabbitMQ: un evenement de commande a ete route et consomme.' -ForegroundColor Green
    }
    finally {
        if ($queueCreated) {
            Invoke-RabbitAdmin @('delete', 'queue', "name=$queue") | Out-Null
        }
        if ($exchangeCreated) {
            Invoke-RabbitAdmin @('delete', 'exchange', "name=$exchange") | Out-Null
        }
    }

    Write-Host 'Verifications Docker terminees.' -ForegroundColor Green
}
finally {
    Pop-Location
}
