#!/bin/sh
set -eu

bucket="${AWS_S3_BUCKET:-tikitaka-local}"
region="${AWS_DEFAULT_REGION:-ap-northeast-2}"

if [ "$region" = "us-east-1" ]; then
  awslocal s3api create-bucket --bucket "$bucket"
else
  awslocal s3api create-bucket --bucket "$bucket" \
    --create-bucket-configuration "LocationConstraint=$region"
fi
