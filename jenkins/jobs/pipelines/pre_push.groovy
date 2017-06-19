/*
 * Copyright (c) 2016, 2017 by Delphix. All rights reserved.
 */

currentBuild.result = 'SUCCESS'
error = null

try {
    stage('Create VM')
        def create = build(job: 'devops-gate/master/create-dc-slave', parameters: [
            [$class: 'StringParameterValue', name: 'DEVOPS_REPO', value: env.DEVOPS_REPO],
            [$class: 'StringParameterValue', name: 'DEVOPS_BRANCH', value: env.DEVOPS_BRANCH],
            [$class: 'StringParameterValue', name: 'DCENTER_HOST', value: env.DCENTER_HOST],
            [$class: 'StringParameterValue', name: 'DCENTER_ROLES', value: env.DCENTER_ROLES],
            [$class: 'StringParameterValue', name: 'DCENTER_IMAGE', value: env.DCENTER_IMAGE],
            [$class: 'StringParameterValue', name: 'SLAVE_ROLES', value: env.SLAVE_ROLES],
            [$class: 'StringParameterValue', name: 'JENKINS_MASTER', value: env.JENKINS_URL]
        ])

        env.DCENTER_GUEST = create.rawBuild.environment.GUEST_NAME

    node(env.DCENTER_GUEST) {
        stage('Checkout')
            checkout([$class: 'GitSCM', changelog: false, poll: false,
                      userRemoteConfigs: [[name: 'origin', url: GIT_URL, credentialsId: 'git-ci-key']],
                      branches: [[name: GIT_BRANCH]],
                      extensions: [[$class: 'WipeWorkspace']]])

        stage('Dependencies')
            sh('sudo pip install discover unittest2')

        stage('Tests')
            parallel('Unit Tests' : {
                /*
                 * Some of the DelphixOSUtil tests must be run as root, or else they'll be skipped. As a
                 * result, we have to run the tests using "sudo" to avoid skipping these tests.
                 */
                sh('sudo python -m discover -s tests -v')
            }, 'SMF Validation': {
                sh('svccfg validate init/delphix/waagent.xml')
            })
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

    if (env.DCENTER_GUEST) {
        build(job: 'devops-gate/master/destroy-dc-guest', parameters: [
            [$class: 'StringParameterValue', name: 'DEVOPS_REPO', value: env.DEVOPS_REPO],
            [$class: 'StringParameterValue', name: 'DEVOPS_BRANCH', value: env.DEVOPS_BRANCH],
            [$class: 'StringParameterValue', name: 'DCENTER_HOST', value: env.DCENTER_HOST],
            [$class: 'StringParameterValue', name: 'GUEST_NAME', value: env.DCENTER_GUEST]
        ])
    }

    if (error)
        throw error
}

// vim: tabstop=4 softtabstop=4 shiftwidth=4 expandtab textwidth=112 colorcolumn=120
