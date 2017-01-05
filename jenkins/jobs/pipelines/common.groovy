/*
 * Copyright (c) 2016, 2017 by Delphix. All rights reserved.
 */

import org.apache.commons.lang.RandomStringUtils

def getDCenterGuestName() {
    return 'walinuxagent-jenkins-' + RandomStringUtils.randomAlphanumeric(8)
}

def createDCenterGuest(String guest, String host, String image) {
    node(host) {
        sh("dc clone-latest ${image} ${guest}")

        /*
         * We need to wait for the VM that we create is ready to process incoming SSH connections, or else we
         * can wind up in situation where we attempt to connect to the VM and attach it as a Jenkins slave,
         * prior to the VM being ready; this would result in the job failing.
         *
         * By relying on "dc guest wait" and "dc guest run", we're encoding the assumption that the VM we just
         * created has VMware's guest tools installed into the guest's operating system. Without these tools,
         * we cannot use "dc guest". Additionally, by relying on the "svcadm" command, we encode the assumption
         * that the VM we created is actually a "dlpx-trunk" VM. It's a shame to encode these implicit
         * dependencies here, but there isn't any clearly better solution that would work across all guest
         * operating systems; and since we're currently only using "dlpx-trunk" VMs, this should be OK.
         */
        sh("dc guest wait ${guest}")
        sh("dc guest run ${guest} 'svcadm enable -s svc:/network/ssh:default'")
    }
}

def destroyDCenterGuest(String guest, String host) {
    node(host) {
        sh("dc destroy ${guest}")
    }
}

def unregisterDCenterGuest(String guest, String host) {
    node(host) {
        sh("dc unregister --ignore-missing --expires 7 ${guest}")
    }
}

def sendResults(String recipient) {
    /*
     * We can only use "emailext" if we're in a "node" context, so we allocate a node to satisfy that
     * constraint, but we don't have to worry about which node we're using.
     */
    node {
        emailext(to: recipient, body: "Please visit ${env.BUILD_URL} to inspect results.",
                 subject: "Job ${env.JOB_NAME} #${env.BUILD_NUMBER} completed with status: ${currentBuild.result}")
    }
}

return this;

// vim: tabstop=4 softtabstop=4 shiftwidth=4 expandtab textwidth=112 colorcolumn=120
