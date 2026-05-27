#!/usr/bin/env bash

set -euo pipefail

export NETL_UID=$(id -u)
export NETL_GID=$(id -g)

echo "Using NETL_UID=$NETL_UID NETL_GID=$NETL_GID"

docker compose up -d