## Delphix and the Microsoft Azure Agent

### Introduction

As part of supporting the Microsoft Azure computing platform, the
Microsoft Azure Agent was modified such that it would work when
installed and running on Delphix. This document is intended fullfil the
following purposes:

  * List the features of the Agent that are supported when running on
    Delphix.

  * Detail the features of the Agent that are not supported when
    running on Delphix, and explain the reasoning behind the lack of
    support.

  * Provide insight into the modifications that we made to the Agent,
    to enable it to work when running on Delphix.

  * List the various tools and automation that has been created to
    help aid development, stability, and maintenance of the Agent.

  * Provide basic, high-level instructions and/or tips for making
    modifications to the project; information that would be useful to
    new developers looking to work on this codebase.

  * Explain how the Agent is versioned, packaged, installed, and
    executed on Delphix.

  * Add commentary regarding any implementation details that pertain to
    pushing our modifications upstream.

### Supported Features

The following are all features that are supported when running the
Microsoft Azure Agent on Delphix.

#### Setting the VM's Hostname

When a user creates a VM in Azure, they will be prompted to provide a
string value to use as the hostname for the VM. During the provisioning
process, which occurs the first time the VM boots, this value will be
communicated to the Agent which will then configure the system's
hostname to match this value.

#### Reporting SSH Host Key Fingerprint to the Platform

As the final step of the provisioning process for a newly created
VM running on Azure, the Agent will communicate the VM's SSH host key
fingerprint as it reports the system to be "ready" (or not).

#### Console redirection to the serial port

TODO: Add more information.

### Unsupported Features

The following are all features that are *not* supported when running the
Microsoft Azure Agent on Delphix.

#### Creation of Non-Root User Account

Creation of a non-root user account during the provisioning process is
not supported on Delphix.

How this works for the other platforms is this:

  1. The user provides a username in the Azure UI when creating the VM
  2. After the VM boots the first time, the Agent will attempt to
     "provision" the VM.
  3. As part of that provisioning process, the Agent will be provided
     this username as a string, and a new user account will be generated
     using this username (at the OS level, such that SSH using this
     username will work).

For Delphix, this doesn't make much sense, since we don't allow our
customers normal shell access to the Delphix Engine. Thus, we've simply
disabled this functionality from the Agent when it is running on
Delphix.

As a result, the username provided by the user when creating the VM is
practically useless, as we'll ignore it during the provisioning process.

#### Configuring SSH Authentication Types

There's logic for modifying the sshd configuration file that's been
disabled when running on Delphix. By default, this is used to configure
the following sshd options:

  * PasswordAuthentication
  * ChallengeResponseAuthentication
  * ClientAliveInterval

We've disabled this feature when on Delphix, since there's no good
reason for the Agent to make any of these changes. Since we control the
Delphix image that's uploaded to Azure, we can ensure sshd is properly
configured without needing to involve the Agent.

#### Deployment of SSH Public Keys and Key Pairs

We don't support this feature on Delphix, for the same reason that we
don't support the creation of non-root user accounts. See
[here](#creation-of-non-root-user-account) for more details.

#### Publishing the Hostname to the Platform DNS

This isn't supported when running on Delphix, since it's unnecessary due
to difference in the Delphix OS and the other supported platforms.

During provisioning of a VM in Azure, which occurs the first time the
system boots, the hostname will be provided using an ISO that's attached
to the system. This feature is then used to convey this hostname to the
DHCP server, such that on subsequent boots of the VM, the DHCP server
will properly convey the VM's hostname when the VM aquires it's IP
address. If this feature is not supported, when the DHCP address is
requested, the server will not include a hostname in its response.

The lack of a hostname in the DHCP response is not a problem for the
Delphix OS because we configure the system to ignore any DHCP provided
hostname, and rely solely on the contents of the `/etc/nodename` file.
Additionally, this file is populated with the correct hostname during
the provisioning of the VM.

#### Resource Disk Management

The Microsoft Azure platform supports the notion of a "resource disk"
that is a temporary disk attached to VMs, whose purpose is to be used to
store temporary data which one can afford to lose at any time, but can
have higher IOPs and lower latency when comparied to the persistent
disk(s) attached to the VM. The Delphix Engine isn't capable of
utilizing such a disk, so managing this additional device isn't
supported.

For more information about these disks, see
[here](https://blogs.msdn.microsoft.com/mast/2013/12/06/understanding-the-temporary-drive-on-windows-azure-virtual-machines/).

#### Formatting and Mounting the Resource Disk

See [here](#resource-disk-management) for more information.

#### Configuring Swap Space

This feature is disabled since swap space will automatically be
configured on a Delphix system, without needing help from the Agent.

#### Manages routes to improve compatibility with platform DHCP servers

TODO: Add more information.

#### Ensures the stability of the network interface name

TODO: Add more information.

#### Configure virtual NUMA

TODO: Add more information.

#### Consume Hyper-V entropy for /dev/random

TODO: Add more information.

#### Configure SCSI timeouts for the root device (which could be remote)

TODO: Add more information.


#### VM Extension

TODO: Add more information.

