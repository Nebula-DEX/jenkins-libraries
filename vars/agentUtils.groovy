
void localKillRunningContainers() {
  sh (
    label: 'remove any running docker containers',
    script: 'if [ ! -z "$(docker ps -q)" ]; then docker kill $(docker ps -q) || echo "echo failed to kill"; fi'
  )
}

void localDockerCleanup() {
  sh label: 'docker volume prune',
  returnStatus: true,  // ignore exit code
  script: '''#!/bin/bash -e
      docker volume prune --force
  '''
}

// Remove all binaries found in PATH
void localRemoveBinary(String binName) {
  print("Removing all existing binaries of " + binName)
  while (true) {
    try {
      binaryPath = shellOutput("which " + binName)
      if (binaryPath == "") {
        return
      } else {
        sh 'sudo rm -f ' + binaryPath
      }
    } catch(e) {
      return
    }
  }
}

/**
 * We have a long live runners, in case there may be some trash left on the boxt after
 * previous runs, we should clean it up.
 **/
def commonCleanup() {
  localKillRunningContainers()
  localDockerCleanup()

  // Each pipeline MUST build the following binaries in the pipeline
  localRemoveBinary('vega')
  localRemoveBinary('data-node')
  localRemoveBinary('vegavisor')
  localRemoveBinary('vegawallet')
  localRemoveBinary('visor')
  localRemoveBinary('vegatools')
  localRemoveBinary('vegacapsule')
  localRemoveBinary('blockexplorer')
}

