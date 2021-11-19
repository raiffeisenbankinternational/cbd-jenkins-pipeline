#!/bin/bash

set -euxo pipefail

echo "Started deploy.sh"

ls -la /dist/ansible/deploy/roles

cd /dist
export HOME=/dist

echo "Checking current user"
id

echo "Docker socket permissions"
ls -la /var/run/docker.sock

echo "Prepare request directory"
export work_dir="${BUILD_ID}"
mkdir -p $work_dir

echo "We are running inside ${work_dir}"

echo "Setting up ansible directories"
mkdir -p $work_dir/group_vars

region=eu-west-1
export AWS_DEFAULT_REGION=${region}

echo "Assuming role in target account"
SESSION=$(aws sts assume-role \
            --role-arn arn:aws:iam::${TargetAccountId}:role/PipelineRole \
            --role-session-name "${ServiceName}-deployment-${BUILD_ID}" \
            --endpoint https://sts.${region}.amazonaws.com \
            --region ${AWS_DEFAULT_REGION})

CURRENT_ROLE=$(curl http://169.254.169.254/latest/meta-data/iam/security-credentials)
curl -o security-credentials.json http://169.254.169.254/latest/meta-data/iam/security-credentials/${CURRENT_ROLE}/

export PIPELINE_AWS_ACCESS_KEY_ID=$(cat security-credentials.json | jq -r '.AccessKeyId')
export PIPELINE_AWS_SECRET_ACCESS_KEY=$(cat security-credentials.json | jq -r '.SecretAccessKey')
export PIPELINE_AWS_SESSION_TOKEN=$(cat security-credentials.json | jq -r '.Token')
export PIPELINE_ACCOUNT_ID=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | jq -r '.accountId')

export AWS_ACCESS_KEY_ID=$(echo $SESSION | jq -r '.Credentials.AccessKeyId')
export AWS_SECRET_ACCESS_KEY=$(echo $SESSION | jq -r '.Credentials.SecretAccessKey')
export AWS_SESSION_TOKEN=$(echo $SESSION | jq -r '.Credentials.SessionToken')

echo "Fetching VPC CIDR"
VPC_ID=$(aws ec2 describe-vpcs \
	   --query "Vpcs[*].VpcId" \
	   --filter "Name=tag:Name,Values=aws-controltower-*" \
	   --output text)

VPC_CIDR=$(aws ec2 describe-vpcs \
	     --query "Vpcs[*].CidrBlock" \
	     --filter "Name=tag:Name,Values=aws-controltower-*"  \
	     --output text)


echo "Fetching subnets"
SUBNET_IDS=$(aws ec2 describe-subnets \
    --query "Subnets[*].{Id:SubnetId,Name:Tags[?Key=='Name']|[0].Value}" \
    --output text | awk '{printf $1":"$2"\n"}')

echo "Fetching subnet CIRDs"
SUBNET_CIDRS=$(aws ec2 describe-subnets \
    --query "Subnets[*].{Name:Tags[?Key=='Name']|[0].Value,Cidr:CidrBlock}" \
    --output text | awk '{printf $1":"$2"\n"}')
    
RESULT_SUBNETS=$(for i in $SUBNET_IDS; do echo "\"${i##*-}\" : \"${i%%:*}\","; done)
RESULT_SUBNET_CIDRS=$(for i in $SUBNET_CIDRS; do echo "\"${i##*-}Cidr\" : \"${i%%:*}\","; done)

echo "List hosted zones since this usally beaks the build"
aws route53 list-hosted-zones \
	--query 'HostedZones[*].[Id,Config.PrivateZone]' \
	--output text

HOSTED_ZONE_ID=$(aws route53 list-hosted-zones \
            --query "HostedZones[*].[Id,Config.PrivateZone,Name]" \
     --output text | grep "False" | awk '{printf $1}')

HOSTED_ZONE_NAME=$(aws route53 list-hosted-zones \
            --query "HostedZones[*].[Name,Config.PrivateZone,Name]" \
     --output text | grep "False" | awk '{printf $1}')


aws ec2 describe-route-tables --query 'RouteTables[].{Name:Tags[?Key=='\''Name'\'']|[0].Value, Id:RouteTableId}' --output text
aws ec2 describe-route-tables --query 'RouteTables[].{Name:Tags[?Key=='\''Name'\'']|[0].Value, Id:RouteTableId}' --output text | grep -vi None
aws ec2 describe-route-tables --query 'RouteTables[].{Name:Tags[?Key=='\''Name'\'']|[0].Value, Id:RouteTableId}' --output text | grep -vi None | awk '{printf $1":"$2"\n"}'

ROUTE_TABLES=$(aws ec2 describe-route-tables \
    --query "RouteTables[].{Name:Tags[?Key=='Name']|[0].Value, Id:RouteTableId}" \
    --output text | grep -vi "None" | awk '{printf $1":"$2"\n"}')
    
RESULT_ROUTE_TABLES=$(for i in $ROUTE_TABLES; do echo "\"${i##*-}\" : \"${i%%:*}\","; done)


priority="$(aws ssm get-parameter \
         --name "/priority/${ServiceName}" \
         --query 'Parameter.Value' \
         --output text 2>/dev/null || echo '')"

max_priority="$(aws ssm get-parameter \
             --name "/priority/current"  \
             --query 'Parameter.Value' \
             --output text 2>/dev/null || echo '')"

echo "Current priority ${priority}"
echo "Max priority ${max_priority}"

if [[ -z "${max_priority}" ]]; then
  max_priority=1
  aws ssm put-parameter \
    --name "/priority/current" \
    --type "String" \
    --value ${max_priority} \
    --overwrite
fi

if [[ -z "${priority}" ]]; then
  priority=$((max_priority + ( RANDOM % 10 ) + 1))
  aws ssm put-parameter \
    --name "/priority/current" \
    --type "String" \
    --value ${priority} \
    --overwrite

  aws ssm put-parameter \
    --name "/priority/${ServiceName}" \
    --type "String" \
    --value ${priority} \
    --overwrite
fi

echo "Final priority ${priority}"

if [ ! -f "/dist/artifacts.json" ]; then
 echo "{}" > /dist/artifacts.json
fi

target_access=$(cat /dist/artifacts.json | \
 jq '. + {"AWS_ACCESS_KEY_ID" :  "'$AWS_ACCESS_KEY_ID'",
          "AWS_SESSION_TOKEN" : "'$AWS_SESSION_TOKEN'",
          "AWS_SECRET_ACCESS_KEY" : "'$AWS_SECRET_ACCESS_KEY'",
          "AWS_DEFAULT_REGION" : "'${AWS_DEFAULT_REGION}'",
          }')


pipeline_access=$(cat /dist/artifacts.json | \
 jq '. + {"AWS_ACCESS_KEY_ID" :  "'${PIPELINE_AWS_ACCESS_KEY_ID}'",
          "AWS_SESSION_TOKEN" : "'${PIPELINE_AWS_SESSION_TOKEN}'",
          "AWS_SECRET_ACCESS_KEY" : "'${PIPELINE_AWS_SECRET_ACCESS_KEY}'",
          "AWS_DEFAULT_REGION" : "'${AWS_DEFAULT_REGION}'",
	  "AccountId" : "'${PIPELINE_ACCOUNT_ID}'",
          "ServiceName" : "'${ServiceName}'"
          }')


params=$(echo "${target_access}" | \
 jq '. + {"BuildId" : "'${BUILD_ID}'",
          "Version" : "'${BUILD_ID}'",
          "Region" : "'${AWS_DEFAULT_REGION}'",
          "VPCId" : "'${VPC_ID}'",
          "VPCCidr" : "'${VPC_CIDR}'",
          '"${RESULT_SUBNETS}"'
          '"${RESULT_SUBNET_CIDRS}"'
          '"${RESULT_ROUTE_TABLES}"'
	  "AccountId" : "'${TargetAccountId}'",
	  "Priority" : "'${priority}'",
          "PrivateHostedZoneName" : "'${HOSTED_ZONE_NAME%.*}'",
          "PrivateHostedZoneId" : "'${HOSTED_ZONE_ID##*/}'",
          "PublicHostedZoneName" : "'${HOSTED_ZONE_NAME%.*}'",
          "PublicHostedZoneId" : "'${HOSTED_ZONE_ID##*/}'",
          "EnvironmentNameUpper" : "'${EnvironmentNameUpper}'",
          "EnvironmentNameLower" : "'${EnvironmentNameUpper,,}'",
          "DeploymentS3BucketName" : "'${TargetAccountId}-${EnvironmentNameUpper,,}-deployment'",
          "RuntimeImage" : "'${ServiceName}-runtime-image:${BUILD_ID}'",
          "HostedZoneName" : "'${HOSTED_ZONE_NAME}'",
          "ServiceName" : "'${ServiceName}'"
           }')




echo '{ "params" : '${params}', 
        "pipeline_params" : '${pipeline_access}', 
        "resource_tags" : {} }' > $work_dir/group_vars/all.json


echo "Executing ansible deployment"
export ANSIBLE_FORCE_COLOR=true

ansible-playbook \
         -i $work_dir/inventory \
         --extra-vars "BuildId=${BUILD_ID}" \
         -${ANSIBLE_LOG_LEVEL:-vvv} \
         ansible/deploy/deploy.yml

