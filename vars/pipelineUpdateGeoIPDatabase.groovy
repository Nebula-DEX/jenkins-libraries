String servers = params.SERVERS

pipeline {
    agent any
    
    stages {
        stage('Cleanup') {
            steps {
                cleanWs()
                script {
                    agentUtils.commonCleanup()
                }
            }
        }
        
        stage('Download maxmind country db') {
            steps {
                
                withCredentials([string(credentialsId: 'maxmind-api-basic-auth', variable: 'MAXMIND_API_BASIC_AUTH')]) {
                    sh '''curl -L \
                        -u "''' + MAXMIND_API_BASIC_AUTH + '''" \
                        --output ./GeoLite2-Country.tar.gz \
                        https://download.maxmind.com/geoip/databases/GeoLite2-Country/download?suffix=tar.gz
                    '''
                }
                sh 'mkdir geo-ip-db'
                sh 'tar -xzvf ./GeoLite2-Country.tar.gz --strip-components=1 -C ./geo-ip-db'
                sh 'ls -als ./geo-ip-db/'
            }
        }
        
        stage('Uplaod geo ip db to remote servers') {
            steps {
                script {
                    dir("geo-ip-db") {
                        withCredentials([sshUserPrivateKey(credentialsId: "frontend-deployment-ssh-key", usernameVariable: 'DEPLOYMENT_USER', keyFileVariable: 'DEPLOYMENT_KEY_FILE')]) {
                            servers.split(',').each { srvAddress ->
                                sh '''rsync \
                                    -avz \
                                    --stats \
                                    -e "ssh -o StrictHostKeyChecking=no -i ''' + DEPLOYMENT_KEY_FILE + '''" \
                                    --rsync-path="sudo rsync" \
                                    GeoLite2-Country.mmdb \
                                    ''' + DEPLOYMENT_USER + '''@''' + srvAddress + ''':/etc/caddy/GeoLite2-Country.mmdb
                                '''
                                
                                sh '''ssh \
                                    -o StrictHostKeyChecking=no -i ''' + DEPLOYMENT_KEY_FILE + ''' \
                                    ''' + DEPLOYMENT_USER + '''@''' + srvAddress + ''' \
                                    'sudo chown caddy:caddy /etc/caddy/GeoLite2-Country.mmdb'
                                '''
                            }
                        }
                    }
                }
            }
        }

        stage('Send notifications') {
            steps {
                script {
                if (currentBuild.currentResult.toLowerCase() == 'success') {
                    println("Not need to send notification")
                    return
                }

                String description = "Pipeline to update the GEO IP database everyday at midnight"
                description += '\n[Pipeline logs](' + env.BUILD_URL + 'console)'
                withCredentials([string(credentialsId: 'nebula-discord-webhook-url', variable: 'DISCORD_WEBHOOK_URL')]) {
                    discordSend webhookURL: env.DISCORD_WEBHOOK_URL,
                                title: 'Geo IP db update #' + env.BUILD_NUMBER,
                                result: currentBuild.currentResult,
                                link: env.BUILD_URL,
                                description: description + "\n\u2060", // word joiner character forces a blank line
                                enableArtifactsList: false,
                                showChangeset: false
                }
            }
        }
    }
}

