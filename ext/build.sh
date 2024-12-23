#!/bin/bash

set -eou pipefail

export PROJECT_NAME=$1
export SERVICE_NAME=$2
export ENV_NAME_UPPER=$3

SESSION_TOKEN=$(curl -s \
	          -X PUT "http://169.254.169.254/latest/api/token"\
		  -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")

ROLE_NAME=$(curl -H "X-aws-ec2-metadata-token: $SESSION_TOKEN" \
	           http://169.254.169.254/latest/meta-data/iam/security-credentials/)

CREDENTIALS=$(curl -H "X-aws-ec2-metadata-token: $SESSION_TOKEN" \
	       http://169.254.169.254/latest/meta-data/iam/security-credentials/$ROLE_NAME)

export AWS_DEFAULT_REGION=$(curl -s -H "X-aws-ec2-metadata-token: $SESSION_TOKEN" \
             http://169.254.169.254/latest/dynamic/instance-identity/document \
	         | jq -r .region)

export AWS_ACCESS_KEY_ID=$(echo $CREDENTIALS | jq -r '.AccessKeyId')
export AWS_SECRET_ACCESS_KEY=$(echo $CREDENTIALS | jq -r '.SecretAccessKey')
export AWS_SESSION_TOKEN=$(echo $CREDENTIALS | jq -r '.Token')
export TARGET_ACCOUNT_ID="$(aws sts get-caller-identity | jq -r '.Account')"

export DOCKER_BUILDKIT=1

export LATEST_IMAGE="$(aws ec2 describe-images \
                          --owners self --no-paginate  \
			  | jq -r '.Images[].Name' \
			  | grep build-${PROJECT_NAME}-${SERVICE_NAME}  \
			  | sort | tail -1)"

echo "Last image found: $LATEST_IMAGE"

if [[ "$LATEST_IMAGE" == "" ]]; then
  export BUILD_ID="0"
else
  export BUILD_ID="${LATEST_IMAGE##*.b}"
fi
export BUILD_ID=$((BUILD_ID+1))
echo "New build id: $BUILD_ID"

mkdir -p cert
cp /etc/pki/ca-trust/source/anchors/* cert/

arg_http_proxy="--build-arg http_proxy=${http_proxy:-}"
arg_https_proxy="--build-arg https_proxy=${https_proxy:-}"
arg_no_proxy="--build-arg no_proxy=${no_proxy:-}"
arg_HTTP_PROXY="--build-arg HTTP_PROXY=${HTTP_PROXY:-}"
arg_HTTPS_PROXY="--build-arg HTTPS_PROXY=${HTTPS_PROXY:-}"
arg_NO_PROXY="--build-arg NO_PROXY=${NO_PROXY:-}"

docker build --progress=plain \
             --network=host \
             --no-cache \
             --pull \
	     --build-arg BUILD_ID="${BUILD_ID}" \
	     --build-arg BuildId="${BUILD_ID}" \
	     --build-arg AWS_REGION="${AWS_DEFAULT_REGION}" \
      	     --build-arg AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
	     --build-arg DOCKER_REGISTRY_URL="${DOCKER_REGISTRY_URL}" \
             --build-arg PROJECT_NAME="${PROJECT_NAME}" \
             ${arg_http_proxy} \
	     ${arg_https_proxy} \
	     ${arg_no_proxy} \
	     ${arg_HTTP_PROXY} \
      	     ${arg_HTTPS_PROXY} \
	     ${arg_NO_PROXY} \
	     -t ${PROJECT_NAME}-${SERVICE_NAME}:b${BUILD_ID} \
	     -f Dockerfile .

