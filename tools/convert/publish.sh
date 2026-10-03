#!/bin/bash
# Push this job's logs to the branch ci-results-convert-<name> (readable without the Actions log site).
NAME=$1
mkdir -p out; echo "${GITHUB_SHA}" > out/commit.txt; free -g > out/memory.txt 2>&1; df -h / /mnt >> out/memory.txt 2>&1
cd out && git init -q -b r && git config user.email ci@nanogone && git config user.name nanogone-ci
git add -A && git commit -qm "$NAME" && git push -qf "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" r:ci-results-convert-$NAME
