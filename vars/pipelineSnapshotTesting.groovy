import java.text.SimpleDateFormat

void call(Map config=[:]) {
    Boolean failed = false

    int pipelineTimeout = 17
    String snapshotTestingBranch = "main"
    String nodeLabel = "snapshot-testing"
    String networkName = env.NET_NAME
    String configPath = ""
    Boolean shouldSkipNotificationMessage = false
    String extraLogLines = ""

    if (config.containsKey('networkName')) {
        networkName = config.networkName
    }

    if (config.containsKey('configPath')) {
        configPath = config.configPath
    } else if (params.containsKey('CONFIG_PATH')) {
        configPath = params.CONFIG_PATH
    }

    if (config.containsKey('timeout')) {
        pipelineTimeout = config.timeout.toInteger()
    } else if (params.containsKey('TIMEOUT')) {
        pipelineTimeout = params.TIMEOUT.toInteger()
    }

    if (config.containsKey('nodeLabel')) {
        nodeLabel = config.nodeLabel
    } else if (params.containsKey('NODE_LABEL')) {
        nodeLabel = params.NODE_LABEL
    }

    if (config.containsKey('snapshotTestingBranch')) {
        snapshotTestingBranch = config.snapshotTestingBranch
    } else if (params.containsKey('SNAPSHOT_TESTING_BRANCH')) {
        snapshotTestingBranch = params.SNAPSHOT_TESTING_BRANCH
    }



    node(nodeLabel) {
        stage('init') {
            skipDefaultCheckout()
            cleanWs()

            script {
                // initial cleanup
                agentUtils.commonCleanup()
                // init global variables
                // monitoringDashboardURL = jenkinsutils.getMonitoringDashboardURL([job: "snapshot-${networkName}"])
                jenkinsAgentIP = agent.getPublicIP()
                // echo "Jenkins Agent IP: ${jenkinsAgentIP}"
                // echo "Monitoring Dahsboard: ${monitoringDashboardURL}"
                // set job Title and Description
                String prefixDescription = jenkinsUtils.getNicePrefixForJobDescription()
                currentBuild.displayName = "#${currentBuild.id} ${prefixDescription} [${env.NODE_NAME.take(12)}]"
                // currentBuild.description = "Monitoring: ${monitoringDashboardURL}, Jenkins Agent IP: ${jenkinsAgentIP} [${env.NODE_NAME}]"
                // Setup grafana-agent
                // grafanaAgent.configure("snapshot", [
                //     JENKINS_JOB_NAME: "snapshot-${networkName}",
                // ])
                // grafanaAgent.restart()
            }
        }

        stage('INFO') {
            // Print Info only, do not execute anythig
            echo "Jenkins Agent IP: ${jenkinsAgentIP}"
            echo "Jenkins Agent name: ${env.NODE_NAME}"
            // echo "Monitoring Dahsboard: ${monitoringDashboardURL}"
            echo "Core stats: http://${jenkinsAgentIP}:3003/statistics"
            echo "GraphQL: http://${jenkinsAgentIP}:3008/graphql/"
            echo "Epoch: http://${jenkinsAgentIP}:3008/api/v2/epoch"
            echo "Data-Node stats: http://${jenkinsAgentIP}:3008/statistics"
            echo "PARAMS"
            echo "=================================="
            echo "NODE_LABEL: " + nodeLabel
            echo "SNAPSHOT_TESTING_BRANCH: " + snapshotTestingBranch
            echo "TIMEOUT: " + pipelineTimeout
            echo "NODE_LABEL: " + nodeLabel
        }


        // Give extra 5 minutes for setup
        timeout(time: pipelineTimeout + 10, unit: 'MINUTES') {
            stage('Clone snapshot-testing') {
                gitClone([
                    url: 'git@github.com:nebula-dex/snapshot-testing.git',
                    branch: snapshotTestingBranch,
                    credentialsId: 'nebula-dex-github-ssh',
                    directory: 'snapshot-testing'
                ])
            }

            stage('build snapshot-testing binary') {
                dir('snapshot-testing') {
                    sh 'mkdir ../dist && go build -o ../dist ./...'
                }
            }

            stage('Run tests') {
                List<String> snapshotTestingArgs = [
                    '--duration ' + (pipelineTimeout*60) + 's',
                    ' --environment ' + networkName,
                    '--work-dir ./work-dir'
                ]
                if (configPath != "") {
                    snapshotTestingArgs << '--config-path ' + configPath
                }

                try {
                    sh './dist/snapshot-testing run ' + snapshotTestingArgs.join(' ')
                } catch (e) {
                    failed = true
                    print('FAILURE: ' + e)
                }
            }

            stage('Process results') {
                currentBuild.result = 'SUCCESS'
                reason = "Unknown failure"
                String catchupDuration = "N/A"

                Map results = [:]
                if (fileExists('./work-dir/results.json')) {
                    results = readJSON file: './work-dir/results.json'
                }
                println(results)

                if (failed == true) {
                    currentBuild.result = 'FAILURE'
                } else {
                    try {
                        switch (results["status"] ?: 'UNKNOWN') {
                            case 'HEALTHY':
                                currentBuild.result = 'SUCCESS'
                                reason = ""
                                break
                            case 'MAYBE':
                                currentBuild.result = 'UNSTABLE'
                                reason = results["reason"] ?: "Unknown reason"
                                break
                            default:
                                currentBuild.result = 'FAILURE'
                                reason = results["reason"] ?: "Unknown reason"
                                break
                        }
                        catchupDuration = results["catchup-duration"] ?: "N/A"
                        extraLogLines = results["visor-extra-log-lines"] ?: ""
                        
                        String snapshotsFrom = results["snapshot-min"] ?: 'UNKNOWN'
                        String snapshotsTo = results["snapshot-max"] ?: 'UNKNOWN'
                        int buildNo = currentBuild.number as Integer

                        // archiveArtifactsToS3(buildNo, networkName, './work-dir', snapshotsFrom, snapshotsTo)
                    } catch(e) {
                        print(e)
                        currentBuild.result = 'FAILURE'
                    }
                }
                
                // We have conditions (e.g: when the devnet1 network is dead) to not report it
                shouldSkipNotificationMessage = (results["should-skip-failure"] as Boolean) ?: false
            }
        }

        stage('Node logs') {
            print('Logs have been moved to the artifacts.')
            print('See the following directory for logs:')
            print(env.BUILD_URL + 'artifact/work-dir/logs/')
        }

        stage('Archive artifacts') {
            sh 'ls -als ./work-dir'
            archiveArtifacts(
                artifacts: 'work-dir/**/*',
                allowEmptyArchive: true,
                excludes: [
                    'work-dir/bins/*',
                    'work-dir/**/*.sock',
                ].join(','),
            )
        }

        stage('Send notifications') {
            script {
                if (currentBuild.currentResult.toLowerCase() == 'success' || shouldSkipNotificationMessage) {
                    println("Not need to send notification")
                    return
                }

                String result = currentBuild.currentResult.toLowerCase()

                String description = "No extra logs has been reported. See the pipeline logs for details"
                if (extraLogLines.length() > 0) {
                    description = '''Node potentially failed with the following error:
```
''' + extraLogLines + '''
```
                    '''
                }

                description += '\n\n[Vegavisor node logs](' + env.BUILD_URL + 'artifact/work-dir/logs/)'
                description += '\n[Pipeline logs](' + env.BUILD_URL + 'console)'

                withCredentials([string(credentialsId: 'nebula-discord-webhook-url', variable: 'DISCORD_WEBHOOK_URL')]) {
                    discordSend webhookURL: env.DISCORD_WEBHOOK_URL,
                                title: 'Snapshot testing(' + env.NET_NAME + ') #' + env.BUILD_NUMBER,
                                result: currentBuild.currentResult,
                                link: env.BUILD_URL,
                                description: description + "\n\u2060", // word joiner character forces a blank line
                                enableArtifactsList: false,
                                showChangeset: false
                }
            }
        }

        stage('Cleanup') {
            cleanWs()
            script {
                agentUtils.commonCleanup()
            }
        }
    }
}
