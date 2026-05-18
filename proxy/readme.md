# Protoyp für die HTTP-Zugriffssteuerung (API's, S3, ...)

In diesem Prototyp wird erarbeitet, wie mit Apache apisix als PDP und OPA als PEP verschiedene Endpunkte zugriffsgeschützt werden.

Als Beispiel für den Endpunkt wird mittels Caddy eine REST-API simuliert.

## Anforderungen für Proto

Zugriff lesend und schreibend für unterschiedliche Benutzer auf das User-API