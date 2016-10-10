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

import socket
import time
import os
import random
import azurelinuxagent.common.osutil.delphix as osutil
import azurelinuxagent.common.utils.shellutil as shellutil
from tests.tools import *


class TestOSUtil(AgentTestCase):
    @unittest.skipUnless(os.getuid() == 0, 'test must be run as root')
    def test_set_hostname(self):
        ret = shellutil.run_get_output("hostname")
        if ret[0] == 0:
            old_hostname = ret[1]
        else:
            self.fail("Cannot retrive old hostname.")

        set_hostname = "delphix-{0}".format(random.randint(0, 100))
        osutil.DelphixOSUtil().set_hostname(set_hostname)

        ret = shellutil.run_get_output("hostname")
        if ret[0] == 0:
            new_hostname = ret[1].strip()
        else:
            self.fail("Cannot retrive new hostname.")

        self.assertNotEqual(old_hostname, set_hostname)
        self.assertNotEqual(old_hostname, new_hostname)
        self.assertEqual(set_hostname, new_hostname)

    @unittest.skipUnless(os.getuid() == 0, 'test must be run as root')
    def test_restart_ssh_service(self):
        cmd = "pgrep -c $(svcs -H -o ctid svc:/network/ssh)"

        ret = shellutil.run_get_output(cmd)
        if ret[0] == 0:
            pid_before = int(ret[1])
        else:
            self.fail("Cannot retrive ssh pid before restart.")

        osutil.DelphixOSUtil().restart_ssh_service()

        ret = shellutil.run_get_output(cmd)
        if ret[0] == 0:
            pid_after = int(ret[1])
        elif retry <= 0:
            self.fail("Cannot retrive ssh pid after restart.")

        self.assertNotEqual(pid_before, pid_after)

    def test_get_first_if(self):
        ifname, ipaddr = osutil.DelphixOSUtil().get_first_if()
        self.assertTrue(ifname.startswith('vmxnet3'))
        self.assertTrue(ipaddr is not None)
        try:
            socket.inet_aton(ipaddr)
        except socket.error:
            self.fail("not a valid ip address")

    def test_get_processor_cores(self):
        """
        Validate the returned value matches to the one retrieved by invoking shell command
        """
        cmd = "psrinfo | wc -l"
        ret = shellutil.run_get_output(cmd)
        if ret[0] == 0:
            self.assertEqual(int(ret[1]), osutil.DelphixOSUtil().get_processor_cores())
        else:
            self.fail("Cannot retrieve number of process cores using shell command.")

    def test_get_total_mem(self):
        """
        Validate the returned value matches to the one retrieved by invoking shell command
        """
        cmd = "prtconf | grep 'Memory size' | awk '{print $3}'"
        ret = shellutil.run_get_output(cmd)
        if ret[0] == 0:
            expected = int(ret[1])
            actual = osutil.DelphixOSUtil().get_total_mem()

            #
            # Due to how the expected value and actual value are
            # determined, they may differ by 1 MB due to rounding
            # issues. E.g. if the amount of total memory isn't evenly
            # divisible by a Megabyte. Thus, to account for this, we
            # only verify the actual value is within a range of the
            # expected value, rather than being exactly equal to it.
            #
            self.assertTrue((expected - 1) <= actual <= (expected + 1))
        else:
            self.fail("Cannot retrieve total memory using shell command.")

if __name__ == '__main__':
    unittest.main()
