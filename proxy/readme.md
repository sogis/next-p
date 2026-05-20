# Protoyp für die HTTP-Zugriffssteuerung (API's, S3, ...)

In diesem Prototyp wird erarbeitet, wie mit Apache apisix als PDP und OPA als PEP verschiedene Endpunkte zugriffsgeschützt werden.

Als Beispiel für den Endpunkt wird mittels Caddy eine REST-API simuliert.

## Anforderungen für Proto

Zugriff lesend und schreibend für unterschiedliche Benutzer auf das User-API:

* Reader: Hat lesenden Zugriff auf die User-API
* Writer: Hat schreibenden Zugriff auf die User-API
* Other: Hat keinen Zugriff auf die User-API

## Authz Vorschritte

### Über Consumers und API-Key:

    curl -i http://127.0.0.1:8088/api/users -H 'apikey: reader-key'

### Über Consumers und Basic-Auth:

    curl -i http://127.0.0.1:8088/api/users -u reader-key:reader-key

## Authz

### Signed token von Mock Authz Server beziehen

Die issuer url muss übereinstimmen. Dies wird erreicht mittels ausführen von curl
innerhalb des docker netzwerks:

    docker exec -it client bash

In der client bash das curl ausführen:

    TOKEN=$(
    curl -s -X POST http://authz_srv:8080/default/token \
        -H 'Content-Type: application/x-www-form-urlencoded' \
        -d 'grant_type=client_credentials' \
        -d 'client_id=reader' \
        -d 'client_secret=secret' \
        -d 'scope=openid profile email' \
    | jq -r '.access_token'
    )

    echo "$TOKEN"

Der Mock-Server codiert die client-id als "sub" in das token

### Apisix mit dem zuletzt ausgestellten Token aufrufen

Ebenfalls in der client bash ausführen:

    curl http://apisix:9080/your-route -H "Authorization: Bearer $TOKEN"

## Diverses

Github-Issue, in dem gemäss KI das Consumer-Mapping für das openid-connect plugin gefordert wird: https://github.com/apache/apisix/issues/13037

## Todo: Erweitern mit Casbin