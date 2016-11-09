# Microsoft Azure Linux Agent
#
# Copyright 2014 Microsoft Corporation
# Copyright (c) 2016 by Delphix. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Requires Python 2.4+ and Openssl 1.0+
#

import azurelinuxagent.common.utils.fileutil as fileutil
import azurelinuxagent.common.utils.shellutil as shellutil
import azurelinuxagent.common.logger as logger
from azurelinuxagent.common.osutil.default import DefaultOSUtil

class DelphixOSUtil(DefaultOSUtil):
    def __init__(self):
        super(DelphixOSUtil, self).__init__()

    #
    # The methods that emit an "error" are not expected to be called
    # when the agent is running on Delphix. The code paths that could
    # have called them have been disabled either by configuration file
    # settings, or code changes to other parts of the codebase.
    #

    def useradd(self, username, expiration=None):
        logger.error('"useradd" not supported.')

    def chpasswd(self, username, password, crypt_id=6, salt_len=10):
        logger.error('"chpasswd" not supported.')

    def conf_sudoer(self, username, nopasswd=False, remove=False):
        logger.error('"conf_sudoer" not supported.')

    def conf_sshd(self, disable_password):
        logger.error('"conf_sshd" not supported.')

    def del_root_password(self):
        logger.error('"del_root_password" not supported.')

    def restart_if(self, ifname):
        logger.error('"restart_if" not supported.')

    def set_hostname(self, hostname):
        shellutil.run("hostname {0}".format(hostname), chk_err=False)
        fileutil.write_file('/etc/nodename', hostname)
        fileutil.update_conf_file("/etc/hosts",
                                  "::1",
                                  "::1 {0} localhost".format(hostname))
        fileutil.update_conf_file("/etc/hosts",
                                  "127.0.0.1",
                                  "127.0.0.1 {0} localhost loghost".format(hostname))

    def publish_hostname(self, hostname):
        logger.warn('"publish_hostname" not supported.')

    def restart_ssh_service(self):
        ret = shellutil.run('svcadm disable -s svc:/network/ssh')
        if ret == 0:
            return shellutil.run('svcadm enable -s svc:/network/ssh')
        else:
            return ret

    def is_sys_user(self, username):
        logger.warn('"is_sys_user" not supported.')

    def del_account(self, username):
        logger.warn('"del_account" not supported.')

    def deploy_ssh_pubkey(self, username, pubkey):
        logger.warn('"deploy_ssh_pubkey" not supported.')

    def is_selinux_system(self):
        return False

    #
    # We rely on the OS mounting and unmounting the dvd for us; thus,
    # all of these methods are not supported.
    #

    def get_dvd_device(self, dev_dir='/dev'):
        logger.warn('"get_dvd_device" not supported.')

    def mount_dvd(self, max_retry=6, chk_err=True, dvd_device=None, mount_point=None):
        logger.warn('"mount_dvd" not supported.')

    def umount_dvd(self, chk_err=True, mount_point=None):
        logger.warn('"unmount_dvd" not supported.')

    def eject_dvd(self, chk_err=True):
        logger.warn('"eject_dvd" not supported.')

    def get_if_mac(self, ifname):
        data = self._get_net_info()
        if data[0] == ifname:
            return data[2].replace(':', '').upper()
        return None

    def get_first_if(self):
        return self._get_net_info()[:2]

    def route_add(self, net, mask, gateway):
        cmd = 'route add {0} {1} {2}'.format(net, gateway, mask)
        return shellutil.run(cmd, chk_err=False)

    def is_missing_default_route(self):
        return False

    def is_dhcp_enabled(self):
        return False

    def allow_dhcp_broadcast(self):
        pass

    def get_dhcp_pid(self):
        ret = shellutil.run_get_output("pgrep -c $(svcs -H -o ctid svc:/network/dhcp-client)", chk_err=False)
        return ret[1] if ret[0] == 0 else None

    def set_scsi_disks_timeout(self, timeout):
        logger.warn('"set_scsi_disks_timeout" not supported.')

    def check_pid_alive(self, pid):
        return shellutil.run("ps -p {0}".format(pid), chk_err=False) == 0

    @staticmethod
    def _get_net_info():
        iface = ''
        inet = ''
        mac = ''

        err, output = shellutil.run_get_output('dladm show-ether -p -o LINK', chk_err=False)
        if err:
            raise OSUtilError("Can't find ether interface:{0}".format(output))
        ifaces = output.split()
        if not ifaces:
            raise OSUtilError("Can't find ether interface.")
        iface = ifaces[0]

        err, output = shellutil.run_get_output('dladm show-phys -m -p -o address ' + iface, chk_err=False)
        if err:
            raise OSUtilError("Can't get mac address for interface:{0}".format(iface))
        macs = output.split()
        if not macs:
            raise OSUtilError("Can't find mac address.")
        mac = macs[0]

        #
        # It's possible for the output from "dladm show-phys" to output
        # a mac address, such that each octet is not two characters
        # (e.g. "2:dc:0:0:23:ff"). Certain parts of the agent expect
        # each octet of the mac address to be two hex characters long,
        # so we're forcing the address returned by this function to
        # always have two character long octets.
        #
        mac = ":".join(map(lambda x: "{0:02x}".format(int(x, 16)), mac.split(":")))

        err, output = shellutil.run_get_output('ipadm show-addr -p -o addr ' + iface + '/', chk_err=False)
        if err:
            raise OSUtilError("Can't get ip address for interface:{0}".format(iface))
        ips = output.split()
        if not ips:
            raise OSUtilError("Can't find ip address.")
        ip = ips[0].split('/')[0]

        logger.verbose("Interface info: ({0},{1},{2})", iface, ip, mac)

        return iface, ip, mac

    def device_for_ide_port(self, port_id):
        logger.warn('"device_for_ide_port" not supported.')
