#!/bin/bash

set -euo pipefail
./$1 start $2 --single-run \
  --root 'plugins/delete-project/web/_bazel_ts_out_tests/' \
  --test-files '*_test.js'
