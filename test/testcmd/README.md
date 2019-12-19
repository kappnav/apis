## Prerequisites

1. Install a Kubernetes cluster. 
1. Install the [Kubernetes command-line tool](https://kubernetes.io/docs/tasks/tools/install-kubectl/).
1. Install kubernetes Application Navigator (https://github.com/kappnav/README/blob/master/README.md)  


## Run the script

Usage: ./testJobCommand.sh <openapiURL> <userName>
where:
    <openapiURL> specifies the openapi console URL."
                 i.e. http://actdev-actdev.apps.<host-ip>"
    <userName> specifies the login user name."

The script will perform the following steps:

1. Create self-contained test data including application and component config maps and sample application.
1. Inject the command APIs to create jobs from command action.
1. Retrieve job name and job completion time based on jobs created from previous step.
1. Report test success or failure by comparing timestamp from returned job's completion time
1. Cleanup and exit

