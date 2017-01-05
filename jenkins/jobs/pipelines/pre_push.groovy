/*
 * Copyright (c) 2016, 2017 by Delphix. All rights reserved.
 */

currentBuild.result = 'SUCCESS'
error = null
common = null

node {
    stage('Checkout') {
        checkout([$class: 'GitSCM', changelog: false, poll: false,
                  userRemoteConfigs: [[name: 'origin', url: GIT_URL, credentialsId: 'git-ci-key']],
                  branches: [[name: GIT_BRANCH]],
                  extensions: [[$class: 'WipeWorkspace']]])

        common = load('jenkins/jobs/pipelines/common.groovy')

        /*
         * Some of the unit tests require the .git directory to be present, so we need to ensure we include this
         * directory when stashing these files.
         */
        stash(name: 'walinuxagent', useDefaultExcludes: false)
    }
}

if (common == null)
    error('common pipeline library incorrectly loaded.')

env.DCENTER_GUEST = common.getDCenterGuestName()

try {
    stage('Create VM') {
        common.createDCenterGuest(env.DCENTER_GUEST, env.DCENTER_HOST, env.DCENTER_IMAGE)
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

                stage('Dependencies') {
                    unstash(name: 'walinuxagent')
                    sh('sudo pip install discover unittest2')
                }

                stage('Tests') {
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
            }
        }
    }
} catch (e) {
    currentBuild.result = 'FAILURE'
    error = e
} finally {
    common.sendResults(EMAIL)

    /*
     * On failure, it might be useful to have the VM around for debugging purposes (e.g. inspecting the state of
     * the system after the unit tests run, since some of them modify the system's state), which is why we only
     * unregister the VM when this job fails.
     */
    if (currentBuild.result == 'FAILURE')
        common.unregisterDCenterGuest(env.DCENTER_GUEST, env.DCENTER_HOST)
    else
        common.destroyDCenterGuest(env.DCENTER_GUEST, env.DCENTER_HOST)

    if (error)
        throw error
}

// vim: tabstop=4 softtabstop=4 shiftwidth=4 expandtab textwidth=112 colorcolumn=120
