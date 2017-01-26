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
Microsoft Azure Agent on Delphix:

  * Setting the VM's Hostname
  * Reporting SSH Host Key Fingerprint to the Platform
  * Console redirection to the serial port
  * Manages routes to improve compatibility with platform DHCP servers
  * Configure SCSI timeouts for the root device (which could be remote)

#### Setting the VM's Hostname

When a user creates a VM in Azure, they will be prompted to provide a
string value to use as the hostname for the VM. During the provisioning
process, which occurs the first time the VM boots, this value will be
communicated to the Agent which will then configure the system's
hostname to match this value.

#### Console redirection to the serial port

During the VM provisioning process, the bootloader configuration is
modified using the command:

    /opt/delphix/server/bin/enable_serial_console

which will configure the kernel to use the serial console. After this
command is run, though, a reboot is required for the new configuration
to take effect.

### Unsupported Features

The following are all features that are *not* supported when running the
Microsoft Azure Agent on Delphix:

  * Creation of Non-Root User Account
  * Configuring SSH Authentication Types
  * Deployment of SSH Public Keys and Key Pairs
  * Publishing the Hostname to the Platform DNS
  * Resource Disk Management
  * Formatting and Mounting the Resource Disk
  * Configuring Swap Space

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

### Modifications Required to Support Delphix

The modifications to the Azure Agent that were needed to support running
on Delphix generally fall into the following categories:

  * Implement the platform specific "OSUtil" class for Delphix
  * Implement unit tests for the new Delphix specific "OSUtil" class
  * Modify "version.py" to detect the Delphix platform
  * Modify "setup.py" to properly build on the Delphix platform
  * Add SMF manifest for executing the Agent as an SMF service.
  * Add Jenkins automation for:
    - running the unit tests on a DCenter based Delphix VM
    - building an IPS package and updating the internal IPS repository

#### Platform Specific "OSUtil" Class for Delphix

The implementation of the Agent is architected in such a way that makes
it easy for it to support multiple different operating systems. Most, if
not all, of the platform specific parts of the Agent is encapsulated
into "OSUtil" classes for each platform. As an example, there's a
`DefaultOSUtil` class that targets functionality that is common to most
Linux distributions, but then there's classes like `RedhatOSUtil`,
`SUSEOSUtil`, and `UbuntuOSUtil` that implement the necessary parts that
are unique to each distribution. Additionally, there's a `FreeBSDOSUtil`
that provides implementation that's specific to FreeBSD.

These "OSUtil" classes all inherit from the base `DefaultOSUtil` class,
and class is used in a polymorphic way; consumers simply call
`get_osutil` and the returned object will be of the correct type (e.g.
a `FreeBSDOSUtil` will be returned if the Agent is running on FreeBSD).
This allows each platform to implement the interface of the "OSUtil"
class in any way that makes sense for that specific platform, and the
consumers of the interface don't have to have any knowledge of the
specific "OSUtil" class being used. Consumers just consume the interface
provided by the platform agnostic `DefaultOSUtil` class.

Thus, to support the Delphix platform, the new `DelphixOSUtil` class was
needed to implement the `DefaultOSUtil` interface. Since the default
implementaiton targetting the GNU/Linux platform(s), without a Delphix
specific class, the Agent would attempt to perform logic and run
commands that wasn't compatible with Delphix.

For example, when setting SCSI timeouts, the default implementation for
the `set_scsi_disks_timeout` function would attempt to write to files
under the `/sys/block` directory. On Delphix, SCSI timeouts are
configured by adding the appropriate configuration to the `/etc/system`
file. Differences like that had to be reconciled by re-implementing
parts of the `DefaultOSUtil` interface using the new `DelphixOSUtil`
class.

The implementation for the `DefaultOSUtil` class can be found
[here](azurelinuxagent/common/osutil/default.py), and the
implementation for the `DelphixOSUtil` class can be found
[here](azurelinuxagent/common/osutil/delphix.py).

#### Unit Tests for the Delphix Specific "OSUtil" Class

In addition to the implementation `DelphixOSUtil` class, there's also
logic with the specific intention of unit testing that class. These test
cases assume that they're running on a Delphix system, and can be found
[here](tests/common/osutil/test_delphix.py). Additionally, they attempt
to follow the convention put in place by the test cases targetting the
`DefaultOSUtil` class found [here](tests/common/osutil/test_default.py).

#### Detecting the Delphix Platform

In order for the Agent to use the correct "OSUtil" class for the
platform that it is running on, it has to detect the platform it's
running on to make that decision. The logic for performing this decision
occurs in the `get_distro` function of the "version.py" file; that file
can be found [here](azurelinuxagent/common/version.py). To support
running on Delphix, the logic in that function had to be extended to
detect when it's running on Delphix.
