#!/bin/sh
set -eu
# Copy read-only Windows mounts to enforce OpenSSH's required permissions.
cp /run/ssh-key /tmp/ssh-key
chmod 600 /tmp/ssh-key
exec ssh -N -T -i /tmp/ssh-key \
  -o BatchMode=yes -o StrictHostKeyChecking=yes \
  -o UserKnownHostsFile=/run/known-hosts \
  -o ExitOnForwardFailure=yes -o ConnectTimeout=10 \
  -o ServerAliveInterval=15 -o ServerAliveCountMax=3 \
  -L 0.0.0.0:19100:127.0.0.1:9100 \
  -L 0.0.0.0:19101:127.0.0.1:9101 \
  "${SSH_USER}@${SSH_HOST}"
