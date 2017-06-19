/*
 * Copyright (c) 2016 by Delphix. All rights reserved.
 */

pipelineJob('post-push') {
    if (System.getenv('JENKINS_DEVELOPER') == null) {
        triggers {
            scm('H/5 * * * *')
        }
    }

    environmentVariables {
        env('GIT_URL', 'https://gitlab.delphix.com/os-platform/WALinuxAgent.git')
        env('GIT_BRANCH', 'master')

        if (System.getenv('JENKINS_DEVELOPER') == null) {
            env('EMAIL', '')
        } else {
            env('EMAIL', '')
        }

        env('PKG_BUILD_URL', 'https://gitlab.delphix.com/os-platform/pkg-build-gate.git')
        env('PKG_BUILD_BRANCH', 'master')
    }

    definition {
        cps {
            script(readFileFromWorkspace('jenkins/jobs/pipelines/post_push.groovy'))
            sandbox()
        }
    }
}

// vim: tabstop=4 softtabstop=4 shiftwidth=4 expandtab textwidth=112 colorcolumn=120
