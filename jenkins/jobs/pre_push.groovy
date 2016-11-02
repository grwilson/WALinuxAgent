/*
 * Copyright (c) 2016 by Delphix. All rights reserved.
 */

pipelineJob('pre-push') {
    concurrentBuild(true)
    quietPeriod(0)

    parameters {
        stringParam('GIT_URL', 'ssh://git@git/var/WALinuxAgent', 'The Git repository to use for the build.')
        stringParam('GIT_BRANCH', 'projects/hyperv', 'The Git branch to use for the build.')
        stringParam('EMAIL', '', 'The email to use for build status notifications.')
    }

    environmentVariables {
        env('DCENTER_IMAGE', 'dlpx-trunk')
        env('DCENTER_HOST', 'dcenter')
    }

    definition {
        cps {
            script(readFileFromWorkspace('jenkins/jobs/pipelines/pre_push.groovy'))
            sandbox()
        }
    }
}

// vim: tabstop=4 softtabstop=4 shiftwidth=4 expandtab textwidth=112 colorcolumn=120
