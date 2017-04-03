/*
 * Copyright (c) 2016, 2017 by Delphix. All rights reserved.
 */

currentBuild.result = 'SUCCESS'
error = null
commit = null

node {
    stage('Checkout')
        checkout([$class: 'GitSCM', changelog: true, poll: true,
                  userRemoteConfigs: [[name: 'origin', url: env.GIT_URL, credentialsId: 'git-ci-key']],
                  branches: [[name: env.GIT_BRANCH]],
                  extensions: [[$class: 'WipeWorkspace']]])
        commit = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
}

if (commit == null)
    error('commit information incorrectly loaded.')

try {
    stage('pre-push')
        build(job: 'pre-push', quietPeriod: 0, parameters: [
            [$class: 'StringParameterValue', name: 'GIT_URL', value: env.GIT_URL],
            [$class: 'StringParameterValue', name: 'GIT_BRANCH', value: commit],
        ])

    stage('update-package')
        build(job: 'update-package', quietPeriod: 0, parameters: [
            [$class: 'StringParameterValue', name: 'GIT_URL', value: env.GIT_URL],
            [$class: 'StringParameterValue', name: 'GIT_BRANCH', value: commit],
        ])
} catch (e) {
    currentBuild.result = 'FAILURE'
    error = e
} finally {
    stage('mail')
        node {
            // We use "Mailer" instead of "emailext" to avoid sending emails when the build succeeds.
            step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: env.EMAIL, sendToIndividuals: true])
        }

    if (error)
        throw error
}

// vim: tabstop=4 softtabstop=4 shiftwidth=4 expandtab textwidth=112 colorcolumn=120
