#
# Copyright 2014 Microsoft Corporation
# Copyright (c) 2016, 2017 by Delphix. All rights reserved.
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

import azurelinuxagent.common.logger as logger
from azurelinuxagent.common.utils.textutil import Version
from azurelinuxagent.common.version import DISTRO_NAME, DISTRO_VERSION, \
                                     DISTRO_FULL_NAME
from .default import ProvisionHandler
from .ubuntu import UbuntuProvisionHandler
from .delphix import DelphixOSProvisionHandler

def get_provision_handler(distro_name=DISTRO_NAME, 
                            distro_version=DISTRO_VERSION,
                            distro_full_name=DISTRO_FULL_NAME):
    if distro_name == "ubuntu":
        return UbuntuProvisionHandler()
    if distro_name == "delphix":
        return DelphixOSProvisionHandler()

    return ProvisionHandler()

