/*
 * Copyright (c) 2016 by Delphix. All rights reserved.
 */

pipelineJob('update-package') {
    /*
     * We can't allow concurrent builds of this job since it manipulates infrastructure that is not unique to
     * each build. Specifically, a source distribution ("sdist") is generated of the WALinuxAgent repository, and
     * this is then uploaded to our internal mirror, which is a globally shared resource. Additionally, an IPS
     * package is generated and uploaded to our internal IPS package repository, which is also a globally shared
     * resource. We want to prevent concurrent builds to try and ensure we don't incorrectly modify these resources.
     */
    concurrentBuild(false)

    parameters {
        stringParam('GIT_URL', 'https://gitlab.delphix.com/os-platform/WALinuxAgent.git',
                    'The Git repository to use for the build.')
        stringParam('GIT_BRANCH', 'projects/hyperv',
                    'The Git branch to use for the build.')
        stringParam('GIT_DIRECTORY', 'WALinuxAgent',
                    'The directory to use to checkout the Git repository.')

        stringParam('EMAIL', '', 'The email to use for build status notifications.')

        stringParam('PKG_BUILD_URL', 'https://gitlab.delphix.com/os-platform/pkg-build-gate.git',
                    'The URL for the pkg-build-gate repository to use for this build.')
        stringParam('PKG_BUILD_BRANCH', 'master',
                    'The branch of the pkg-build-gate repository to use for this build.')
        stringParam('PKG_BUILD_DIRECTORY', 'pkg-build-gate',
                    'The directory to use when checking out the pkg-build-gate repository.')

    }

    environmentVariables {
        env('DCENTER_IMAGE', 'dlpx-trunk')
        env('DCENTER_HOST', 'dcenter')

        env('SDIST_MIRROR_DIRECTORY', '/net/pharos/export/third-party/mirror')
        env('PKG_REPOSITORY_DIRECTORY', '/net/pharos/export/src/dlpx-pkg-gate')

        if (System.getenv('JENKINS_DEVELOPER') == null) {
            env('UPDATE_SDIST_MIRROR', 'yes')
            env('UPDATE_PKG_REPOSITORY', 'yes')
        } else {
            env('UPDATE_SDIST_MIRROR', 'no')
            env('UPDATE_PKG_REPOSITORY', 'no')
        }
    }

    definition {
        cps {
            script(readFileFromWorkspace('jenkins/jobs/pipelines/update_package.groovy'))
            sandbox()
        }
    }
}

// vim: tabstop=4 softtabstop=4 shiftwidth=4 expandtab textwidth=112 colorcolumn=120
