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

import os
import azurelinuxagent.common.logger as logger
import azurelinuxagent.common.conf as conf
from azurelinuxagent.pa.provision.default import ProvisionHandler
from azurelinuxagent.common.exception import ProvisionError, ProtocolError
import azurelinuxagent.common.utils.fileutil as fileutil

class DelphixProvisionHandler(ProvisionHandler):
    def __init__(self):
        super(DelphixProvisionHandler, self).__init__()

    def config_user_account(self, ovfenv):
        logger.info('"config_user_account" not supported.')

    def get_protocol_by_file(self):
        logger.info('"get_protocol_by_file" not supported.')

    def report_not_ready(self, sub_status, description):
        logger.info('"report_not_ready" not supported.')

    def report_ready(self, thumbprint=None):
        logger.info('"report_ready" not supported.')
