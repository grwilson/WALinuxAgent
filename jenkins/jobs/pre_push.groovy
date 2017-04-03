/*
 * Copyright (c) 2016, 2017 by Delphix. All rights reserved.
 */

pipelineJob('pre-push') {
    concurrentBuild(true)
    quietPeriod(0)

    parameters {
        stringParam('GIT_URL', 'https://gitlab.delphix.com/os-platform/WALinuxAgent.git',
                    'The Git repository to use for the build.')
        stringParam('GIT_BRANCH', 'projects/hyperv', 'The Git branch to use for the build.')
        stringParam('EMAIL', '', 'The email to use for build status notifications.')
    }

    environmentVariables {
        env('DEVOPS_REPO', 'https://gitlab.delphix.com/devops/devops-gate.git')
        env('DEVOPS_BRANCH', 'master')

        env('DCENTER_IMAGE', 'dlpx-trunk')
        env('DCENTER_HOST', 'dcenter')

        env('DCENTER_ROLES', 'dlpx.dxos-credentials')
        env('SLAVE_ROLES', 'dlpx.initialize-dxos')
    }

    definition {
        cps {
            script(readFileFromWorkspace('jenkins/jobs/pipelines/pre_push.groovy'))
            sandbox()
        }
    }
}

// vim: tabstop=4 softtabstop=4 shiftwidth=4 expandtab textwidth=112 colorcolumn=120
