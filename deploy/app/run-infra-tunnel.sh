#!/bin/sh
set -eu
: "${INFRA_SSH_HOST:?required}" "${INFRA_SSH_USER:?required}" "${INFRA_SSH_KEY:?required}" "${INFRA_KNOWN_HOSTS:?required}"
exec /usr/bin/ssh -NT -p "${INFRA_SSH_PORT:-22}" \
  -i "$INFRA_SSH_KEY" -o BatchMode=yes -o IdentitiesOnly=yes \
  -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$INFRA_KNOWN_HOSTS" \
  -o ExitOnForwardFailure=yes -o ServerAliveInterval=15 -o ServerAliveCountMax=3 \
  -L "127.0.0.1:${REDIS_FORWARD_PORT:-16379}:127.0.0.1:${REDIS_REMOTE_PORT:-6379}" \
  -L "127.0.0.1:${MONGO_FORWARD_PORT:-27018}:127.0.0.1:${MONGO_REMOTE_PORT:-27017}" \
  -L "127.0.0.1:${RABBITMQ_FORWARD_PORT:-15673}:127.0.0.1:${RABBITMQ_REMOTE_PORT:-5672}" \
  -L "127.0.0.1:${ELASTICSEARCH_FORWARD_PORT:-19200}:127.0.0.1:${ELASTICSEARCH_REMOTE_PORT:-9200}" \
  -L "127.0.0.1:${MINIO_FORWARD_PORT:-19000}:127.0.0.1:${MINIO_REMOTE_PORT:-9000}" \
  "$INFRA_SSH_USER@$INFRA_SSH_HOST"
