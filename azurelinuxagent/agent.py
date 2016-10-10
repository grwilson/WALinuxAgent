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

"""
Module agent
"""

import os
import sys
import re
import subprocess
import azurelinuxagent.common.logger as logger
import azurelinuxagent.common.event as event
import azurelinuxagent.common.conf as conf
from azurelinuxagent.common.version import AGENT_NAME, AGENT_LONG_VERSION, \
                                     DISTRO_NAME, DISTRO_VERSION, \
                                     PY_VERSION_MAJOR, PY_VERSION_MINOR, \
                                     PY_VERSION_MICRO, GOAL_STATE_AGENT_VERSION
from azurelinuxagent.common.osutil import get_osutil

class Agent(object):
    def __init__(self, verbose):
        """
        Initialize agent running environment.
        """
        self.osutil = get_osutil()
        #Init stdout log
        level = logger.LogLevel.VERBOSE if verbose else logger.LogLevel.INFO
        logger.add_logger_appender(logger.AppenderType.STDOUT, level)

        #Init config
        conf_file_path = self.osutil.get_agent_conf_file_path()
        conf.load_conf_from_file(conf_file_path)

        #Init log
        verbose = verbose or conf.get_logs_verbose()
        level = logger.LogLevel.VERBOSE if verbose else logger.LogLevel.INFO
        logger.add_logger_appender(logger.AppenderType.FILE, level,
                                 path="/var/log/waagent.log")
#
# TODO: Disabling this due to hangs seen when writing to /dev/console
# with the following stack:
# > ::pgrep python | ::walk thread | ::findstack -v
# stack pointer for thread ffffff0368f503a0: ffffff000cf358c0
# [ ffffff000cf358c0 _resume_from_idle+0xf4() ]
#   ffffff000cf358f0 swtch+0x141()
#   ffffff000cf35960 cv_wait_sig+0x185(ffffff031615718a, ffffff031615f668)
#   ffffff000cf35990 str_cv_wait+0x27(ffffff031615718a, ffffff031615f668, ffffffffffffffff, 0)
#   ffffff000cf35a40 strwaitq+0x2c3(ffffff031615f5e8, 1, 0, 2302, ffffffffffffffff, ffffff000cf35a8c)
#   ffffff000cf35ae0 strwrite_common+0x19c(ffffff0316152700, ffffff000cf35e60, ffffff0365b1ade0, 0)
#   ffffff000cf35b10 strwrite+0x1b(ffffff0316152700, ffffff000cf35e60, ffffff0365b1ade0)
#   ffffff000cf35ca0 cnwrite+0x96(2700000000, ffffff000cf35e60, ffffff0365b1ade0)
#   ffffff000cf35cd0 cdev_write+0x2d(2700000000, ffffff000cf35e60, ffffff0365b1ade0)
#   ffffff000cf35db0 spec_write+0x4c1(ffffff03166efa00, ffffff000cf35e60, 0, ffffff0365b1ade0, 0)
#   ffffff000cf35e30 fop_write+0x5b(ffffff03166efa00, ffffff000cf35e60, 0, ffffff0365b1ade0, 0)
#   ffffff000cf35f00 write+0x250(3, a82e24, 40)
#   ffffff000cf35f10 sys_syscall+0x17a()
#
#        logger.add_logger_appender(logger.AppenderType.CONSOLE, level,
#                                 path="/dev/console")

        #Init event reporter
        event_dir = os.path.join(conf.get_lib_dir(), "events")
        event.init_event_logger(event_dir)
        event.enable_unhandled_err_dump("WALA")

    def daemon(self):
        """
        Run agent daemon
        """
        from azurelinuxagent.daemon import get_daemon_handler
        daemon_handler = get_daemon_handler()
        daemon_handler.run()

    def provision(self):
        """
        Run provision command
        """
        from azurelinuxagent.pa.provision import get_provision_handler
        provision_handler = get_provision_handler()
        provision_handler.run()

    def deprovision(self, force=False, deluser=False):
        """
        Run deprovision command
        """
        from azurelinuxagent.pa.deprovision import get_deprovision_handler
        deprovision_handler = get_deprovision_handler()
        deprovision_handler.run(force=force, deluser=deluser)

    def register_service(self):
        """
        Register agent as a service
        """
        print("Register {0} service".format(AGENT_NAME))
        self.osutil.register_agent_service()
        print("Stop {0} service".format(AGENT_NAME))
        self.osutil.stop_agent_service()
        print("Start {0} service".format(AGENT_NAME))
        self.osutil.start_agent_service()

    def run_exthandlers(self):
        """
        Run the update and extension handler
        """
        from azurelinuxagent.ga.update import get_update_handler
        update_handler = get_update_handler()
        update_handler.run()

def main(args=[]):
    """
    Parse command line arguments, exit with usage() on error.
    Invoke different methods according to different command
    """
    if len(args) <= 0:
        args = sys.argv[1:]
    command, force, verbose = parse_args(args)
    if command == "version":
        version()
    elif command == "help":
        usage()
    elif command == "start":
        start()
    else:
        try:
            agent = Agent(verbose)
            if command == "deprovision+user":
                agent.deprovision(force, deluser=True)
            elif command == "deprovision":
                agent.deprovision(force, deluser=False)
            elif command == "provision":
                agent.provision()
            elif command == "register-service":
                agent.register_service()
            elif command == "daemon":
                agent.daemon()
            elif command == "run-exthandlers":
                agent.run_exthandlers()
        except Exception as e:
            logger.error(u"Failed to run '{0}': {1}", command, e)

def parse_args(sys_args):
    """
    Parse command line arguments
    """
    cmd = "help"
    force = False
    verbose = False
    for a in sys_args:
        if re.match("^([-/]*)deprovision\\+user", a):
            cmd = "deprovision+user"
        elif re.match("^([-/]*)deprovision", a):
            cmd = "deprovision"
        elif re.match("^([-/]*)daemon", a):
            cmd = "daemon"
        elif re.match("^([-/]*)start", a):
            cmd = "start"
        elif re.match("^([-/]*)register-service", a):
            cmd = "register-service"
        elif re.match("^([-/]*)run-exthandlers", a):
            cmd = "run-exthandlers"
        elif re.match("^([-/]*)version", a):
            cmd = "version"
        elif re.match("^([-/]*)verbose", a):
            verbose = True
        elif re.match("^([-/]*)force", a):
            force = True
        elif re.match("^([-/]*)(help|usage|\\?)", a):
            cmd = "help"
        else:
            cmd = "help"
            break
    return cmd, force, verbose

def version():
    """
    Show agent version
    """
    print(("{0} running on {1} {2}".format(AGENT_LONG_VERSION,
                                           DISTRO_NAME,
                                           DISTRO_VERSION)))
    print("Python: {0}.{1}.{2}".format(PY_VERSION_MAJOR,
                                       PY_VERSION_MINOR,
                                       PY_VERSION_MICRO))
    print("Goal state agent: {0}".format(GOAL_STATE_AGENT_VERSION))

def usage():
    """
    Show agent usage
    """
    print("")
    print((("usage: {0} [-verbose] [-force] [-help] "
           "-deprovision[+user]|-register-service|-version|-daemon|-start|"
           "-run-exthandlers]"
           "").format(sys.argv[0])))
    print("")

def start():
    """
    Start agent daemon in a background process and set stdout/stderr to
    /dev/null
    """
    devnull = open(os.devnull, 'w')
    subprocess.Popen([sys.argv[0], '-daemon'], stdout=devnull, stderr=devnull)

if __name__ == '__main__' :
    main()
