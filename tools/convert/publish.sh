#!/bin/bash
# Push this job's logs to the branch ci-results-convert-<name> (readable without the Actions log site).
# Never fails the job: a missed report is not a reason to stop converting.
NAME=$1
mkdir -p out; echo "${GITHUB_SHA}" > out/commit.txt; date -u > out/time.txt; free -g > out/memory.txt 2>&1; df -h / /mnt >> out/memory.txt 2>&1
(cd out && rm -rf .git && git init -q -b r && git config user.email ci@nanogone && git config user.name nanogone-ci \
  && git add -A && git commit -qm "$NAME" && git push -qf "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" r:ci-results-convert-$NAME) || true
