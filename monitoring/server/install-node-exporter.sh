#!/usr/bin/env bash
# Run with sudo only after reviewing this script. Does not restart the application.
set -euo pipefail
VERSION=1.12.1
case "$(uname -m)" in
  x86_64) ARCH=amd64 ;;
  aarch64) ARCH=arm64 ;;
  *) echo "Unsupported architecture" >&2; exit 1 ;;
esac
if [[ -e /etc/systemd/system/node_exporter.service || -e /usr/local/bin/node_exporter ]]; then
  echo "Existing node_exporter detected; inspect it before installing." >&2
  exit 1
fi
WORK_DIR=$(mktemp -d)
trap 'rm -rf -- "$WORK_DIR"' EXIT
cd "$WORK_DIR"
ARCHIVE="node_exporter-${VERSION}.linux-${ARCH}.tar.gz"
RELEASE="https://github.com/prometheus/node_exporter/releases/download/v${VERSION}"
curl --fail --silent --show-error --location --retry 3 -O "${RELEASE}/${ARCHIVE}"
curl --fail --silent --show-error --location --retry 3 -O "${RELEASE}/sha256sums.txt"
awk -v file="$ARCHIVE" '$2 == file {print}' sha256sums.txt > selected-sha256.txt
test -s selected-sha256.txt
sha256sum --check selected-sha256.txt
tar -xzf "$ARCHIVE"
id node_exporter >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin node_exporter
install -m 0755 "node_exporter-${VERSION}.linux-${ARCH}/node_exporter" /usr/local/bin/node_exporter
cat > /etc/systemd/system/node_exporter.service <<'UNIT'
[Unit]
Description=Prometheus Node Exporter (loopback only)
After=network.target

[Service]
User=node_exporter
Group=node_exporter
ExecStart=/usr/local/bin/node_exporter --web.listen-address=127.0.0.1:9100
Restart=on-failure
RestartSec=5
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
MemoryMax=64M
CPUQuota=20%

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable --now node_exporter
curl --fail --silent http://127.0.0.1:9100/metrics --output /dev/null
echo "Node Exporter ready on 127.0.0.1:9100"
