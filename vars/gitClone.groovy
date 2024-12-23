/* groovylint-disable DuplicateStringLiteral */
/* groovylint-disable DuplicateNumberLiteral */

void doClone(Map config) {
  retry(3) {
    timeout(time: config.timeout, unit: 'MINUTES') {
      return checkout([
        $class: 'GitSCM',
        branches: [[name: config?.branch]],
        extensions: config?.extensions,
        userRemoteConfigs: [[
          url: config?.url,
          credentialsId: config?.credentialsId
      ]]])
    }
  }
}

void call(Map additionalConfig) {
  Map defaultCconfig = [
      directory: '',
      branch: 'main',
      vegaUrl: '',
      githubUrl: '',
      url: '',
      credentialsId: 'vega-ci-bot',
      timeout: 3,
  ]

  Map config = defaultCconfig + additionalConfig

  if (config.vegaUrl && !config.url) {
    config.url = "git@github.com:vegaprotocol/${config.vegaUrl}.git"
  }
  if (config.githubUrl && !config.url) {
    config.url = "git@github.com:${config.githubUrl}.git"
  }

  ['branch', 'url', 'credentialsId'].each { item ->
    if (config[item]?.length() < 1) {
      error('[gitClone] Field config.' + item + ' cannot be empty')
    }
  }

  if (config.directory == '') {
    return doClone(config)
  }

  dir(config.directory) {
    return doClone(config)
  }
}

/**
 * Example usage
 */
// gitClone([
//   credentialsId: 'vega-ci-bot',
//   url: 'git@github.com:vegaprotocol/vegacapsule.git',
//   branch: 'main'
// ])

// gitClone([
//   credentialsId: 'vega-ci-bot',
//   url: 'git@github.com:vegaprotocol/vegacapsule.git',
//   branch: 'main',
//   directory: 'abc'
// ])

// gitClone([
//   credentialsId: 'vega-ci-bot',
//   url: 'git@github.com:vegaprotocol/vegacapsule.git',
//   branch: 'main',
//   directory: 'def'
// ])