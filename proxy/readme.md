# Protoyp für die HTTP-Zugriffssteuerung (API's, S3, ...)

In diesem Prototyp wird erarbeitet, wie mit Apache apisix als PDP und OPA als PEP verschiedene Endpunkte zugriffsgeschützt werden.

Als Beispiel für den upstream Endpunkt wird traefik/whoami eingesetzt.

## Anforderungen für Proto

Zugriff lesend und schreibend für unterschiedliche Benutzer auf das User-API:

* Reader: Hat lesenden Zugriff auf die User-API
* Writer: Hat schreibenden Zugriff auf die User-API
* Other: Hat keinen Zugriff auf die User-API



## Prototyp ausführen

## Docker starten

In Verzeichnis docker/

    docker compose up

### Signed token von Mock Authz Server beziehen

Die issuer url muss übereinstimmen. Dies wird erreicht mittels ausführen von curl
innerhalb des docker netzwerks:

    docker exec -it client bash

In der client bash das curl ausführen:

    TOKEN=$(
    curl -s -X POST http://authn_srv:8080/default/token \
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

    curl http://apisix:9080/kbs -H "Authorization: Bearer $TOKEN"

Dieser Request wird in Richtung upstream weitergeleitet, da der user "reader" GET-Berechtigung auf die Url /kbs hat.

    curl http://apisix:9080/xbs -H "Authorization: Bearer $TOKEN"

Dieser Request wird geblockt, da der user "reader" keine Berechtigung auf die Url /xbs hat.
