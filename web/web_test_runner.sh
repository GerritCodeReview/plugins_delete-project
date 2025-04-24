#!/bin/bash

set -euo pipefail
./$1 --config $2 \
  --dir 'plugins/delete-project/web/_bazel_ts_out_tests' \
  --test-files 'plugins/delete-project/web/_bazel_ts_out_tests/*_test.js' \
  --type-config="plugins/delete-project/web/tsconfig.json"
