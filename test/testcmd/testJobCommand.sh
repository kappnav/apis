#!/bin/bash

#####################################################################################
#
#  The script tests the command action API for job completion
#
#  Usage: testJobCommand.sh <openapiURL> <userName>
#
#  The script will perform the following steps:
#  
#  1. Create self-contained test data 
#  2. Retrieve job name and job completion time based on jobs created from step #1
#  3. Report test success or failure by comparing timestamp from returned job's completion time
#  4. Cleanup 
#
#####################################################################################

## Get positional parameters
openapiURL=$1
userName=$2

if [ x$openapiURL = 'x' ] || [ x$openapiURL = x'?' ] || [ x$openapiURL = x'-?' ] || [ x$userName = 'x' ] || [ x${userName:0:1} = 'x-' ]; then
    echo "syntax: testJobCommand.sh <openapiURL> <userName>"
    echo ""
    echo "where: "
    echo "   <openapiURL> specifies the openapi console URL."
    echo "   i.e. http://actdev-actdev.apps.<host-ip>"
    echo ""
    echo "   <userName> specifies the login user name."
    echo
    exit 0
fi


## Step 1: Create self-contained testcmd data 
# create namespace, application and component config maps and sample testcmd application
kubectl create namespace testcmd

# create application and component config maps and testcmd app
kubectl apply -f . -n testcmd

# inject POST API to create command action job from testcmd application
job1=$(curl -X POST "$openapiURL/kappnav/resource/testcmd/execute/command/testcmdapp?namespace=testcmd" -H "accept: */*" -H "Cookie: kappnav-user=$userName" -H "Content-Type: application/json" -d "{}")

#echo "Debug: job is created: $job1"

# inject POST API to create commmand action job from testcmd component and application
job2=$(curl -X POST "$openapiURL/kappnav/resource/testcmd/kappnav.actions.application.testcmd/ConfigMap/execute/command/testcmdcomp?application-namespace=testcmd&namespace=testcmd" -H "accept: */*" -H "Cookie: kappnav-user=$userName" -H "Content-Type: application/json" -d "{}")

#echo "Debug: job is created: $job2"


## Step 2: Retrieve job name and completion time from "kubectl get jobs" command
jobOutput=$(kubectl get jobs -n testcmd -o=jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.completionTime}{"\n"}{end}')

# create an array to report test failure
declare -a failedResult=()

# Split job output with new line and to array
echo "$jobOutput" | while read each
do
  array=($each)

  # retrieve job name and completionTime 
  jobName=${array[0]}
  completionTime=${array[1]}
  echo ""
  #echo "Debug: job name: $jobName"
  echo ""

  # Use curl to inject action API to get jobs based on completion time retrieving from kubectl get jobs command
  timestamps=$(curl -X GET "$openapiURL/kappnav/resource/commands?user=$userName&time=$completionTime" -H  "accept: */*" | grep -o '"completionTime":"[^"]*' | cut -d'"' -f4) 


  ## Step 3: Report test success/failure by comparing timestamp from returned job with specified completion time
  # Success: Jobs with completion time newer than specified timestamp are returned
  # Failure: Jobs with completion time older than specified timestamp are returned
  for time in $timestamps
  do 
      # compare two timestamp and test failed when specified completion timestamp newer than returned job timestamp
      #echo "Debug: timestamp specified:      $completionTime"
      #echo "Debug: timestamp job completed:  $time"
      echo ""

      if [ -n "$completionTime" ] && [ ${completionTime//[-:.TZ]/} -gt ${time//[-:.TZ]/} ]; then
         #echo "Debug: test failed - invalid job name: $jobName with the completion time: $time  was returned"
         failedResult+=('failed')
         break
      fi
  done

done

#echo "Debug: test failure result: $failedResult"

if [ ${#failedResult[@]} -eq 0 ]; then
   echo "TEST SUCCESS !!"
   echo ""
   echo ""
else
   echo "TEST FAILED !!"
   echo ""
   echo ""
fi


## Step 4: Cleanup
echo "Start to clean up ..."
echo ""
echo ""

# delete config maps
kubectl delete -f . -n testcmd

# delete jobs
echo "$jobOutput" | while read each
do
  array=($each)
  jobName=${array[0]}
  #echo "Debug: job name: $jobName is deleted"
  job=$(kubectl get job $jobName)
  if [ -n "$job" ]; then 
     kubectl delete job $jobName 
  fi
done

# delete namespace
kubectl delete namespace --namespace=testcmd 


echo "TEST DONE ..."

