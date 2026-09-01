#!/bin/sh
set -eu

bucket="${AWS_S3_BUCKET:-tikitaka-local}"
region="${AWS_DEFAULT_REGION:-ap-northeast-2}"

if ! awslocal s3api head-bucket --bucket "$bucket" 2>/dev/null; then
  awslocal s3 mb "s3://$bucket" --region "$region"
fi
