/*
 * Copyright (c) 2016, 2017 by Delphix. All rights reserved.
 */

currentBuild.result = 'SUCCESS'
error = null
common = null
commit = null

node {
    stage('Checkout')
        checkout([$class: 'GitSCM', changelog: true, poll: true,
                  userRemoteConfigs: [[name: 'origin', url: GIT_URL, credentialsId: 'git-ci-key']],
                  branches: [[name: GIT_BRANCH]],
                  extensions: [[$class: 'WipeWorkspace']]])

        common = load("jenkins/jobs/pipelines/common.groovy")
        stash(name: 'walinuxagent', useDefaultExcludes: false)

        commit = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
}

if (common == null)
    error('common pipeline library incorrectly loaded.')

if (commit == null)
    error('commit information incorrectly loaded.')

try {
    stage('pre-push')
        build(job: 'pre-push', quietPeriod: 0, parameters: [
            [$class: 'StringParameterValue', name: 'GIT_URL', value: GIT_URL],
            [$class: 'StringParameterValue', name: 'GIT_BRANCH', value: commit],
        ])

    stage('update-package')
        build(job: 'update-package', quietPeriod: 0, parameters: [
            [$class: 'StringParameterValue', name: 'GIT_URL', value: GIT_URL],
            [$class: 'StringParameterValue', name: 'GIT_BRANCH', value: commit],
        ])
} catch (e) {
    currentBuild.result = 'FAILURE'
    error = e
} finally {
    stage('mail')
        node {
            // We use "Mailer" instead of "emailext" to avoid sending emails when the build succeeds.
            step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: "${EMAIL}", sendToIndividuals: true])
        }

    if (error)
        throw error
}

// vim: tabstop=4 softtabstop=4 shiftwidth=4 expandtab textwidth=112 colorcolumn=120
