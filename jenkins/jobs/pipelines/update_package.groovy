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
                  extensions: [
                      [$class: 'RelativeTargetDirectory', relativeTargetDir: GIT_DIRECTORY],
                      [$class: 'WipeWorkspace']]])

        common = load("${GIT_DIRECTORY}/jenkins/jobs/pipelines/common.groovy")
        stash(name: 'walinuxagent', include: "${GIT_DIRECTORY}/**", useDefaultExcludes: false)
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
                    checkout([$class: 'GitSCM', changelog: true, poll: false,
                              userRemoteConfigs: [[name: 'origin', url: PKG_BUILD_URL, credentialsId: 'git-ci-key',
                                  refspec: '+refs/tags/*:refs/remotes/origin/tags/*']],
                              branches: [[name: PKG_BUILD_BRANCH]],
                              extensions: [
                                  [$class: 'RelativeTargetDirectory', relativeTargetDir: PKG_BUILD_DIRECTORY],
                                  [$class: 'WipeWorkspace']]])

                    unstash(name: 'walinuxagent')
                }

                stage('Check TAG') {
                    dir("${GIT_DIRECTORY}") {
                        describe = sh(script: 'git describe --tags', returnStdout: true).trim().tokenize('-')
                        tag      = describe[0]
                        ncommits = describe[1]

                        /*
                         * The tags are expected to be of the form "v2.2.0". Python packages cannot have a version
                         * that starts with an alphabetic characters, so we strip the leading "v" from the tag to
                         * obtain the version we will use when building the source distribution and package.
                         */
                        if (!tag.startsWith('v') || tag.tokenize('.').size() != 3)
                            error("The git tag is not in the expected format: '${tag}'")
                        tag = tag.substring(1, tag.length())
                    }
                }

                stage('Update SDIST') {
                    dir("${GIT_DIRECTORY}") {
                        /*
                         * Since we're building new packages for every commit, if the version of each SDIST that
                         * we generated were all identical (e.g. if we just used the version of the agent
                         * hardcoded in the repository), it would be difficult to know exactly which version of
                         * repository was contained in any given SDIST.
                         *
                         * Thus, we used git-describe above, to determine the number of commits past the most
                         * recent git-tag for the version of the repository that we're building. Using this
                         * information, we dynamically change the version to include this information. This will
                         * cause each SDIST (and IPS package) generated to each have a unique version (assuming
                         * this automation is only run for a single git-branch of the repository).
                         */
                        sh("sed -i \"s/^AGENT_VERSION = '.*'/AGENT_VERSION = '${tag}.${ncommits}'/\" " +
                           "azurelinuxagent/common/version.py")

                        name = sh(script: 'python setup.py --name', returnStdout: true).trim()
                        version = sh(script: 'python setup.py --version', returnStdout: true).trim()

                        env.SDIST_DIRECTORY = "${env.SDIST_MIRROR_DIRECTORY}/${name}"
                        env.SDIST_FILENAME = "${name}-${version}.tar.gz"

                        sh('python setup.py sdist --dist-dir dist --formats gztar')
                        sh("test -f dist/${env.SDIST_FILENAME}")

                        if (env.UPDATE_SDIST_MIRROR == 'yes') {
                            sh("mkdir -p ${env.SDIST_DIRECTORY}")

                            /*
                             * We copy to a temporary file and then move the temporary file to the final location
                             * to avoid corrupting any previous copy of the dist on the mirror. Since the copy is
                             * not an atomic operation, it's possible for it to get interrupted prior to it
                             * completing, which would leave the dist in an undefined state. Once the temporary
                             * file is successfully copied, we can atomically move this to it's final location
                             * since this will dissolve to a "rename" syscall (which is atomic).
                             */
                            sh("cp dist/${env.SDIST_FILENAME} ${env.SDIST_DIRECTORY}/${env.SDIST_FILENAME}.tmp")
                            sh("mv ${env.SDIST_DIRECTORY}/${env.SDIST_FILENAME}.tmp " +
                               "${env.SDIST_DIRECTORY}/${env.SDIST_FILENAME}")
                        }
                    }
                }

                /*
                 * The SDIST is a requirement in order to build the IPS package. Thus, if we don't update the
                 * mirror with the SDIST we just built for this specific version, it won't be aviable for
                 * download by the package build logic below. In this case, it doesn't make sense to attempt to
                 * build the package, knowing that it will fail, so we simply stop here and return.
                 */
                if (env.UPDATE_SDIST_MIRROR != 'yes')
                    return

                stage('Update PKG') {
                    dir("${PKG_BUILD_DIRECTORY}/build/walinuxagent") {
                        /*
                         * In order to create a package that contains the code that we generated above, we need
                         * to dynamically modify the version used to to create the package to match the version
                         * of the SDIST. Without this change, we would simply rebuild (and possibly fail to do
                         * so) the package based on whatever version was already contained in the build.sh file.
                         */
                        sh("sed -i 's/^VER=.*/VER=${version}/' build.sh")
                    }

                    dir("${PKG_BUILD_DIRECTORY}/build") {
                        /*
                         * The build system needs an IPS repository to upload the package to after it is
                         * successfully built; thus we create such a repository prior to executing the
                         * build.
                         */
                        sh('test ! -e packages')
                        sh('pkgrepo create packages')
                        sh('pkgrepo add-publisher -s packages delphix.com')

                        /*
                         * Execute the build; this will upload the package into the specified IPS repository
                         * upon a successful build.
                         */
                        sh('./buildctl -r $(pwd)/packages -b build walinuxagent')

                        if (env.UPDATE_PKG_REPOSITORY == 'yes') {
                            sh("sudo -u delphix pkgrecv -s packages -d ${env.PKG_REPOSITORY_DIRECTORY} walinuxagent")
                            sh("sudo -u delphix pkgrepo -s ${env.PKG_REPOSITORY_DIRECTORY} refresh")
                        }
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
