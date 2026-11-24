#!/usr/bin/env bash
# 首次部署时，将路由规则推送到 Nacos（网关启动后会自动热更）。
# 用法：bash scripts/nacos-init.sh  （可通过 NACOS_ADDR/DATA_ID/GROUP 环境变量覆盖默认值）
set -euo pipefail

NACOS_ADDR="${NACOS_ADDR:-localhost:8848}"
DATA_ID="${DATA_ID:-lumina-gateway-routing}"
GROUP="${GROUP:-DEFAULT_GROUP}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTENT_FILE="${SCRIPT_DIR}/../deploy/nacos-config-example.json"

echo "推送 ${CONTENT_FILE} -> Nacos(${NACOS_ADDR}) dataId=${DATA_ID} group=${GROUP}"
curl -s -X POST "http://${NACOS_ADDR}/nacos/v1/cs/configs" \
  --data-urlencode "dataId=${DATA_ID}" \
  --data-urlencode "group=${GROUP}" \
  --data-urlencode "type=json" \
  --data-urlencode "content@${CONTENT_FILE}" \
  && echo -e "\nOK"
