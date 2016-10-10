/*
 * Copyright (c) 2016 by Delphix. All rights reserved.
 */

import org.apache.commons.lang.RandomStringUtils

env.DCENTER_GUEST = 'walinuxagent-pre-push-' + RandomStringUtils.randomAlphanumeric(8)

currentBuild.result = 'SUCCESS'
error = null

try {
    stage('Create VM') {
        node(env.DCENTER_HOST) {
            sh("dc clone-latest ${env.DCENTER_IMAGE} ${env.DCENTER_GUEST}")

            /*
             * We need to wait for the VM that we create is ready to process incoming SSH connections, or else we
             * can wind up in situation where we attempt to connect to the VM and attach it as a Jenkins slave,
             * prior to the VM being ready; this would result in the job failing.
             *
             * By relying on "dc guest wait" and "dc guest run", we're encoding the assumption that the VM we just
             * created has VMware's guest tools installed into the guest's operating system. Without these tools,
             * we cannot use "dc guest". Additionally, by relying on the "svcadm" command, we encode the assumption
             * that the VM we created is actually a "dlpx-trunk" VM. It's a shame to encode these implicit
             * dependencies here, but there isn't any clearly better solution that would work across all guest
             * operating systems; and since we're currently only using "dlpx-trunk" VMs, this should be OK.
             */
            sh("dc guest wait ${env.DCENTER_GUEST}")
            sh("dc guest run ${env.DCENTER_GUEST} 'svcadm enable -s svc:/network/ssh:default'")
        }
    }

    /*
     * We can only use "withCredentials" if we're in a "node" context, so we allocate a node to satisfy that
     * constraint, but don't have to worry about which node we're using.
     */
    node {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'blackbox',
                          usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {

            node("ssh-direct:" +
                 "${env.DCENTER_GUEST}.${env.DCENTER_HOST}:" +
                 "${USERNAME}:${PASSWORD}:" +
                 "/opt/jdk/bin/java:/var/tmp/jenkins:sudo -u delphix sh -c ':'") {

                stage('Checkout') {
                    checkout([$class: 'GitSCM', changelog: false, poll: false,
                              userRemoteConfigs: [[name: 'origin', url: GIT_URL, credentialsId: 'git-ci-key']],
                              branches: [[name: GIT_BRANCH]],
                              extensions: [[$class: 'WipeWorkspace']]])
                }

                stage('Dependencies') {
                    sh('sudo pip install discover unittest2')
                }

                stage('Tests') {
                    sh('python -m discover -s tests -v')
                }
            }
        }
    }
} catch (e) {
    currentBuild.result = 'FAILURE'
    error = e
} finally {
    /*
     * We can only use "emailext" if we're in a "node" context, so we allocate a node to satisfy that
     * constraint, but we don't have to worry about which node we're using.
     */
    node {
        emailext(to: EMAIL, body: "Please visit ${env.BUILD_URL} to inspect results.",
                 subject: "Job ${env.JOB_NAME} #${env.BUILD_NUMBER} completed with status: ${currentBuild.result}")
    }

    stage('Destroy VM') {
        node(env.DCENTER_HOST) {
            sh("dc destroy ${env.DCENTER_GUEST}")
        }
    }

    if (error)
        throw error
}

// vim: tabstop=4 softtabstop=4 shiftwidth=4 expandtab textwidth=112 colorcolumn=120
