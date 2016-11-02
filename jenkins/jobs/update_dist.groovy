/*
 * Copyright (c) 2016 by Delphix. All rights reserved.
 */

pipelineJob('update-dist') {
    /*
     * We can't allow concurrent builds of this job since it manipulates infrastructure that is not unique to
     * each build. Specifically, a "dist" is generated of the WALinuxAgent repository, and this is then uploaded
     * to our internal IPS package mirror, which is a globally shared resource.
     */
    concurrentBuild(false)

    parameters {
        stringParam('GIT_URL', 'ssh://git@git/var/WALinuxAgent', 'The Git repository to use for the build.')
        stringParam('GIT_BRANCH', 'projects/hyperv', 'The Git branch to use for the build.')
        stringParam('EMAIL', '', 'The email to use for build status notifications.')
    }

    if (System.getenv('JENKINS_DEVELOPER') == null) {
        triggers {
            scm('H/5 * * * *')
        }
    }

    environmentVariables {
        env('DCENTER_IMAGE', 'dlpx-trunk')
        env('DCENTER_HOST', 'dcenter')

        env('DIST_MIRROR_DIRECTORY', '/net/pharos/export/third-party/mirror')

        if (System.getenv('JENKINS_DEVELOPER') == null) {
            env('UPDATE_DIST_MIRROR', 'yes')
        } else {
            env('UPDATE_DIST_MIRROR', 'no')
        }
    }

    definition {
        cps {
            script(readFileFromWorkspace('jenkins/jobs/pipelines/update_dist.groovy'))
            sandbox()
        }
    }
}

// vim: tabstop=4 softtabstop=4 shiftwidth=4 expandtab textwidth=112 colorcolumn=120
