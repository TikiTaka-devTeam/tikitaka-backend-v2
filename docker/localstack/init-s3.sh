#!/bin/sh
set -eu

awslocal s3api create-bucket --bucket "${AWS_S3_BUCKET:-tikitaka-local}"
