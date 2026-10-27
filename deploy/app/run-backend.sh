#!/bin/sh
set -eu
: "${JAVA_HOME:?required}" "${APP_JAR:?required}" "${PUBLIC_HOST:?required}"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-https://$PUBLIC_HOST}"
export MINIO_PUBLIC_ENDPOINT="${MINIO_PUBLIC_ENDPOINT:-https://$PUBLIC_HOST}"
export ALIPAY_SANDBOX_NOTIFY_URL="${ALIPAY_SANDBOX_NOTIFY_URL:-https://$PUBLIC_HOST/api/v1/payment-notifications/alipay-sandbox}"
exec "$JAVA_HOME/bin/java" -jar "$APP_JAR"
