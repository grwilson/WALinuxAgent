/*
 * Copyright (c) 2016 by Delphix. All rights reserved.
 */

currentBuild.result = 'SUCCESS'
error = null
common = null

node {
    stage('Checkout') {
        checkout([$class: 'GitSCM', changelog: true, poll: true,
                  userRemoteConfigs: [[name: 'origin', url: GIT_URL, credentialsId: 'git-ci-key']],
                  branches: [[name: GIT_BRANCH]],
                  extensions: [[$class: 'WipeWorkspace']]])

        common = load('jenkins/jobs/pipelines/common.groovy')
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

                name = null
                version = null

                stage('Dependencies') {
                    unstash(name: 'walinuxagent')
                    name = sh(script: 'python setup.py --name', returnStdout: true).trim()
                    version = sh(script: 'python setup.py --version', returnStdout: true).trim()
                }

                if (name == null || version == null)
                    error('name or version incorrectly loaded.')

                stage('Update Dist') {
                    env.DIST_DIRECTORY = "${env.DIST_MIRROR_DIRECTORY}/${name}"
                    env.DIST_FILENAME = "${name}-${version}.tar.gz"

                    sh('python setup.py sdist --dist-dir dist --formats gztar')
                    sh("test -f dist/${env.DIST_FILENAME}")

                    if (env.UPDATE_DIST_MIRROR == 'yes') {
                        sh("mkdir -p ${env.DIST_DIRECTORY}")

                        /*
                         * We copy to a temporary file and then move the temporary file to the final location
                         * to avoid corrupting any previous copy of the dist on the mirror. Since the copy is
                         * not an atomic operation, it's possible for it to get interrupted prior to it
                         * completing, which would leave the dist in an undefined state. Once the temporary
                         * file is successfully copied, we can atomically move this to it's final location
                         * since this will dissolve to a "rename" syscall (which is atomic).
                         */
                        sh("cp dist/${env.DIST_FILENAME} ${env.DIST_DIRECTORY}/${env.DIST_FILENAME}.tmp")
                        sh("mv ${env.DIST_DIRECTORY}/${env.DIST_FILENAME}.tmp " +
                           "${env.DIST_DIRECTORY}/${env.DIST_FILENAME}")
                    }
                }
            }
        }
    }
} catch (e) {
    currentBuild.result = 'FAILURE'
    error = e
} finally {
    common.sendResults(EMAIL)
    common.destroyDCenterGuest(env.DCENTER_GUEST, env.DCENTER_HOST)

    if (error)
        throw error
}

// vim: tabstop=4 softtabstop=4 shiftwidth=4 expandtab textwidth=112 colorcolumn=120
