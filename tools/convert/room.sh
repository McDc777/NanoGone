#!/bin/bash
# Free disk on a GitHub machine; work on the bigger disk; add swap only from space beyond the 40 GB the work needs.
sudo rm -rf /usr/share/dotnet /opt/ghc /usr/local/share/boost /usr/local/lib/android /usr/local/.ghcup /opt/hostedtoolcache/CodeQL || true
sudo docker image prune -af >/dev/null 2>&1 || true
ROOT=$(df --output=avail -BG / | tail -1 | tr -dc 0-9); MNT=$(df --output=avail -BG /mnt 2>/dev/null | tail -1 | tr -dc 0-9)
if [ "$(df --output=source /mnt | tail -1)" != "$(df --output=source / | tail -1)" ] && [ "${MNT:-0}" -gt "$ROOT" ]; then BASE=/mnt/big; FREE=$MNT; else BASE=$HOME/big; FREE=$ROOT; fi
sudo mkdir -p $BASE && sudo chown $USER $BASE
[ -e /mnt/w ] || sudo ln -s $BASE /mnt/w
SWAP=$(( FREE - 40 )); [ $SWAP -gt 24 ] && SWAP=24
if [ $SWAP -ge 4 ]; then sudo swapoff -a || true; sudo fallocate -l ${SWAP}G $BASE/swapfile && sudo chmod 600 $BASE/swapfile && sudo mkswap $BASE/swapfile >/dev/null && sudo swapon $BASE/swapfile; fi
echo "work disk $BASE, free ${FREE}G, swap ${SWAP}G"; free -g; df -h $BASE
