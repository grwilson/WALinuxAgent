/*
 * Copyright (c) 2016, 2017 by Delphix. All rights reserved.
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
        stringParam('GIT_BRANCH', 'master',
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
        env('DEVOPS_REPO', 'https://gitlab.delphix.com/devops/devops-gate.git')
        env('DEVOPS_BRANCH', 'master')

        env('DCENTER_IMAGE', 'dlpx-trunk')
        env('DCENTER_HOST', 'dcenter')

        env('DCENTER_ROLES', 'dlpx.dxos-credentials')
        env('SLAVE_ROLES', 'dlpx.initialize-dxos')

        env('SDIST_MIRROR_DIRECTORY', '/net/pharos/export/third-party/mirror')
        env('PKG_REPOSITORY_DIRECTORIES',
            '/net/pharos/export/src/dlpx-pkg-gate /net/pharos/export/src/5.1/dlpx-pkg-5.1-release')

        if (System.getenv('JENKINS_DEVELOPER') == null) {
            env('UPDATE_SDIST_MIRROR', 'yes')
            env('UPDATE_PKG_REPOSITORY', 'yes')
            env('UPDATE_ISO_MEDIA', 'yes')
        } else {
            env('UPDATE_SDIST_MIRROR', 'no')
            env('UPDATE_PKG_REPOSITORY', 'no')
            env('UPDATE_ISO_MEDIA', 'no')
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
